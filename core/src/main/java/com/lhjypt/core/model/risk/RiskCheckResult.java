package com.lhjypt.core.model.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 风控检查结果
 */
public class RiskCheckResult {

    /** 是否通过（所有规则均通过） */
    private boolean passed;

    /** 触发的规则结果明细 */
    private List<RuleViolation> violations = new ArrayList<>();

    /**
     * 添加违规项
     */
    public void addViolation(String ruleCode, String ruleName,
                             String message, String action) {
        violations.add(new RuleViolation(ruleCode, ruleName, message, action));
        if ("REJECT".equals(action)) {
            passed = false;
        }
    }

    public static RiskCheckResult pass() {
        RiskCheckResult r = new RiskCheckResult();
        r.passed = true;
        return r;
    }

    /**
     * 规则违规明细
     */
    public record RuleViolation(String ruleCode, String ruleName,
                                String message, String action) {}

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public List<RuleViolation> getViolations() {
        return violations;
    }

    @Override
    public String toString() {
        return "RiskCheckResult{passed=" + passed + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RiskCheckResult that = (RiskCheckResult) o;
        return Objects.equals(passed, that.passed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(passed);
    }
}
