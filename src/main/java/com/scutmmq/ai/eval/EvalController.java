package com.scutmmq.ai.eval;

import com.scutmmq.entity.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C13:/dev/ai/eval/run HTTP 端点。
 *
 * <p>2026-08-23 凌晨 C0-C12 13 个 hotfix 之后,需要自动化回归覆盖,
 * 否则改 orchestrator 一改就挂、用户凌晨反复刷 issue。
 *
 * <p>端点(只 dev 模式启用,生产环境 ai.capability.eval.enabled=false 时不暴露):
 * <ul>
 *   <li>POST /dev/ai/eval/run — 跑全部 yaml 用例,返回 EvalReport</li>
 *   <li>POST /dev/ai/eval/run/{caseName} — 跑单个用例</li>
 *   <li>GET /dev/ai/eval/cases — 列出所有用例(name + message)</li>
 * </ul>
 *
 * <p>注意:这些调用真的会触发 DeepSeek API(每个 5-30 秒),不要在生产环境启用。
 * EvalRunner 用 isolated userId(默认 9999L),不会污染真实用户 session 数据。
 */
@Slf4j
@RestController
@RequestMapping("/dev/ai/eval")
@ConditionalOnProperty(name = "ai.capability.eval.enabled", havingValue = "true")
@RequiredArgsConstructor
public class EvalController {

    /** dev 配置里固定的 eval 目录 */
    private static final String EVAL_DIR = "src/main/resources/eval";

    private final EvalRunner evalRunner;

    /**
     * 跑所有 yaml 用例。
     * 返回 EvalReport(每个用例的 verdict + 总览)。
     */
    @PostMapping("/run")
    public Result runAll() {
        long t0 = System.currentTimeMillis();
        log.info("[AI][EVAL] POST /dev/ai/eval/run starting");
        EvalReport report = evalRunner.runDirectory(EVAL_DIR);
        long elapsed = System.currentTimeMillis() - t0;
        log.info("[AI][EVAL] POST /dev/ai/eval/run done total={} passed={} failed={} in {}ms",
                report.getTotal(), report.getPassed(), report.getFailed(), elapsed);
        // code: 1 = 全过, 0 = 有失败(运维需要看到 http status 也知道失败)
        int code = (report.getFailed() == 0) ? 1 : 0;
        String msg = (report.getFailed() == 0)
                ? "all " + report.getTotal() + " cases passed in " + elapsed + "ms"
                : report.getFailed() + " of " + report.getTotal() + " cases failed";
        return Result.successWithCode(code, msg, report);
    }

    /**
     * 跑单个 yaml 用例(按 case name 或文件名匹配)。
     */
    @PostMapping("/run/{caseName}")
    public Result runOne(@PathVariable String caseName) {
        log.info("[AI][EVAL] POST /dev/ai/eval/run/{}", caseName);
        EvalVerdict v = evalRunner.runByName(caseName);
        int code = v.isPassed() ? 1 : 0;
        String msg = v.isPassed() ? "case passed" : "case failed: " + v.getReason();
        return Result.successWithCode(code, msg, v);
    }

    /**
     * 列出所有 yaml 用例的元数据。
     */
    @GetMapping("/cases")
    public Result listCases() {
        List<EvalCase> cases = evalRunner.listCases(EVAL_DIR);
        return Result.success(cases);
    }
}
