package com.scutmmq.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.scutmmq.ai.service.AgentOrchestrator;
import com.scutmmq.ai.service.OrchestratorListener;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.dto.UserDTO;
import com.scutmmq.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * B2:Stage 1 评估运行器 + /dev/ai/eval/run HTTP 端点配套。
 *
 * 用法(dev 模式启用 ai.capability.eval 后):
 * 1. 在 src/main/resources/eval 下放 *.yaml 用例
 * 2. 通过 POST /dev/ai/eval/run 触发,或 POST /dev/ai/eval/run/{caseName} 跑单个
 * 3. GET /dev/ai/eval/cases 列出 yaml
 *
 * 设计原则:
 * - 默认 ai.capability.eval.enabled=false,不创建 Bean,不暴露 HTTP 端点
 * - 一次 EvalCase 调一次 AgentOrchestrator.runStreaming,不并发(便于统计 token)
 * - 失败也写入 EvalReport,便于排查
 *
 * 2026-08-23 凌晨 C0-C12 事故复盘后扩展的断言(防止再次踩坑):
 * - expectReplyNoDsml:C7 — DSML 不应在 reply 出现
 * - expectToolArgsContains:C11 — 工具 args 必须包含指定字段(防 phantom 跨污染)
 * - expectDraft + expectDraftArgsContains:C0 — 防草稿幻觉
 * - expectMaxToolExecutions:C8 — 防 maxIter 死循环
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.capability.eval.enabled", havingValue = "true")
@RequiredArgsConstructor
public class EvalRunner {

    /** C7 DSML 标签特征:尖括号 + 全角竖线 + DSML */
    private static final String DSML_PATTERN = "<｜｜DSML｜｜";

