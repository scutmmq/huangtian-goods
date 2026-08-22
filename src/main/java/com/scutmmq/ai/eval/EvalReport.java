package com.scutmmq.ai.eval;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一轮评估的汇总报告(多 EvalCase → 1 EvalReport)。
 */
@Data
public class EvalReport {

    private long startedAtMillis;
    private long endedAtMillis;
    private int total;
    private int passed;
    private int failed;
    private List<EvalVerdict> verdicts = new ArrayList<>();

    public void add(EvalVerdict v) {
        verdicts.add(v);
        total++;
        if (v.isPassed()) passed++;
        else failed++;
    }

    public double passRate() {
        return total == 0 ? 0.0 : (passed * 1.0 / total);
    }
}
