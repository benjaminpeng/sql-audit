package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 3.6.1 【规格】OpenGauss 不支持 UPDATE 语句中使用 LIMIT
 */
@Component
public class UpdateLimitChecker implements SqlChecker {

    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "\\blimit\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "UPDATE_LIMIT";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (!"update".equalsIgnoreCase(fragment.getStatementType())) {
            return CheckResult.pass();
        }
        if (LIMIT_PATTERN.matcher(fragment.getSqlText()).find()) {
            return CheckResult.fail(
                    "OpenGauss 不支持在 UPDATE 语句中使用 LIMIT，应使用 WHERE 条件明确目标行",
                    "LIMIT in UPDATE"
            );
        }
        return CheckResult.pass();
    }
}
