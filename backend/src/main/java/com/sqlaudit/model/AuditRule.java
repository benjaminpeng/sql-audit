package com.sqlaudit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审查规则模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRule {

    /** 规则唯一标识 */
    private String id;

    /** 规则名称 */
    private String name;

    /** 规则描述 */
    private String description;

    /** 严重等级: ERROR, WARNING, INFO */
    private Severity severity;

    /** 规则类型: REGEX（正则匹配）, BUILT_IN（内置检查器） */
    private RuleType type;

    /** 正则表达式（当 type=REGEX 时使用） */
    private String pattern;

    /** 内置检查器名称（当 type=BUILT_IN 时使用） */
    private String checkerName;

    /** 规范章节号（如 "3.3.1"） */
    private String section;

    /** 规则分类（如 "WHERE子句"、"SELECT"） */
    private String category;

    /** 规则来源: DEFAULT（内置默认）, CUSTOM（用户上传） */
    private RuleSource source;

    public enum Severity {
        ERROR, WARNING, INFO
    }

    public enum RuleType {
        REGEX, BUILT_IN
    }

    public enum RuleSource {
        DEFAULT, CUSTOM
    }
}
