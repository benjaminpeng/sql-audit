package com.sqlaudit.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqlaudit.model.ScanReport;
import com.sqlaudit.service.ReportExportService;
import com.sqlaudit.service.ScanService;
import com.sqlaudit.util.TextDecodingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "sql-audit.cli", name = "enabled", havingValue = "true")
public class SqlAuditCliRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(SqlAuditCliRunner.class);

    private final ScanService scanService;
    private final ReportExportService reportExportService;
    private final ObjectMapper objectMapper;

    private volatile int exitCode = 0;

    public SqlAuditCliRunner(ScanService scanService,
                             ReportExportService reportExportService,
                             ObjectMapper objectMapper) {
        this.scanService = scanService;
        this.reportExportService = reportExportService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            exitCode = execute(args);
        } catch (IllegalArgumentException e) {
            exitCode = 2;
            log.error("CLI 参数错误: {}", e.getMessage());
        } catch (Exception e) {
            exitCode = 1;
            log.error("CLI 扫描失败", e);
        }
    }

    int execute(ApplicationArguments args) throws Exception {
        String repoPath = getSingleOption(args, "sql-audit.cli.repo-path");
        String sqlFile = getSingleOption(args, "sql-audit.cli.sql-file");
        String jsonOut = requireSingleOption(args, "sql-audit.cli.json-out");
        String markdownOut = getSingleOption(args, "sql-audit.cli.markdown-out");

        if ((repoPath == null) == (sqlFile == null)) {
            throw new IllegalArgumentException("必须且只能提供一个输入：--sql-audit.cli.repo-path 或 --sql-audit.cli.sql-file");
        }

        Path jsonPath = Path.of(jsonOut);
        createParentDir(jsonPath);

        Path markdownPath = markdownOut == null || markdownOut.isBlank() ? null : Path.of(markdownOut);
        if (markdownPath != null) {
            createParentDir(markdownPath);
        }

        ScanReport report;
        if (repoPath != null) {
            report = scanService.scan(repoPath);
        } else {
            byte[] bytes = Files.readAllBytes(Path.of(sqlFile));
            var decoded = TextDecodingUtils.decodeBestEffort(bytes);
            String filename = Path.of(sqlFile).getFileName().toString();
            String notice = decoded.buildNotice(filename);
            report = scanService.scanSqlContent(
                    decoded.text(),
                    filename,
                    notice == null ? List.of() : List.of(notice));
        }

        byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        Files.write(jsonPath, jsonBytes);

        if (markdownPath != null) {
            ReportExportService.ExportPayload payload = reportExportService.exportMarkdown(report);
            Files.write(markdownPath, payload.content());
        }

        log.info("CLI 扫描完成: files={}, statements={}, violations={}",
                report.getTotalFiles(), report.getTotalStatements(), report.getTotalViolations());
        return 0;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private String requireSingleOption(ApplicationArguments args, String name) {
        String value = getSingleOption(args, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填参数: --" + name);
        }
        return value;
    }

    private String getSingleOption(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            throw new IllegalArgumentException("参数重复: --" + name);
        }
        return values.get(0);
    }

    private void createParentDir(Path path) throws Exception {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
