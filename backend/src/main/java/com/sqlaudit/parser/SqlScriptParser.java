package com.sqlaudit.parser;

import com.sqlaudit.model.SqlFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 原始 SQL 脚本解析器
 * <p>
 * 将上传的 .sql 文件按分号拆分为独立的 SQL 语句，
 * 自动识别语句类型并封装为 {@link SqlFragment} 供规则检查复用。
 */
@Component
public class SqlScriptParser {

    private static final Logger log = LoggerFactory.getLogger(SqlScriptParser.class);

    private static final Pattern STATEMENT_TYPE_PATTERN = Pattern.compile(
            "^\\s*(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE|MERGE|REPLACE|WITH)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * 解析 SQL 脚本文本，返回拆分后的 SQL 片段列表
     *
     * @param sqlContent 完整 SQL 脚本内容
     * @param fileName   文件名（用于报告展示）
     * @return SQL 片段列表
     */
    public List<SqlFragment> parse(String sqlContent, String fileName) {
        List<SqlFragment> fragments = new ArrayList<>();

        var statements = splitStatements(sqlContent);
        log.info("从 {} 中解析出 {} 条 SQL 语句", fileName, statements.size());

        for (var entry : statements) {
            var sql = entry.sql().trim();
            if (sql.isBlank() || sql.length() < 3)
                continue;

            var type = detectStatementType(sql);

            fragments.add(SqlFragment.builder()
                    .filePath(fileName)
                    .relativePath(fileName)
                    .statementId(type + "_L" + entry.line())
                    .statementType(type.toLowerCase())
                    .sqlText(sql)
                    .lineNumber(entry.line())
                    .namespace("sql-script")
                    .build());
        }

        return fragments;
    }

    /**
     * 按分号拆分 SQL 语句，正确处理注释和字符串中的分号
     */
    private List<StatementEntry> splitStatements(String content) {
        List<StatementEntry> result = new ArrayList<>();
        var sb = new StringBuilder();
        int lineNumber = 1;
        int stmtStartLine = 1;
        boolean inSingleLineComment = false;
        boolean inBlockComment = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            char next = (i + 1 < content.length()) ? content.charAt(i + 1) : 0;

            // Track line numbers
            if (c == '\n') {
                lineNumber++;
                inSingleLineComment = false;
            }

            // Skip single-line comments
            if (!inBlockComment && !inSingleQuote && !inDoubleQuote
                    && c == '-' && next == '-') {
                inSingleLineComment = true;
            }
            if (inSingleLineComment) {
                if (c != '\n')
                    continue;
                else {
                    inSingleLineComment = false;
                    continue;
                }
            }

            // Handle block comments
            if (!inSingleQuote && !inDoubleQuote && c == '/' && next == '*') {
                inBlockComment = true;
                i++; // skip *
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++; // skip /
                }
                continue;
            }

            // Handle string literals
            if (!inDoubleQuote && c == '\'') {
                inSingleQuote = !inSingleQuote;
            }
            if (!inSingleQuote && c == '"') {
                inDoubleQuote = !inDoubleQuote;
            }

            // Statement delimiter
            if (!inSingleQuote && !inDoubleQuote && c == ';') {
                var stmt = sb.toString().trim();
                if (!stmt.isBlank()) {
                    result.add(new StatementEntry(stmt, stmtStartLine));
                }
                sb.setLength(0);
                stmtStartLine = lineNumber;
                continue;
            }

            // First non-whitespace char of a new statement sets start line
            if (sb.isEmpty() && !Character.isWhitespace(c)) {
                stmtStartLine = lineNumber;
            }

            sb.append(c);
        }

        // Handle last statement without trailing semicolon
        var last = sb.toString().trim();
        if (!last.isBlank()) {
            result.add(new StatementEntry(last, stmtStartLine));
        }

        return result;
    }

    /**
     * 识别 SQL 语句类型
     */
    private String detectStatementType(String sql) {
        Matcher matcher = STATEMENT_TYPE_PATTERN.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return "UNKNOWN";
    }

    /** 内部记录：SQL 文本 + 起始行号 */
    private record StatementEntry(String sql, int line) {
    }
}
