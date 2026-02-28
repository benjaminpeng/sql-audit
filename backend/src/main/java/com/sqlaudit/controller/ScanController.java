package com.sqlaudit.controller;

import com.sqlaudit.model.AuditRule;
import com.sqlaudit.model.ScanReport;
import com.sqlaudit.service.ReportExportService;
import com.sqlaudit.service.RuleService;
import com.sqlaudit.service.ScanService;
import com.sqlaudit.util.TextDecodingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL 审查 API 控制器
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ScanController {

    private static final Logger log = LoggerFactory.getLogger(ScanController.class);

    private final ScanService scanService;
    private final RuleService ruleService;
    private final ReportExportService reportExportService;

    public ScanController(ScanService scanService, RuleService ruleService, ReportExportService reportExportService) {
        this.scanService = scanService;
        this.ruleService = ruleService;
        this.reportExportService = reportExportService;
    }

    /**
     * 扫描指定仓库路径
     */
    @PostMapping("/scan")
    public ResponseEntity<?> scan(@RequestBody Map<String, String> request) {
        String repoPath = request.get("repoPath");
        if (repoPath == null || repoPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请提供仓库路径 (repoPath)"));
        }

        try {
            log.info("收到扫描请求: {}", repoPath);
            ScanReport report = scanService.scan(repoPath);
            scanService.cacheLastScanReport(report);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("扫描失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "扫描过程中出错: " + e.getMessage()));
        }
    }

    /**
     * 上传 SQL 脚本文件进行审查
     */
    @PostMapping("/scan/sql")
    public ResponseEntity<?> scanSql(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请上传文件"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".sql")) {
            return ResponseEntity.badRequest().body(Map.of("error", "请上传 .sql 格式的 SQL 脚本文件"));
        }

        try {
            byte[] bytes = file.getBytes();
            var decoded = TextDecodingUtils.decodeBestEffort(bytes);
            String sqlContent = decoded.text();
            log.info("收到 SQL 脚本审查请求: {}, 大小: {} bytes, 编码: {}", filename, bytes.length, decoded.charsetName());

            List<String> notices = new ArrayList<>();
            String decodeNotice = decoded.buildNotice(filename);
            if (decodeNotice != null) {
                notices.add(decodeNotice);
            }

            ScanReport report = scanService.scanSqlContent(sqlContent, filename, notices);
            scanService.cacheLastScanReport(report);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("SQL 脚本审查失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "审查过程中出错: " + e.getMessage()));
        }
    }

    /**
     * 上传 Word 文档审查规范
     */
    @PostMapping("/rules/upload")
    public ResponseEntity<?> uploadRules(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请上传文件"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
            return ResponseEntity.badRequest().body(Map.of("error", "请上传 .docx 格式的 Word 文档"));
        }

        try {
            List<AuditRule> rules = ruleService.loadRulesFromWord(file.getInputStream());
            return ResponseEntity.ok(Map.of(
                    "message", "成功加载 " + rules.size() + " 条审查规则",
                    "rules", rules));
        } catch (Exception e) {
            log.error("上传规则文件失败", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "解析文件失败: " + e.getMessage()));
        }
    }

    /**
     * 获取当前所有规则（默认 + 自定义）
     */
    @GetMapping("/rules")
    public ResponseEntity<List<AuditRule>> getAllRules() {
        return ResponseEntity.ok(ruleService.getAllRules());
    }

    /**
     * 获取默认内置规则
     */
    @GetMapping("/rules/default")
    public ResponseEntity<List<AuditRule>> getDefaultRules() {
        return ResponseEntity.ok(ruleService.getDefaultRules());
    }

    /**
     * 获取用户自定义规则
     */
    @GetMapping("/rules/custom")
    public ResponseEntity<List<AuditRule>> getCustomRules() {
        return ResponseEntity.ok(ruleService.getCustomRules());
    }

    /**
     * 清除自定义规则
     */
    @DeleteMapping("/rules/custom")
    public ResponseEntity<?> clearCustomRules() {
        ruleService.clearCustomRules();
        return ResponseEntity.ok(Map.of("message", "已清除自定义规则"));
    }

    /**
     * 导出 Markdown 报告（优先使用请求体中的报告；未传时回退到服务端最近一次扫描结果）
     */
    @PostMapping("/report/export/markdown")
    public ResponseEntity<?> exportMarkdown(@RequestBody(required = false) ScanReport report) {
        return exportReport("markdown", report);
    }

    /**
     * 导出 JSON 报告（优先使用请求体中的报告；未传时回退到服务端最近一次扫描结果）
     */
    @PostMapping("/report/export/json")
    public ResponseEntity<?> exportJson(@RequestBody(required = false) ScanReport report) {
        return exportReport("json", report);
    }

    /**
     * 直接下载最近一次 Markdown 报告（便于浏览器直接触发下载）
     */
    @GetMapping("/report/export/markdown")
    public ResponseEntity<?> exportMarkdownLatest() {
        return exportReport("markdown", null);
    }

    /**
     * 直接下载最近一次 JSON 报告（便于浏览器直接触发下载）
     */
    @GetMapping("/report/export/json")
    public ResponseEntity<?> exportJsonLatest() {
        return exportReport("json", null);
    }

    private ResponseEntity<?> exportReport(String format, ScanReport requestReport) {
        try {
            ScanReport report = resolveReportForExport(requestReport);
            if (report == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "暂无可导出的审查报告，请先执行一次扫描"));
            }

            ReportExportService.ExportPayload payload;
            if ("markdown".equalsIgnoreCase(format)) {
                payload = reportExportService.exportMarkdown(report);
            } else if ("json".equalsIgnoreCase(format)) {
                payload = reportExportService.exportJson(report);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "不支持的导出格式: " + format));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(payload.contentType()));
            headers.setContentLength(payload.content().length);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(payload.filename(), StandardCharsets.UTF_8)
                    .build());
            return new ResponseEntity<>(payload.content(), headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("导出报告失败, format={}", format, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "导出失败: " + e.getMessage()));
        }
    }

    private ScanReport resolveReportForExport(ScanReport requestReport) {
        if (requestReport != null && requestReport.getScanTime() != null) {
            return requestReport;
        }
        return scanService.getLastScanReport().orElse(null);
    }
}
