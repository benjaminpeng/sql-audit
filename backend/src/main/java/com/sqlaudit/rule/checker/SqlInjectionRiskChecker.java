package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检查 SQL 注入风险 — 使用 ${} 拼接的场景
 */
@Component
public class SqlInjectionRiskChecker implements SqlChecker {

    private static final Pattern DOLLAR_PATTERN = Pattern.compile("\\$\\{[^}]*}");

    @Override
    public String name() {
        return "SQL_INJECTION_RISK";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        Matcher matcher = DOLLAR_PATTERN.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail(
                    "存在 SQL 注入风险：使用了 ${} 字符串拼接，建议使用 #{} 参数绑定",
                    matcher.group()
            );
        }
        return CheckResult.pass();
    }
}
