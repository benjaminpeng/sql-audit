package com.sqlaudit.parser;

import com.sqlaudit.model.AuditRule;
import com.sqlaudit.model.AuditRule.RuleSource;
import com.sqlaudit.model.AuditRule.RuleType;
import com.sqlaudit.model.AuditRule.Severity;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word 文档规则解析器
 * 从上传的 .docx 文件中提取 SQL 审查规则
 *
 * 支持的文档格式:
 * 1. 段落格式: 每条规则占一个段落，格式为 "规则描述"
 * 2. 表格格式: 表格中的每行代表一条规则
 * 3. 列表格式: 有序/无序列表中的每项为一条规则
 */
@Component
public class WordRuleParser {

    private static final Logger log = LoggerFactory.getLogger(WordRuleParser.class);

    // 用于识别规则关键词的模式
    private static final Pattern SEVERITY_PATTERN = Pattern.compile(
            "(?:【(错误|警告|提示|ERROR|WARNING|INFO)】|\\[(错误|警告|提示|ERROR|WARNING|INFO)])",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 解析 Word 文档，提取审查规则
     */
    public List<AuditRule> parse(InputStream inputStream) {
        List<AuditRule> rules = new ArrayList<>();
        try {
            XWPFDocument document = new XWPFDocument(inputStream);

            // 解析表格中的规则
            for (XWPFTable table : document.getTables()) {
                rules.addAll(parseTable(table));
            }

            // 解析段落中的规则
            rules.addAll(parseParagraphs(document.getParagraphs()));

            document.close();
        } catch (Exception e) {
            log.error("解析 Word 文档失败", e);
            throw new RuntimeException("无法解析上传的 Word 文档: " + e.getMessage(), e);
        }

        log.info("从 Word 文档中解析出 {} 条规则", rules.size());
        return rules;
    }

    /**
     * 从表格中解析规则
     * 支持格式: | 规则编号 | 规则描述 | 严重等级 | ...
     */
    private List<AuditRule> parseTable(XWPFTable table) {
        List<AuditRule> rules = new ArrayList<>();
        List<XWPFTableRow> rows = table.getRows();
        if (rows.size() < 2) {
            return rules; // 至少需要表头 + 一行数据
        }

        // 分析表头确定列含义
        XWPFTableRow headerRow = rows.get(0);
        int descCol = -1, severityCol = -1, idCol = -1;

        for (int i = 0; i < headerRow.getTableCells().size(); i++) {
            String header = headerRow.getCell(i).getText().trim().toLowerCase();
            if (header.contains("描述") || header.contains("规则") || header.contains("说明")
                    || header.contains("description") || header.contains("rule")) {
                descCol = i;
            } else if (header.contains("等级") || header.contains("级别") || header.contains("severity")
                    || header.contains("level")) {
                severityCol = i;
            } else if (header.contains("编号") || header.contains("id") || header.contains("序号")) {
                idCol = i;
            }
        }

        // 如果没识别出描述列，用第一列（非编号列）
        if (descCol == -1) {
            descCol = (idCol == 0) ? 1 : 0;
        }

        // 解析数据行
        for (int i = 1; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            try {
                String desc = getCellText(row, descCol);
                if (desc.isBlank()) continue;

                String id = idCol >= 0 ? getCellText(row, idCol) : "CUSTOM_" + i;
                Severity severity = severityCol >= 0
                        ? parseSeverity(getCellText(row, severityCol))
                        : Severity.WARNING;

                AuditRule rule = buildRuleFromDescription(id, desc, severity);
                if (rule != null) {
                    rules.add(rule);
                }
            } catch (Exception e) {
                log.warn("解析表格第 {} 行失败: {}", i + 1, e.getMessage());
            }
        }

        return rules;
    }

    private String getCellText(XWPFTableRow row, int col) {
        if (col < row.getTableCells().size()) {
            return row.getCell(col).getText().trim();
        }
        return "";
    }

    /**
     * 从段落中解析规则
     * 跳过标题段落和空段落，提取带有编号或列表标记的段落
     */
    private List<AuditRule> parseParagraphs(List<XWPFParagraph> paragraphs) {
        List<AuditRule> rules = new ArrayList<>();
        int customIndex = 1;

        for (XWPFParagraph paragraph : paragraphs) {
            String text = paragraph.getText().trim();
            if (text.isBlank() || text.length() < 5) continue;

            // 跳过标题
            String style = paragraph.getStyle();
            if (style != null && style.toLowerCase().contains("heading")) {
                continue;
            }

            // 检测列表项或规则描述
            if (isRuleText(text)) {
                // 去除列表编号前缀
                String cleanText = text.replaceFirst("^(\\d+[.、)）]\\s*|[•·\\-*]\\s*)", "");
                Severity severity = extractSeverity(cleanText);
                String desc = SEVERITY_PATTERN.matcher(cleanText).replaceAll("").trim();

                if (!desc.isBlank() && desc.length() >= 5) {
                    AuditRule rule = buildRuleFromDescription("CUSTOM_P" + customIndex++, desc, severity);
                    if (rule != null) {
                        rules.add(rule);
                    }
                }
            }
        }

        return rules;
    }

    /**
     * 判断文本是否可能是审查规则
     */
    private boolean isRuleText(String text) {
        // 常见的规则描述关键词
        String lower = text.toLowerCase();
        return lower.contains("禁止") || lower.contains("不允许") || lower.contains("必须")
                || lower.contains("应该") || lower.contains("不能") || lower.contains("不得")
                || lower.contains("建议") || lower.contains("要求") || lower.contains("规范")
                || lower.contains("select") || lower.contains("update") || lower.contains("delete")
                || lower.contains("insert") || lower.contains("sql") || lower.contains("索引")
                || lower.contains("where") || lower.contains("table") || lower.contains("字段")
                || text.matches("^\\d+[.、)）].*");
    }

    /**
     * 根据规则描述创建规则对象
     * 通过关键词匹配将自然语言规则映射到内置检查器
     */
    private AuditRule buildRuleFromDescription(String id, String description, Severity severity) {
        String lower = description.toLowerCase();
        AuditRule.AuditRuleBuilder builder = AuditRule.builder()
                .id(id)
                .name(description.length() > 50 ? description.substring(0, 50) + "..." : description)
                .description(description)
                .severity(severity)
                .source(RuleSource.CUSTOM);

        // 关键词映射到内置检查器
        if (lower.contains("select *") || lower.contains("select*") || lower.contains("禁止使用select *")
                || (lower.contains("禁止") && lower.contains("星号"))) {
            return builder.type(RuleType.BUILT_IN).checkerName("NO_SELECT_STAR").build();

        } else if ((lower.contains("update") || lower.contains("delete")) && lower.contains("where")) {
            return builder.type(RuleType.BUILT_IN).checkerName("REQUIRE_WHERE").build();

        } else if (lower.contains("大写") && (lower.contains("关键字") || lower.contains("关键词") || lower.contains("keyword"))) {
            return builder.type(RuleType.BUILT_IN).checkerName("KEYWORD_UPPERCASE").build();

        } else if (lower.contains("${") || lower.contains("注入") || lower.contains("injection") || lower.contains("拼接")) {
            return builder.type(RuleType.BUILT_IN).checkerName("SQL_INJECTION_RISK").build();

        } else if (lower.contains("limit") && (lower.contains("必须") || lower.contains("需要"))) {
            return builder.type(RuleType.BUILT_IN).checkerName("REQUIRE_LIMIT").build();

        } else if (lower.contains("索引") || lower.contains("index")) {
            return builder.type(RuleType.BUILT_IN).checkerName("INDEX_HINT").build();

        } else if (lower.contains("别名") || lower.contains("alias")) {
            return builder.type(RuleType.BUILT_IN).checkerName("REQUIRE_ALIAS").build();

        } else {
            // 无法映射到内置检查器，尝试提取关键词生成正则
            String regex = extractRegexFromDescription(description);
            if (regex != null) {
                return builder.type(RuleType.REGEX).pattern(regex).build();
            }
            // 仍然保留为规则（展示在列表中，但标记为未映射）
            return builder.type(RuleType.BUILT_IN).checkerName("UNMAPPED").build();
        }
    }

    /**
     * 尝试从规则描述中提取正则表达式
     */
    private String extractRegexFromDescription(String description) {
        // 如果描述中有明显引号括起来的内容，当做关键词
        Pattern quoted = Pattern.compile("[\"'\\u201c\\u201d\\u2018\\u2019]([^\"'\\u201c\\u201d\\u2018\\u2019]+)[\"'\\u201c\\u201d\\u2018\\u2019]");
        Matcher matcher = quoted.matcher(description);
        if (matcher.find()) {
            return Pattern.quote(matcher.group(1));
        }
        return null;
    }

    private Severity extractSeverity(String text) {
        Matcher matcher = SEVERITY_PATTERN.matcher(text);
        if (matcher.find()) {
            String s = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            return parseSeverity(s);
        }
        return Severity.WARNING; // 默认
    }

    private Severity parseSeverity(String text) {
        if (text == null) return Severity.WARNING;
        String upper = text.trim().toUpperCase();
        return switch (upper) {
            case "ERROR", "错误" -> Severity.ERROR;
            case "WARNING", "警告" -> Severity.WARNING;
            case "INFO", "提示", "信息" -> Severity.INFO;
            default -> Severity.WARNING;
        };
    }
}
