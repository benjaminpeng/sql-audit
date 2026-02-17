package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检查是否使用了 SELECT *
 */
@Component
public class NoSelectStarChecker implements SqlChecker {

    private static final Pattern SELECT_STAR = Pattern.compile(
            "\\bSELECT\\s+\\*\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String name() {
        return "NO_SELECT_STAR";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (!"select".equalsIgnoreCase(fragment.getStatementType())) {
            return CheckResult.pass();
        }
        Matcher matcher = SELECT_STAR.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail(
                    "禁止使用 SELECT *，请明确指定需要查询的列",
                    matcher.group()
            );
        }
        return CheckResult.pass();
    }
}
