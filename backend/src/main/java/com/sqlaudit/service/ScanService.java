package com.sqlaudit.service;

import com.sqlaudit.model.ScanReport;
import com.sqlaudit.model.SqlFragment;
import com.sqlaudit.model.Violation;
import com.sqlaudit.model.AuditRule.Severity;
import com.sqlaudit.parser.MyBatisMapperParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 代码仓库扫描服务
 */
@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", ".idea", ".vscode", "target", "build",
            "node_modules", ".mvn", "bin", "out", ".gradle"
    );

    private final MyBatisMapperParser mapperParser;
    private final RuleService ruleService;

    public ScanService(MyBatisMapperParser mapperParser, RuleService ruleService) {
        this.mapperParser = mapperParser;
        this.ruleService = ruleService;
    }

    /**
     * 扫描指定路径下的 Java 项目
     */
    public ScanReport scan(String repoPath) {
        File repoDir = new File(repoPath);
        if (!repoDir.exists() || !repoDir.isDirectory()) {
            throw new IllegalArgumentException("路径不存在或不是目录: " + repoPath);
        }

        Path repoRoot = repoDir.toPath();

        log.info("开始扫描仓库: {}", repoPath);

        // 1. 查找所有 MyBatis XML 文件
        List<File> mapperFiles = new ArrayList<>();
        findMapperFiles(repoDir, mapperFiles);
        log.info("找到 {} 个 MyBatis Mapper 文件", mapperFiles.size());

        // 2. 解析 SQL 片段
        List<SqlFragment> allFragments = new ArrayList<>();
        for (File file : mapperFiles) {
            List<SqlFragment> fragments = mapperParser.parse(file, repoRoot);
            allFragments.addAll(fragments);
        }
        log.info("提取了 {} 条 SQL 语句", allFragments.size());

        // 3. 执行规则检查
        List<Violation> allViolations = new ArrayList<>();
        for (SqlFragment fragment : allFragments) {
            List<Violation> violations = ruleService.checkSql(fragment);
            allViolations.addAll(violations);
        }
        log.info("发现 {} 条违规", allViolations.size());

        // 4. 构建报告
        long errorCount = allViolations.stream()
                .filter(v -> v.getRule().getSeverity() == Severity.ERROR).count();
        long warningCount = allViolations.stream()
                .filter(v -> v.getRule().getSeverity() == Severity.WARNING).count();
        long infoCount = allViolations.stream()
                .filter(v -> v.getRule().getSeverity() == Severity.INFO).count();

        List<String> scannedFiles = mapperFiles.stream()
                .map(f -> repoRoot.relativize(f.toPath()).toString())
                .toList();

        return ScanReport.builder()
                .repoPath(repoPath)
                .scanTime(LocalDateTime.now())
                .totalFiles(mapperFiles.size())
                .totalStatements(allFragments.size())
                .totalViolations(allViolations.size())
                .errorCount((int) errorCount)
                .warningCount((int) warningCount)
                .infoCount((int) infoCount)
                .violations(allViolations)
                .scannedFiles(scannedFiles)
                .build();
    }

    /**
     * 递归查找 MyBatis Mapper XML 文件
     */
    private void findMapperFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (!EXCLUDED_DIRS.contains(file.getName())) {
                    findMapperFiles(file, result);
                }
            } else if (file.getName().endsWith(".xml") && mapperParser.isMyBatisMapper(file)) {
                result.add(file);
            }
        }
    }
}
