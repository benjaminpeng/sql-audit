package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.2.2 【Should】访问对象时建议带上SCHEMA名称
 */
@Component
public class SchemaPrefixChecker implements SqlChecker {

    // 匹配 FROM/JOIN/UPDATE/DELETE FROM/INSERT INTO 后面的表名，不带 schema 前缀 (没有 .)
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "\\b(?:from|join|update|into)\\s+([a-zA-Z_][a-zA-Z0-9_]*)(?!\\.)",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> IGNORED_TABLES = Set.of(
            "dual", "unnest", "generate_series", "information_schema");

    @Override
    public String name() {
        return "SCHEMA_PREFIX";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String type = fragment.getStatementType();
        // 适用于 select, update, delete, insert
        if (type == null)
            return CheckResult.pass();

        Matcher matcher = TABLE_PATTERN.matcher(fragment.getSqlText());
        while (matcher.find()) {
            String tableName = matcher.group(1);
            if (!IGNORED_TABLES.contains(tableName.toLowerCase())) {
                return CheckResult.warn(
                        "表 '" + tableName + "' 未指定Schema前缀(如 public." + tableName + ")，可能增加搜索开销",
                        matcher.group());
            }
        }
        return CheckResult.pass();
    }
}
