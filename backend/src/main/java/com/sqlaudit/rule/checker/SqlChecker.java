package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;

/**
 * SQL 规则检查器接口
 */
public interface SqlChecker {

    /**
     * 检查器名称，对应 AuditRule.checkerName
     */
    String name();

    /**
     * 检查 SQL 片段，返回违规描述；如果通过返回 null
     */
    CheckResult check(SqlFragment fragment);

    record CheckResult(boolean violated, String message, String matchedText) {
        public static CheckResult pass() {
            return new CheckResult(false, null, null);
        }

        public static CheckResult fail(String message, String matchedText) {
            return new CheckResult(true, message, matchedText);
        }

        public static CheckResult warn(String message, String matchedText) {
            return new CheckResult(true, message, matchedText);
        }
    }
}
