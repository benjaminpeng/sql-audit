package com.sqlaudit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫描结果报告
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanReport {

    /** 扫描的仓库路径 */
    private String repoPath;

    /** 扫描时间 */
    private LocalDateTime scanTime;

    /** 扫描的文件总数 */
    private int totalFiles;

    /** 扫描的 SQL 语句总数 */
    private int totalStatements;

    /** 违规总数 */
    private int totalViolations;

    /** ERROR 级别违规数 */
    private int errorCount;

    /** WARNING 级别违规数 */
    private int warningCount;

    /** INFO 级别违规数 */
    private int infoCount;

    /** 所有违规记录 */
    private List<Violation> violations;

    /** 扫描的文件列表 */
    private List<String> scannedFiles;

    /** 是否因为违规过多达上限而截断 */
    private boolean limitReached;
}
