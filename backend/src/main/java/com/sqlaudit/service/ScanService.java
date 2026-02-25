package com.sqlaudit.service;

import com.sqlaudit.model.ScanReport;
import com.sqlaudit.model.SqlFragment;
import com.sqlaudit.model.Violation;
import com.sqlaudit.model.AuditRule.Severity;
import com.sqlaudit.parser.MyBatisMapperParser;
import com.sqlaudit.parser.SqlScriptParser;
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
            "node_modules", ".mvn", "bin", "out", ".gradle");

    private final MyBatisMapperParser mapperParser;
    private final SqlScriptParser sqlScriptParser;
    private final RuleService ruleService;

    public ScanService(MyBatisMapperParser mapperParser, SqlScriptParser sqlScriptParser, RuleService ruleService) {
        this.mapperParser = mapperParser;
        this.sqlScriptParser = sqlScriptParser;
        this.ruleService = ruleService;
    }

    private static final int MAX_VIOLATIONS = 1000;

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
        boolean limitReached = false;
        for (SqlFragment fragment : allFragments) {
            if (limitReached)
                break;
            List<Violation> violations = ruleService.checkSql(fragment);
            for (Violation v : violations) {
                if (allViolations.size() >= MAX_VIOLATIONS) {
                    limitReached = true;
                    break;
                }
                allViolations.add(v);
            }
        }
        if (limitReached) {
            log.warn("违规数量达到上限 {}，停止进一步扫描", MAX_VIOLATIONS);
        } else {
            log.info("发现 {} 条违规", allViolations.size());
        }

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
                .limitReached(limitReached)
                .build();
    }

    /**
     * 审查上传的 SQL 脚本内容
     *
     * @param sqlContent SQL 脚本文本
     * @param fileName   文件名
     * @return 扫描报告
     */
    public ScanReport scanSqlContent(String sqlContent, String fileName) {
        log.info("开始审查 SQL 脚本: {}", fileName);

        // 1. 解析 SQL 语句
        List<SqlFragment> fragments = sqlScriptParser.parse(sqlContent, fileName);
        log.info("从 {} 中提取了 {} 条 SQL 语句", fileName, fragments.size());

        // 2. 执行规则检查
        List<Violation> allViolations = new ArrayList<>();
        boolean limitReached = false;
        for (SqlFragment fragment : fragments) {
            if (limitReached)
                break;
            List<Violation> violations = ruleService.checkSql(fragment);
            for (Violation v : violations) {
                if (allViolations.size() >= MAX_VIOLATIONS) {
                    limitReached = true;
                    break;
                }
                allViolations.add(v);
            }
        }
        if (limitReached) {
            log.warn("SQL脚本违规数量达到上限 {}，停止进一步扫描", MAX_VIOLATIONS);
        } else {
            log.info("发现 {} 条违规", allViolations.size());
        }

        // 3. 构建报告
        long errorCount = allViolations.stream()
                .filter(v -> v.getRule().getSeverity() == Severity.ERROR).count();
        long warningCount = allViolations.stream()
                .filter(v -> v.getRule().getSeverity() == Severity.WARNING).count();
        long infoCount = allViolations.stream()
                .filter(v -> v.getRule().getSeverity() == Severity.INFO).count();

        return ScanReport.builder()
                .repoPath(fileName)
                .scanTime(LocalDateTime.now())
                .totalFiles(1)
                .totalStatements(fragments.size())
                .totalViolations(allViolations.size())
                .errorCount((int) errorCount)
                .warningCount((int) warningCount)
                .infoCount((int) infoCount)
                .violations(allViolations)
                .scannedFiles(List.of(fileName))
                .limitReached(limitReached)
                .build();
    }

    /**
     * 递归查找 MyBatis Mapper XML 文件
     */
    private void findMapperFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null)
            return;

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
