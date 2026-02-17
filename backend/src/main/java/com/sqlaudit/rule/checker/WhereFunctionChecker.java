package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.3.3 【Should】不建议 WHERE 条件字段使用表达式或函数（导致索引失效）
 */
@Component
public class WhereFunctionChecker implements SqlChecker {

    private static final Pattern WHERE_FUNC = Pattern.compile(
            "where\\s+.*\\b\\w+\\s*\\([^)]+\\)\\s*(=|>|<|!=|<>|like|in)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "WHERE_FUNCTION";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String sql = fragment.getSqlText();
        if (!sql.toUpperCase().contains("WHERE")) {
            return CheckResult.pass();
        }
        Matcher matcher = WHERE_FUNC.matcher(sql);
        if (matcher.find()) {
            return CheckResult.fail(
                    "WHERE 条件左侧疑似使用了函数/表达式，可能导致索引失效",
                    matcher.group().length() > 80 ? matcher.group().substring(0, 80) + "..." : matcher.group()
            );
        }
        return CheckResult.pass();
    }
}
