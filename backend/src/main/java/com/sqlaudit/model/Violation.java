package com.sqlaudit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条违规记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Violation {

    /** 违反的规则 */
    private AuditRule rule;

    /** 违规的 SQL 片段 */
    private SqlFragment sqlFragment;

    /** 具体违规描述 */
    private String message;

    /** 具体修复建议 */
    private String suggestion;

    /** 示例改写 SQL（半自动生成，需人工确认） */
    private String exampleSql;

    /** 违规匹配到的文本内容 */
    private String matchedText;
}