    private final AgentOrchestrator agentOrchestrator;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * 跑单个用例的内部逻辑,纯断言,无 IO。
     * 供 main 和单元测试直接调用。
     *
     * @param evalCase 用例数据
     * @return 判定结果
     */
    public EvalVerdict runOne(EvalCase evalCase) {
        long t0 = System.currentTimeMillis();
        List<AgentOrchestrator.ToolExecutionRecord> executions = new ArrayList<>();
        UserDTO user = new UserDTO();
        user.setId(evalCase.getUserId() == null ? 9999L : evalCase.getUserId());
        user.setUsername("eval-runner");
        user.setRole("USER");
        UserHolder.saveUser(user);
        try {
            AgentOrchestrator.AgentResult result = agentOrchestrator.runStreaming(
                    user,
                    java.util.Collections.emptyList(),
                    evalCase.getMessage(),
                    new OrchestratorListener() {
                        @Override public void onAssistantDelta(String delta, int offset) {}
                        @Override public void onToolStarted(String id, String name, JsonNode args) {}
                        @Override public void onToolFinished(String id, String name, String content, boolean hasDraft) {}
                        @Override public void onDraftCreated(AgentToolResult.DraftPayload draft) {}
                        @Override public void onRunCompleted(String reply, AgentToolResult.DraftPayload draft) {}
                        @Override public void onRunFailed(Throwable err) {}
                    });

            List<String> toolsCalled = result.toolExecutions().stream()
                    .map(AgentOrchestrator.ToolExecutionRecord::name)
                    .filter(n -> n != null && !n.isBlank())
                    .distinct()
                    .collect(Collectors.toList());

            List<EvalVerdict.CheckResult> checks = new ArrayList<>();
            boolean passed = true;
            String failReason = null;

            // ============ 基础断言(B2 已有) ============

            // 工具检查 1:expectTool
            if (evalCase.getExpectTool() != null && !evalCase.getExpectTool().isBlank()) {
                boolean toolOk = toolsCalled.contains(evalCase.getExpectTool());
                checks.add(EvalVerdict.CheckResult.builder()
                        .name("expectTool=" + evalCase.getExpectTool())
                        .passed(toolOk)
                        .detail("actual tools: " + toolsCalled)
                        .build());
                if (!toolOk) {
                    passed = false;
                    failReason = "missing tool: " + evalCase.getExpectTool();
                }
            }

            // 工具检查 2:expectNoTools
            if (Boolean.TRUE.equals(evalCase.getExpectNoTools())) {
                boolean noTools = toolsCalled.isEmpty();
                checks.add(EvalVerdict.CheckResult.builder()
                        .name("expectNoTools")
                        .passed(noTools)
                        .detail("actual tools: " + toolsCalled)
                        .build());
                if (!noTools) {
                    passed = false;
                    failReason = "unexpected tools called: " + toolsCalled;
                }
            }

            // 关键词检查:任一关键词命中即通过
            if (evalCase.getExpectKeywords() != null && !evalCase.getExpectKeywords().isEmpty()) {
                String reply = result.reply() == null ? "" : result.reply();
                String lower = reply.toLowerCase();
                String hit = evalCase.getExpectKeywords().stream()
                        .filter(k -> lower.contains(k.toLowerCase()))
                        .findFirst().orElse(null);
                boolean kwOk = hit != null;
                checks.add(EvalVerdict.CheckResult.builder()
                        .name("expectKeywords")
                        .passed(kwOk)
                        .detail("hit=" + hit + ", reply preview=" + preview(reply, 80))
                        .build());
                if (!kwOk) {
                    passed = false;
                    failReason = "no keyword hit in reply (expected one of: "
                            + evalCase.getExpectKeywords() + ")";
                }
            }

            // ============ C0-C12 回归断言 ============

            // C7:reply 不能含 DSML 标签
            if (Boolean.TRUE.equals(evalCase.getExpectReplyNoDsml())) {
                String reply = result.reply() == null ? "" : result.reply();
                boolean noDsml = !reply.contains(DSML_PATTERN);
                checks.add(EvalVerdict.CheckResult.builder()
                        .name("expectReplyNoDsml")
                        .passed(noDsml)
                        .detail("reply=" + preview(reply, 120))
                        .build());
                if (!noDsml) {
                    passed = false;
                    failReason = "reply contains DSML tag (C7 regression!)";
                }
            }

            // C11:工具 args 必须包含指定字段(防 phantom 跨污染)
            if (evalCase.getExpectToolArgsContains() != null
                    && !evalCase.getExpectToolArgsContains().isEmpty()) {
                for (Map.Entry<String, Map<String, Object>> toolEntry
                        : evalCase.getExpectToolArgsContains().entrySet()) {
                    String toolName = toolEntry.getKey();
                    Map<String, Object> requiredFields = toolEntry.getValue();
                    List<AgentOrchestrator.ToolExecutionRecord> calls =
                            result.toolExecutions().stream()
                                    .filter(r -> toolName.equals(r.name()))
                                    .collect(Collectors.toList());
                    boolean allOk = !calls.isEmpty();
                    String detail = "calls=" + calls.size();
                    if (allOk) {
                        for (AgentOrchestrator.ToolExecutionRecord c : calls) {
                            JsonNode args = c.arguments();
                            if (args == null || !args.isObject()) {
                                allOk = false;
                                detail += ", args=null/notObject";
                                break;
                            }
                            for (Map.Entry<String, Object> req : requiredFields.entrySet()) {
                                if (!args.has(req.getKey())) {
                                    allOk = false;
                                    detail += ", missing " + req.getKey();
                                    break;
                                }
                                // 校验值(如果期望指定)
                                Object expected = req.getValue();
                                if (expected != null) {
                                    JsonNode actual = args.get(req.getKey());
                                    if (actual == null || !String.valueOf(actual.asText()).equals(String.valueOf(expected))) {
                                        allOk = false;
                                        detail += ", " + req.getKey() + "=" + actual
                                                + " (expected " + expected + ")";
                                        break;
                                    }
                                }
                            }
                            if (!allOk) break;
                        }
                    }
                    checks.add(EvalVerdict.CheckResult.builder()
                            .name("expectToolArgsContains[" + toolName + "]")
                            .passed(allOk)
                            .detail(detail + ", required=" + requiredFields)
                            .build());
                    if (!allOk) {
                        passed = false;
                        failReason = "tool " + toolName + " args missing required fields: "
                                + requiredFields + " (C11 regression!)";
                    }
                }
            }

            // C0:必须产出 draft
            if (Boolean.TRUE.equals(evalCase.getExpectDraft())) {
                boolean hasDraft = result.draft() != null;
                checks.add(EvalVerdict.CheckResult.builder()
                        .name("expectDraft")
                        .passed(hasDraft)
                        .detail("draft=" + (result.draft() == null ? "null" : result.draft().getActionType()))
                        .build());
                if (!hasDraft) {
                    passed = false;
                    failReason = "no draft generated (C0 regression: hallucinated confirmation?)";
                } else if (evalCase.getExpectDraftArgsContains() != null
                        && !evalCase.getExpectDraftArgsContains().isEmpty()) {
                    // C0.1:draft payload 必须包含指定字段(防幻觉参数)
                    JsonNode draftPayload = result.draft().getPayload();
                    boolean allPresent = draftPayload != null && draftPayload.isObject();
                    String detail = "payload=" + (draftPayload == null ? "null" : draftPayload);
                    if (allPresent) {
                        for (Map.Entry<String, Object> req
                                : evalCase.getExpectDraftArgsContains().entrySet()) {
                            if (!draftPayload.has(req.getKey())) {
                                allPresent = false;
                                detail += ", missing " + req.getKey();
                                break;
                            }
                            Object expected = req.getValue();
                            if (expected != null) {
                                JsonNode actual = draftPayload.get(req.getKey());
                                if (actual == null
                                        || !String.valueOf(actual.asText()).equals(String.valueOf(expected))) {
                                    allPresent = false;
                                    detail += ", " + req.getKey() + "=" + actual
                                            + " (expected " + expected + ")";
                                    break;
                                }
                            }
                        }
                    }
                    checks.add(EvalVerdict.CheckResult.builder()
                            .name("expectDraftArgsContains")
                            .passed(allPresent)
                            .detail(detail)
                            .build());
                    if (!allPresent) {
                        passed = false;
                        failReason = "draft payload missing required fields: "
                                + evalCase.getExpectDraftArgsContains() + " (C0 regression!)";
                    }
                }
            }

            // C8:工具执行次数 ≤ 上限(防死循环)
            if (evalCase.getExpectMaxToolExecutions() != null) {
                int actualCount = result.toolExecutions().size();
                boolean ok = actualCount <= evalCase.getExpectMaxToolExecutions();
                checks.add(EvalVerdict.CheckResult.builder()
                        .name("expectMaxToolExecutions<=" + evalCase.getExpectMaxToolExecutions())
                        .passed(ok)
                        .detail("actual=" + actualCount + " tools: " + toolsCalled)
                        .build());
                if (!ok) {
                    passed = false;
                    failReason = "tool executions " + actualCount
                            + " > max " + evalCase.getExpectMaxToolExecutions()
                            + " (C8 regression: possible infinite loop)";
                }
            }

            return EvalVerdict.builder()
                    .caseName(evalCase.getName())
                    .passed(passed)
                    .reason(passed ? null : failReason)
                    .toolsCalled(toolsCalled)
                    .replyPreview(preview(result.reply(), 80))
                    .elapsedMs(System.currentTimeMillis() - t0)
                    .checks(checks)
                    .build();
        } catch (Exception e) {
            log.error("[AI][EVAL] case {} threw: {}", evalCase.getName(), e.getMessage(), e);
            return EvalVerdict.builder()
                    .caseName(evalCase.getName())
                    .passed(false)
                    .reason("exception: " + e.getMessage())
                    .toolsCalled(executions.stream()
                            .map(AgentOrchestrator.ToolExecutionRecord::name).collect(Collectors.toList()))
                    .replyPreview("")
                    .elapsedMs(System.currentTimeMillis() - t0)
                    .build();
        } finally {
            UserHolder.removeUser();
        }
    }

