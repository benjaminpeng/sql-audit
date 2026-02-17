package com.sqlaudit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 从 MyBatis XML 中提取的 SQL 片段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlFragment {

    /** 所在文件的绝对路径 */
    private String filePath;

    /** 所在文件的相对路径（相对于扫描根目录） */
    private String relativePath;

    /** SQL 语句 ID（MyBatis mapper 的 id 属性） */
    private String statementId;

    /** SQL 类型: select, insert, update, delete */
    private String statementType;

    /** 提取的 SQL 文本 */
    private String sqlText;

    /** SQL 在文件中的起始行号 */
    private int lineNumber;

    /** 所属 namespace */
    private String namespace;
}
