package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 检查 SELECT 语句是否有 LIMIT 限制
 */
@Component
public class RequireLimitChecker implements SqlChecker {

    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "\\bLIMIT\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String name() {
        return "REQUIRE_LIMIT";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (!"select".equalsIgnoreCase(fragment.getStatementType())) {
            return CheckResult.pass();
        }
        String sql = fragment.getSqlText();

        // 跳过 count 查询
        if (sql.toUpperCase().matches(".*\\bSELECT\\s+COUNT\\s*\\(.*")) {
            return CheckResult.pass();
        }

        if (!LIMIT_PATTERN.matcher(sql).find()) {
            return CheckResult.fail(
                    "SELECT 查询建议添加 LIMIT 限制，避免大数据量查询影响性能",
                    sql.length() > 80 ? sql.substring(0, 80) + "..." : sql
            );
        }
        return CheckResult.pass();
    }
}
