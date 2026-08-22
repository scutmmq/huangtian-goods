package com.scutmmq.ai.eval;

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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * B2:Stage 1 评估运行器。
 *
 * 用法(dev 模式启用 ai.capability.eval 后):
 * 1. 在 src/test/resources/eval 下放 *.yaml 用例
 * 2. 通过 POST /dev/ai/eval/run 触发(后续 Stage 可加 admin endpoint)
 * 3. 或 main() 起来调 runner.runAll()
 *
 * 设计原则(策略文档 §B2 验收):
 * - 默认 ai.capability.eval.enabled=false,不创建 Bean
 * - 一次 EvalCase 调一次 AgentOrchestrator.runStreaming,不并发(便于统计 token)
 * - 失败也写入 EvalReport,便于排查
 *
 * 简化版断言:不抽 AssertStrategy 接口,直接在 EvalRunner 写硬编码规则。
 * 下一轮如果断言规则膨胀(>10 条)再升级。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.capability.eval.enabled", havingValue = "true")
@RequiredArgsConstructor
public class EvalRunner {

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
                        @Override public void onToolStarted(String id, String name, com.fasterxml.jackson.databind.JsonNode args) {}
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
                    .toolsCalled(executions.stream().map(AgentOrchestrator.ToolExecutionRecord::name).collect(Collectors.toList()))
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
