package com.sqlaudit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqlaudit.model.ScanReport;
import com.sqlaudit.model.Violation;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描报告导出服务
 */
@Service
public class ReportExportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ObjectMapper objectMapper;

    public ReportExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ExportPayload exportMarkdown(ScanReport report) {
        String markdown = buildMarkdown(report);
        byte[] content = markdown.getBytes(StandardCharsets.UTF_8);
        return new ExportPayload(
                "sql-audit-report-" + formatFileTs(report.getScanTime()) + ".md",
                "text/markdown;charset=UTF-8",
                content);
    }

    public ExportPayload exportJson(ScanReport report) {
        try {
            byte[] content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
            return new ExportPayload(
                    "sql-audit-report-" + formatFileTs(report.getScanTime()) + ".json",
                    "application/json;charset=UTF-8",
                    content);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 导出失败: " + e.getMessage(), e);
        }
    }

    private String buildMarkdown(ScanReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# SQL 审计规范审查报告\n\n");
        md.append("**扫描时间:** ").append(report.getScanTime() != null ? report.getScanTime() : LocalDateTime.now()).append("\n");
        md.append("**扫描范围:** `").append(escapeInlineCode(scanScope(report))).append("`\n\n");

        if (report.isLimitReached()) {
            md.append("> ⚠️ **警告：扫描结果被截断**\n");
            md.append("> 检测到极多违规项，为保证系统性能，仅保存并展示前 1000 条。建议缩小扫描范围或优化当前规则集。\n\n");
        }

        md.append("## 📊 统计摘要\n");
        md.append("- **扫描文件总数:** ").append(report.getTotalFiles()).append("\n");
        md.append("- **SQL 语句总数:** ").append(report.getTotalStatements()).append("\n");
        md.append("- **违规总数:** ").append(report.getTotalViolations())
                .append(" (❌ 错误: ").append(report.getErrorCount())
                .append(", ⚠️ 警告: ").append(report.getWarningCount())
                .append(", ℹ️ 提示: ").append(report.getInfoCount())
                .append(")\n\n");

        List<Violation> violations = report.getViolations() != null ? report.getViolations() : List.of();
        if (violations.isEmpty()) {
            md.append("✅ **恭喜！所有 SQL 语句均符合规范**\n");
            return md.toString();
        }

        md.append("## 🚫 违规详情\n\n");
        Map<String, List<Violation>> grouped = groupByFile(violations);
        for (Map.Entry<String, List<Violation>> entry : grouped.entrySet()) {
            md.append("### 📄 `").append(escapeInlineCode(entry.getKey())).append("` (")
                    .append(entry.getValue().size()).append(" 项)\n\n");
            for (Violation v : entry.getValue()) {
                String section = v.getRule() != null && v.getRule().getSection() != null
                        ? "§" + v.getRule().getSection() + " "
                        : "";
                String severity = v.getRule() != null && v.getRule().getSeverity() != null
                        ? v.getRule().getSeverity().name()
                        : "UNKNOWN";
                String ruleName = v.getRule() != null && v.getRule().getName() != null
                        ? v.getRule().getName()
                        : "未命名规则";
                String statementType = v.getSqlFragment() != null && v.getSqlFragment().getStatementType() != null
                        ? v.getSqlFragment().getStatementType().toUpperCase()
                        : "UNKNOWN";
                String statementId = v.getSqlFragment() != null && notBlank(v.getSqlFragment().getStatementId())
                        ? v.getSqlFragment().getStatementId()
                        : "unknown";
                int lineNumber = v.getSqlFragment() != null ? v.getSqlFragment().getLineNumber() : 0;

                md.append("**[").append(severity).append("]** ").append(section).append(ruleName).append("\n");
                md.append("- **位置:** 行 ").append(lineNumber).append(" (")
                        .append(statementType).append(" #").append(statementId).append(")\n");
                md.append("- **说明:** ").append(orEmpty(v.getMessage())).append("\n");
                if (notBlank(v.getSuggestion())) {
                    md.append("- **修复建议:** ").append(v.getSuggestion()).append("\n");
                }
                if (notBlank(v.getExampleSql())) {
                    md.append("- **示例改写 SQL（需人工确认）:**\n\n");
                    md.append("```sql\n").append(v.getExampleSql()).append("\n```\n");
                }
                if (notBlank(v.getMatchedText())) {
                    md.append("- **匹配内容:** `")
                            .append(escapeInlineCode(v.getMatchedText().replace("\n", " ")))
                            .append("`\n");
                }
                md.append("\n");
            }
        }

        List<String> files = report.getScannedFiles() != null ? report.getScannedFiles() : List.of();
        md.append("## 📁 扫描文件列表\n\n");
        for (String file : files) {
            md.append("- `").append(escapeInlineCode(file)).append("`\n");
        }
        return md.toString();
    }

    private Map<String, List<Violation>> groupByFile(List<Violation> violations) {
        Map<String, List<Violation>> grouped = new LinkedHashMap<>();
        for (Violation v : violations) {
            String path = "unknown";
            if (v.getSqlFragment() != null && notBlank(v.getSqlFragment().getRelativePath())) {
                path = v.getSqlFragment().getRelativePath();
            }
            grouped.computeIfAbsent(path, k -> new ArrayList<>()).add(v);
        }
        return grouped;
    }

    private String scanScope(ScanReport report) {
        return notBlank(report.getRepoPath()) ? report.getRepoPath() : "SQL 脚本上传模式";
    }

    private String escapeInlineCode(String text) {
        return orEmpty(text).replace("`", "\\`");
    }

    private String orEmpty(String text) {
        return text == null ? "" : text;
    }

    private boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }

    private String formatFileTs(LocalDateTime time) {
        LocalDateTime effective = time != null ? time : LocalDateTime.now();
        return effective.format(FILE_TS);
    }

    public record ExportPayload(String filename, String contentType, byte[] content) {
    }
}
