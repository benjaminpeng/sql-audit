package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.4.5 【Should】避免频繁使用 count() 获取大表行数，资源消耗较大
 */
@Component
public class CountUsageChecker implements SqlChecker {

    private static final Pattern COUNT_PATTERN = Pattern.compile(
            "\\bcount\\s*\\(", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "COUNT_USAGE";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (!"select".equalsIgnoreCase(fragment.getStatementType())) {
            return CheckResult.pass();
        }
        Matcher matcher = COUNT_PATTERN.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail(
                    "检测到 count() 调用，大表资源消耗大，请评估是否必要",
                    matcher.group()
            );
        }
        return CheckResult.pass();
    }
}
