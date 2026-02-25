package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.2.2 【Should】访问对象（表，函数等）时建议带上SCHEMA名称
 */
@Component
public class SchemaPrefixChecker implements SqlChecker {

    // 匹配 FROM table 或 JOIN table，且 table 后没有 .
    // (?:from|join) : 非捕获组
    // \s+ : 空格
    // ([a-zA-Z0-9_]+) : 表名（捕获组1）
    // (?!\.) : 负向先行断言，后面不能跟 .
    // \b : 单词边界
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "\\b(?:from|join)\\s+([a-zA-Z0-9_]+)(?!\\.)\\b", Pattern.CASE_INSENSITIVE);

    private static final Set<String> IGNORED_TABLES = Set.of("dual", "unnest");

    @Override
    public String name() {
        return "SCHEMA_PREFIX";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (!"select".equalsIgnoreCase(fragment.getStatementType())) {
            return CheckResult.pass();
        }

        Matcher matcher = TABLE_PATTERN.matcher(fragment.getSqlText());
        while (matcher.find()) {
            String tableName = matcher.group(1);
            if (!IGNORED_TABLES.contains(tableName.toLowerCase())) {
                return CheckResult.warn(
                        "表 '" + tableName + "' 未指定Schema前缀(如 public." + tableName + ")，可能增加搜索开销",
                        matcher.group()
                );
            }
        }
        return CheckResult.pass();
    }
}
