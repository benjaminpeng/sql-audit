package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.3.1 【Must】禁止使用 = 或 != 判断 NULL，必须使用 IS NULL / IS NOT NULL
 */
@Component
public class NullComparisonChecker implements SqlChecker {

    private static final Pattern NULL_COMPARE = Pattern.compile(
            "(=|!=|<>)\\s*null\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "NULL_COMPARISON";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        Matcher matcher = NULL_COMPARE.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail(
                    "禁止使用比较操作符判断 NULL，必须使用 IS NULL 或 IS NOT NULL",
                    matcher.group()
            );
        }
        return CheckResult.pass();
    }
}
