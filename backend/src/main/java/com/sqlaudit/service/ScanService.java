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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern WINDOWS_DRIVE_PATH = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern WSL_UNC_PATH = Pattern.compile("^//wsl(?:\\$|\\.localhost)/[^/]+(/.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 扫描指定路径下的 Java 项目
     */
    public ScanReport scan(String repoPath) {
        List<String> notices = new ArrayList<>();
        String resolvedRepoPath = normalizeRepoPath(repoPath, notices);

        File repoDir = new File(resolvedRepoPath);
        if (!repoDir.exists() || !repoDir.isDirectory()) {
            throw new IllegalArgumentException("路径不存在或不是目录: " + resolvedRepoPath);
        }

        if (isLikelyWsl() && resolvedRepoPath.startsWith("/mnt/")) {
            notices.add("当前扫描目录位于 /mnt 下（Windows 文件系统），WSL 中扫描大仓库可能较慢。建议复制到 /home 下再扫描。");
        }

        Path repoRoot = repoDir.toPath().toAbsolutePath().normalize();

        log.info("开始扫描仓库: {} (原始输入: {})", resolvedRepoPath, repoPath);

        // 1. 查找所有 MyBatis XML 文件
        List<File> mapperFiles = new ArrayList<>();
        findMapperFiles(repoDir, mapperFiles, new HashSet<>());
        log.info("找到 {} 个 MyBatis Mapper 文件", mapperFiles.size());
        if (mapperFiles.isEmpty()) {
            notices.add("未发现 MyBatis Mapper XML 文件，请确认目录路径正确，或确认 XML 文件包含 <mapper> 根节点。");
        }

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
                .repoPath(resolvedRepoPath)
                .scanTime(LocalDateTime.now())
                .totalFiles(mapperFiles.size())
                .totalStatements(allFragments.size())
                .totalViolations(allViolations.size())
                .errorCount((int) errorCount)
                .warningCount((int) warningCount)
                .infoCount((int) infoCount)
                .violations(allViolations)
                .scannedFiles(scannedFiles)
                .notices(List.copyOf(notices))
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
        return scanSqlContent(sqlContent, fileName, Collections.emptyList());
    }

    public ScanReport scanSqlContent(String sqlContent, String fileName, List<String> initialNotices) {
        log.info("开始审查 SQL 脚本: {}", fileName);
        List<String> notices = new ArrayList<>();
        if (initialNotices != null) {
            notices.addAll(initialNotices);
        }

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
                .notices(List.copyOf(notices))
                .limitReached(limitReached)
                .build();
    }

    /**
     * 递归查找 MyBatis Mapper XML 文件
     */
    private void findMapperFiles(File dir, List<File> result, Set<Path> visitedDirs) {
        try {
            Path dirPath = dir.toPath();
            if (Files.isSymbolicLink(dirPath)) {
                log.info("跳过符号链接目录: {}", dir.getAbsolutePath());
                return;
            }
            Path realDir = dirPath.toRealPath();
            if (!visitedDirs.add(realDir)) {
                log.info("跳过重复目录（可能由软链接导致）: {}", realDir);
                return;
            }
        } catch (IOException e) {
            log.warn("无法访问目录，已跳过: {}", dir.getAbsolutePath(), e);
            return;
        }

        File[] files = dir.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (!EXCLUDED_DIRS.contains(file.getName())) {
                    findMapperFiles(file, result, visitedDirs);
                }
            } else if (Files.isSymbolicLink(file.toPath())) {
                log.info("跳过符号链接文件: {}", file.getAbsolutePath());
            } else if (file.getName().endsWith(".xml") && mapperParser.isMyBatisMapper(file)) {
                result.add(file);
            }
        }
    }

    private String normalizeRepoPath(String rawPath, List<String> notices) {
        if (rawPath == null) {
            return null;
        }
        String path = stripWrappingQuotes(rawPath.trim());
        if (path.isBlank()) {
            return path;
        }

        String slashPath = path.replace('\\', '/');

        Matcher uncMatcher = WSL_UNC_PATH.matcher(slashPath);
        if (uncMatcher.matches()) {
            String converted = Optional.ofNullable(uncMatcher.group(1)).filter(s -> !s.isBlank()).orElse("/");
            if (!Objects.equals(converted, path)) {
                notices.add("检测到 WSL UNC 路径，已自动转换为 Linux 路径: " + converted);
            }
            return converted;
        }

        if (WINDOWS_DRIVE_PATH.matcher(path).matches()) {
            char drive = Character.toLowerCase(path.charAt(0));
            String remainder = path.substring(2).replace('\\', '/');
            String converted = "/mnt/" + drive + remainder;
            notices.add("检测到 Windows 路径格式，已尝试自动转换为 WSL 路径: " + converted);
            return converted;
        }

        return path;
    }

    private String stripWrappingQuotes(String path) {
        if (path == null || path.length() < 2) {
            return path;
        }
        char first = path.charAt(0);
        char last = path.charAt(path.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return path.substring(1, path.length() - 1);
        }
        return path;
    }

    private boolean isLikelyWsl() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            return false;
        }
        if (System.getenv("WSL_DISTRO_NAME") != null) {
            return true;
        }
        try {
            return Files.exists(Path.of("/proc/version"))
                    && Files.readString(Path.of("/proc/version")).toLowerCase(Locale.ROOT).contains("microsoft");
        } catch (Exception e) {
            return false;
        }
    }
}
