package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检查 SQL 关键字是否大写
 */
@Component
public class KeywordCaseChecker implements SqlChecker {

    private static final Set<String> KEYWORDS = Set.of(
            "select", "from", "where", "and", "or", "not", "in",
            "insert", "into", "values", "update", "set", "delete",
            "join", "left", "right", "inner", "outer", "on",
            "group", "by", "order", "having", "limit", "offset",
            "as", "is", "null", "like", "between", "exists",
            "union", "all", "distinct", "case", "when", "then",
            "else", "end", "create", "alter", "drop", "table",
            "index", "count", "sum", "avg", "max", "min"
    );

    // 匹配 word boundary 的单词
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b([a-zA-Z]+)\\b");

    @Override
    public String name() {
        return "KEYWORD_UPPERCASE";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String sql = fragment.getSqlText();

        // 排除引号内的内容
        String strippedSql = sql.replaceAll("'[^']*'", "''");

        Matcher matcher = WORD_PATTERN.matcher(strippedSql);
        while (matcher.find()) {
            String word = matcher.group(1);
            String lower = word.toLowerCase();

            if (KEYWORDS.contains(lower) && !word.equals(word.toUpperCase())) {
                return CheckResult.fail(
                        String.format("SQL 关键字 '%s' 应使用大写形式 '%s'", word, word.toUpperCase()),
                        word
                );
            }
        }
        return CheckResult.pass();
    }
}