    /**
     * 跑一个目录下所有 .yaml 文件。
     */
    public EvalReport runDirectory(String dirPath) {
        EvalReport report = new EvalReport();
        report.setStartedAtMillis(System.currentTimeMillis());
        try {
            Path dir = Paths.get(dirPath);
            if (!Files.isDirectory(dir)) {
                log.warn("[AI][EVAL] not a directory: {}", dirPath);
                return report;
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.yaml")) {
                for (Path p : ds) {
                    EvalCase ec = readCase(p);
                    if (ec == null) continue;
                    EvalVerdict v = runOne(ec);
                    report.add(v);
                    log.info("[AI][EVAL] {} -> {} ({}ms)",
                            ec.getName(), v.isPassed() ? "PASS" : "FAIL", v.getElapsedMs());
                }
            }
        } catch (IOException e) {
            log.error("[AI][EVAL] directory walk failed: {}", e.getMessage(), e);
        }
        report.setEndedAtMillis(System.currentTimeMillis());
        log.info("[AI][EVAL] DONE total={} passed={} failed={} passRate={}",
                report.getTotal(), report.getPassed(), report.getFailed(), report.passRate());
        return report;
    }

    /**
     * 跑单个 yaml 文件,自动定位到 src/main/resources/eval 目录。
     */
    public EvalVerdict runByName(String caseName) {
        Path dir = Paths.get("src/main/resources/eval");
        if (!Files.isDirectory(dir)) {
            dir = Paths.get("src", "main", "resources", "eval");
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.yaml")) {
            for (Path p : ds) {
                EvalCase ec = readCase(p);
                if (ec == null) continue;
                if (caseName.equals(ec.getName()) || caseName.equals(p.getFileName().toString())) {
                    return runOne(ec);
                }
            }
        } catch (IOException e) {
            log.error("[AI][EVAL] lookup failed: {}", e.getMessage());
        }
        return EvalVerdict.builder()
                .caseName(caseName)
                .passed(false)
                .reason("case not found: " + caseName)
                .build();
    }

    /**
     * 列出 eval 目录下所有用例的元数据(name + message)。
     */
    public List<EvalCase> listCases(String dirPath) {
        List<EvalCase> out = new ArrayList<>();
        Path dir = Paths.get(dirPath);
        if (!Files.isDirectory(dir)) {
            dir = Paths.get("src", "main", "resources", "eval");
        }
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.yaml")) {
            Iterator<Path> it = ds.iterator();
            while (it.hasNext()) {
                EvalCase ec = readCase(it.next());
                if (ec != null) out.add(ec);
            }
        } catch (IOException e) {
            log.warn("[AI][EVAL] listCases failed: {}", e.getMessage());
        }
        return out;
    }

    public EvalCase readCase(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            EvalCase ec = yamlMapper.readValue(in, EvalCase.class);
            if (ec.getName() == null || ec.getName().isBlank()) {
                ec.setName(path.getFileName().toString());
            }
            return ec;
        } catch (Exception e) {
            log.warn("[AI][EVAL] failed to load case {}: {}", path, e.getMessage());
            return null;
        }
    }

    private static String preview(String s, int max) {
        if (s == null) return "";
        String t = s.replace("\n", " ").replace("\r", " ");
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    // 给单测直接传 case name 拼一个 runId(Session:local-eval)
    public static String newRunId() {
        return "eval-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
