package com.sqlaudit.service;

import com.sqlaudit.model.AuditRule;
import com.sqlaudit.model.AuditRule.*;
import com.sqlaudit.model.SqlFragment;
import com.sqlaudit.model.Violation;
import com.sqlaudit.parser.WordRuleParser;
import com.sqlaudit.rule.checker.SqlChecker;
import com.sqlaudit.rule.checker.SqlChecker.CheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 规则管理与检查服务
 */
@Service
public class RuleService {

    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    private final Map<String, SqlChecker> checkerMap;
    private final WordRuleParser wordRuleParser;
    private final List<AuditRule> defaultRules;
    private final List<AuditRule> customRules = new CopyOnWriteArrayList<>();

    public RuleService(List<SqlChecker> checkers, WordRuleParser wordRuleParser) {
        this.wordRuleParser = wordRuleParser;
        this.checkerMap = checkers.stream()
                .collect(Collectors.toMap(SqlChecker::name, c -> c));
        this.defaultRules = buildDefaultRules();
        log.info("加载了 {} 个内置检查器, {} 条默认规则", checkerMap.size(), defaultRules.size());
    }

    public List<AuditRule> getAllRules() {
        List<AuditRule> all = new ArrayList<>(defaultRules);
        all.addAll(customRules);
        return all;
    }

    public List<AuditRule> getDefaultRules() {
        return Collections.unmodifiableList(defaultRules);
    }

    public List<AuditRule> getCustomRules() {
        return Collections.unmodifiableList(customRules);
    }

    public List<AuditRule> loadRulesFromWord(InputStream inputStream) {
        List<AuditRule> parsed = wordRuleParser.parse(inputStream);
        customRules.clear();
        customRules.addAll(parsed);
        log.info("从 Word 文档加载了 {} 条自定义规则", parsed.size());
        return parsed;
    }

    public void clearCustomRules() {
        customRules.clear();
    }

    public List<Violation> checkSql(SqlFragment fragment) {
        List<Violation> violations = new ArrayList<>();
        for (AuditRule rule : getAllRules()) {
            Violation v = applyRule(rule, fragment);
            if (v != null) {
                violations.add(v);
            }
        }
        return violations;
    }

    private Violation applyRule(AuditRule rule, SqlFragment fragment) {
        try {
            return switch (rule.getType()) {
                case BUILT_IN -> {
                    SqlChecker checker = checkerMap.get(rule.getCheckerName());
                    if (checker == null) yield null;
                    CheckResult result = checker.check(fragment);
                    if (result.violated()) {
                        yield Violation.builder()
                                .rule(rule)
                                .sqlFragment(fragment)
                                .message(result.message())
                                .matchedText(result.matchedText())
                                .build();
                    }
                    yield null;
                }
                case REGEX -> {
                    if (rule.getPattern() == null) yield null;
                    Pattern pattern = Pattern.compile(rule.getPattern(), Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(fragment.getSqlText());
                    if (matcher.find()) {
                        yield Violation.builder()
                                .rule(rule)
                                .sqlFragment(fragment)
                                .message("匹配到禁止使用的模式: " + rule.getDescription())
                                .matchedText(matcher.group())
                                .build();
                    }
                    yield null;
                }
                default -> null;
            };
        } catch (Exception e) {
            log.warn("应用规则 {} 时出错: {}", rule.getId(), e.getMessage());
        }
        return null;
    }

    /**
     * 构建默认内置规则 — 对齐 OpenGauss 开发规范 3.2 ~ 3.9
     */
    private List<AuditRule> buildDefaultRules() {
        List<AuditRule> rules = new ArrayList<>();

        // ========== 3.2 对象访问 ==========
        rules.add(AuditRule.builder()
                .id("OG_3_2_2").section("3.2.2").category("对象访问")
                .name("建议使用Schema前缀")
                .description("访问对象（表，函数等）时建议带上SCHEMA名称，避免不必要的性能开销")
                .severity(Severity.WARNING).type(RuleType.BUILT_IN)
                .checkerName("SCHEMA_PREFIX").source(RuleSource.DEFAULT).build());

        // ========== 3.3 WHERE 子句 ==========
        rules.add(AuditRule.builder()
                .id("OG_3_3_1").section("3.3.1").category("WHERE子句")
                .name("禁止用 = 或 != 判断 NULL")
                .description("查询条件中判断 NULL 时，禁止使用 = 和 !=，应使用 IS NULL 或 IS NOT NULL")
                .severity(Severity.ERROR).type(RuleType.BUILT_IN)
                .checkerName("NULL_COMPARISON").source(RuleSource.DEFAULT).build());

        rules.add(AuditRule.builder()
                .id("OG_3_3_3").section("3.3.3").category("WHERE子句")
                .name("WHERE 条件字段禁用函数")
                .description("不建议 WHERE 条件字段使用表达式或函数，会导致索引失效")
                .severity(Severity.WARNING).type(RuleType.BUILT_IN)
                .checkerName("WHERE_FUNCTION").source(RuleSource.DEFAULT).build());

        rules.add(AuditRule.builder()
                .id("OG_3_3_4").section("3.3.4").category("WHERE子句")
                .name("少用负向操作符")
                .description("查询条件中尽量少使用 !=, <>, NOT IN 等无法利用索引的操作符")
                .severity(Severity.WARNING).type(RuleType.BUILT_IN)
                .checkerName("NOT_EQUAL_OPS").source(RuleSource.DEFAULT).build());

        rules.add(AuditRule.builder()
                .id("OG_3_3_5").section("3.3.5").category("WHERE子句")
                .name("LIKE 禁止前缀 %")
                .description("LIKE 语句 % 不应放在首字符位置，会导致全表扫描")
                .severity(Severity.WARNING).type(RuleType.BUILT_IN)
                .checkerName("LIKE_PERCENT_START").source(RuleSource.DEFAULT).build());

        rules.add(AuditRule.builder()
                .id("OG_3_3_6").section("3.3.6").category("WHERE子句")
                .name("IN 子集不宜过大")
                .description("WHERE 条件中 IN 的候选子集不宜超过100个")
                .severity(Severity.WARNING).type(RuleType.BUILT_IN)
                .checkerName("IN_LIST_SIZE").source(RuleSource.DEFAULT).build());

        // ========== 3.4 SELECT ==========
        rules.add(AuditRule.builder()
                .id("OG_3_4_1").section("3.4.1").category("SELECT")
                .name("禁止 SELECT *")
                .description("SELECT 语句中禁用通配符字段 *，请明确指定查询列")
                .severity(Severity.ERROR).type(RuleType.BUILT_IN)
                .checkerName("NO_SELECT_STAR").source(RuleSource.DEFAULT).build());

        rules.add(AuditRule.builder()
                .id("OG_3_4_3").section("3.4.3").category("SELECT")
                .name("禁止 LOCK TABLE")
                .description("禁止使用 LOCK TABLE 语句加锁，仅允许使用 SELECT .. FOR UPDATE")
                .severity(Severity.ERROR).type(RuleType.BUILT_IN)
                .checkerName("LOCK_TABLE").source(RuleSource.DEFAULT).build());

        rules.add(AuditRule.builder()
                .id("OG_3_4_4").section("3.4.4").category("SELECT")
                .name("优先使用 UNION ALL")
                .description("考虑使用 UNION ALL 代替 UNION，避免不必要的去重排序开销")
                .severity(Severity.WARNING).type(RuleType.BUILT_IN)
                .checkerName("UNION_ALL").source(RuleSource.DEFAULT).build());

        rules.add(AuditRule.builder()
                .id("OG_3_4_5").section("3.4.5").category("SELECT")
                .name("慎用 count()")
                .description("避免频繁使用 count() 获取大表行数，资源消耗较大")
                .severity(Severity.INFO).type(RuleType.BUILT_IN)
                .checkerName("COUNT_USAGE").source(RuleSource.DEFAULT).build());

        rules.add(AuditRule.builder()
                .id("OG_3_4_LIMIT").section("3.4.6").category("SELECT")
                .name("SELECT 建议分页")
                .description("SELECT 语句建议使用 LIMIT 分页，避免全表查询")
                .severity(Severity.WARNING).type(RuleType.BUILT_IN)
                .checkerName("REQUIRE_LIMIT").source(RuleSource.DEFAULT).build());

        // ========== 3.6 UPDATE ==========
        rules.add(AuditRule.builder()
                .id("OG_3_6_1").section("3.6.1").category("UPDATE")
                .name("UPDATE 禁用 LIMIT")
                .description("OpenGauss 不支持 UPDATE 语句中使用 LIMIT")
                .severity(Severity.ERROR).type(RuleType.BUILT_IN)
                .checkerName("UPDATE_LIMIT").source(RuleSource.DEFAULT).build());

        rules.add(AuditRule.builder()
                .id("OG_3_6_3").section("3.6.3").category("UPDATE")
                .name("UPDATE 必须有 WHERE")
                .description("UPDATE 语句中必须有 WHERE 子句，避免全表更新")
                .severity(Severity.ERROR).type(RuleType.BUILT_IN)
                .checkerName("REQUIRE_WHERE").source(RuleSource.DEFAULT).build());

        // ========== 3.7 DELETE ==========
        rules.add(AuditRule.builder()
                .id("OG_3_7_2").section("3.7.2").category("DELETE")
                .name("全表删除用 TRUNCATE")
                .description("清空一张表建议使用 TRUNCATE，而不是 DELETE")
                .severity(Severity.ERROR).type(RuleType.BUILT_IN)
                .checkerName("DELETE_TRUNCATE").source(RuleSource.DEFAULT).build());

        rules.add(AuditRule.builder()
                .id("OG_3_7_3").section("3.7.3").category("DELETE")
                .name("DELETE 必须有 WHERE")
                .description("DELETE 语句中必须有 WHERE 子句，避免全表删除")
                .severity(Severity.ERROR).type(RuleType.BUILT_IN)
                .checkerName("REQUIRE_WHERE").source(RuleSource.DEFAULT).build());

        // ========== 3.8 关联查询 ==========
        rules.add(AuditRule.builder()
                .id("OG_3_8_1").section("3.8.1").category("关联查询")
                .name("限制关联表数量")
                .description("禁止超过8表关联，建议不超过5个")
                .severity(Severity.ERROR).type(RuleType.BUILT_IN)
                .checkerName("JOIN_TABLE_COUNT").source(RuleSource.DEFAULT).build());

        rules.add(AuditRule.builder()
                .id("OG_3_8_3").section("3.8.3").category("关联查询")
                .name("禁止隐式 JOIN")
                .description("避免使用逗号分隔的隐式 JOIN，应使用显式 INNER/LEFT/RIGHT JOIN")
                .severity(Severity.ERROR).type(RuleType.BUILT_IN)
                .checkerName("IMPLICIT_JOIN").source(RuleSource.DEFAULT).build());

        // ========== 3.9 子查询 ==========
        rules.add(AuditRule.builder()
                .id("OG_3_9_3").section("3.9.3").category("子查询")
                .name("目标列禁用子查询")
                .description("避免在 SELECT 目标列中使用子查询，可能导致计划无法下推影响执行性能")
                .severity(Severity.WARNING).type(RuleType.BUILT_IN)
                .checkerName("SUBQUERY_IN_TARGET").source(RuleSource.DEFAULT).build());
                
        rules.add(AuditRule.builder()
                .id("OG_3_9_4").section("3.9.4").category("子查询")
                .name("子查询嵌套不超过2层")
                .description("子查询嵌套深度不建议超过2层")
                .severity(Severity.WARNING).type(RuleType.BUILT_IN)
                .checkerName("SUBQUERY_DEPTH").source(RuleSource.DEFAULT).build());

        // ========== MyBatis 专属 ==========
        rules.add(AuditRule.builder()
                .id("OG_MYBATIS_INJECTION").section("MyBatis").category("安全")
                .name("SQL 注入风险")
                .description("MyBatis 中使用 ${} 存在 SQL 注入风险，应使用 #{} 参数绑定")
                .severity(Severity.ERROR).type(RuleType.BUILT_IN)
                .checkerName("SQL_INJECTION_RISK").source(RuleSource.DEFAULT).build());

        return rules;
    }
}
