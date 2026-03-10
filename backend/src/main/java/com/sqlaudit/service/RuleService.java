package com.sqlaudit.service;

import com.sqlaudit.model.AuditRule;
import com.sqlaudit.model.AuditRule.*;
import com.sqlaudit.model.SqlFragment;
import com.sqlaudit.model.Violation;
import com.sqlaudit.parser.WordRuleParser;
import com.sqlaudit.rule.checker.SqlChecker;
import com.sqlaudit.rule.checker.SqlChecker.CheckResult;
import com.sqlaudit.rule.checker.SqlScriptChecker;
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
        private static final Pattern TRAILING_SEMICOLON = Pattern.compile(";\\s*$");
        private static final Pattern SELECT_STAR_REWRITE = Pattern.compile(
                        "\\bSELECT\\s+(DISTINCT\\s+)?\\*\\b",
                        Pattern.CASE_INSENSITIVE);
        private static final Pattern NULL_EQ_REWRITE = Pattern.compile("=\\s*NULL\\b", Pattern.CASE_INSENSITIVE);
        private static final Pattern NULL_NE_REWRITE = Pattern.compile("(!=|<>)\\s*NULL\\b", Pattern.CASE_INSENSITIVE);
        private static final Pattern UNION_WITHOUT_ALL = Pattern.compile("\\bUNION\\b(?!\\s+ALL\\b)",
                        Pattern.CASE_INSENSITIVE);
        private static final Pattern DOLLAR_PARAM = Pattern.compile("\\$\\{\\s*([^}]+?)\\s*}");
        private static final Pattern SIMPLE_DELETE_TABLE = Pattern.compile("^\\s*DELETE\\s+FROM\\s+([\\w.]+)\\s*;?\\s*$",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        private static final Pattern LOCK_TABLE_PATTERN = Pattern.compile("\\bLOCK\\s+TABLE\\s+([\\w.]+)\\b",
                        Pattern.CASE_INSENSITIVE);
        private static final Pattern UPDATE_LIMIT_CLAUSE = Pattern.compile("\\s+LIMIT\\s+\\d+(\\s*,\\s*\\d+)?\\s*$",
                        Pattern.CASE_INSENSITIVE);
        private static final Pattern LIKE_PREFIX_LITERAL = Pattern.compile(
                        "(?i)LIKE\\s+(['\"])%([^'\"]*)\\1");
        private static final Pattern TABLE_REF_NO_SCHEMA = Pattern.compile(
                        "\\b(from|join|update|into)\\s+([a-zA-Z_][a-zA-Z0-9_]*)(?!\\.)",
                        Pattern.CASE_INSENSITIVE);
        private static final Pattern WORD_PATTERN = Pattern.compile("\\b([a-zA-Z]+)\\b");
        private static final Set<String> SQL_KEYWORDS = Set.of(
                        "select", "from", "where", "and", "or", "not", "in",
                        "insert", "into", "values", "update", "set", "delete",
                        "join", "left", "right", "inner", "outer", "on",
                        "group", "by", "order", "having", "limit", "offset",
                        "as", "is", "null", "like", "between", "exists",
                        "union", "all", "distinct", "case", "when", "then",
                        "else", "end", "create", "alter", "drop", "table",
                        "truncate", "for", "count", "sum", "avg", "max", "min");

        private final Map<String, SqlChecker> checkerMap;
        private final Map<String, SqlScriptChecker> scriptCheckerMap;
        private final WordRuleParser wordRuleParser;
        private final List<AuditRule> defaultRules;
        private final List<AuditRule> customRules = new CopyOnWriteArrayList<>();
        private final Map<String, Pattern> patternCache = new java.util.concurrent.ConcurrentHashMap<>();

        public RuleService(List<SqlChecker> checkers, List<SqlScriptChecker> scriptCheckers, WordRuleParser wordRuleParser) {
                this.wordRuleParser = wordRuleParser;
                this.checkerMap = checkers.stream()
                                .collect(Collectors.toMap(SqlChecker::name, c -> c));
                this.scriptCheckerMap = scriptCheckers.stream()
                                .collect(Collectors.toMap(SqlScriptChecker::name, c -> c));
                this.defaultRules = buildDefaultRules();
                log.info("加载了 {} 个逐语句检查器, {} 个脚本检查器, {} 条默认规则",
                                checkerMap.size(), scriptCheckerMap.size(), defaultRules.size());
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
                patternCache.clear(); // 清除缓存，重新编译自定义规则的正则
                log.info("从 Word 文档加载了 {} 条自定义规则", parsed.size());
                return parsed;
        }

        public void clearCustomRules() {
                customRules.clear();
                patternCache.clear();
        }

        public List<Violation> checkSql(SqlFragment fragment) {
                List<Violation> violations = new ArrayList<>();
                for (AuditRule rule : getAllRules()) {
                        if (!ruleAppliesToFragment(rule, fragment)) {
                                continue;
                        }
                        Violation v = applyRule(rule, fragment);
                        if (v != null) {
                                violations.add(v);
                        }
                }
                return violations;
        }

        public List<Violation> checkSqlScript(List<SqlFragment> fragments) {
                if (fragments == null || fragments.isEmpty()) {
                        return List.of();
                }

                List<Violation> violations = new ArrayList<>();
                for (AuditRule rule : getAllRules()) {
                        if (rule.getType() != RuleType.BUILT_IN || !ruleAppliesToSqlScript(rule)) {
                                continue;
                        }

                        SqlScriptChecker checker = scriptCheckerMap.get(rule.getCheckerName());
                        if (checker == null) {
                                continue;
                        }

                        try {
                                for (SqlScriptChecker.CheckResult result : checker.check(fragments)) {
                                        if (result.fragment() == null) {
                                                continue;
                                        }
                                        violations.add(Violation.builder()
                                                        .rule(rule)
                                                        .sqlFragment(result.fragment())
                                                        .message(result.message())
                                                        .suggestion(buildSuggestion(rule, result.fragment(), null))
                                                        .exampleSql(null)
                                                        .matchedText(result.matchedText())
                                                        .build());
                                }
                        } catch (Exception e) {
                                log.warn("应用脚本规则 {} 时出错: {}", rule.getId(), e.getMessage());
                        }
                }
                return violations;
        }

        private Violation applyRule(AuditRule rule, SqlFragment fragment) {
                try {
                        return switch (rule.getType()) {
                                case BUILT_IN -> {
                                        SqlChecker checker = checkerMap.get(rule.getCheckerName());
                                        if (checker == null)
                                                yield null;
                                        CheckResult result = checker.check(fragment);
                                        if (result.violated()) {
                                                yield Violation.builder()
                                                                .rule(rule)
                                                                .sqlFragment(fragment)
                                                                .message(result.message())
                                                                .suggestion(buildSuggestion(rule, fragment, result))
                                                                .exampleSql(buildExampleSql(rule, fragment, result))
                                                                .matchedText(result.matchedText())
                                                                .build();
                                        }
                                        yield null;
                                }
                                case REGEX -> {
                                        if (rule.getPattern() == null)
                                                yield null;
                                        Pattern pattern = patternCache.computeIfAbsent(
                                                        rule.getPattern(),
                                                        p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE));
                                        Matcher matcher = pattern.matcher(fragment.getSqlText());
                                        if (matcher.find()) {
                                                yield Violation.builder()
                                                                .rule(rule)
                                                                .sqlFragment(fragment)
                                                                .message("匹配到禁止使用的模式: " + rule.getDescription())
                                                                .suggestion(buildRegexSuggestion(rule, matcher.group()))
                                                                .exampleSql(buildRegexExampleSql(rule, fragment, matcher.group()))
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

        private String buildSuggestion(AuditRule rule, SqlFragment fragment, CheckResult result) {
                String checkerName = rule.getCheckerName();
                if (checkerName == null || checkerName.isBlank()) {
                        return buildGenericSuggestion(rule, fragment);
                }

                String type = fragment.getStatementType() == null ? "" : fragment.getStatementType().toLowerCase(Locale.ROOT);
                return switch (checkerName) {
                        case "NO_SELECT_STAR" -> "将 `SELECT *` 改为显式列名，例如 `SELECT id, name FROM ...`，避免多取字段和后续表结构变更风险。";
                        case "NULL_COMPARISON" -> "将 `= NULL` / `!= NULL` 改为 `IS NULL` / `IS NOT NULL`。例如：`col IS NULL`。";
                        case "WHERE_FUNCTION" -> "尽量避免在 WHERE 条件列上包函数；可把函数移到常量侧，或新增冗余列/函数索引后再查询。";
                        case "NOT_EQUAL_OPS" -> "优先改写为正向条件（如 `IN`、范围条件、等值匹配），减少 `!=`/`<>` 导致的索引利用下降。";
                        case "LIKE_PERCENT_START" -> "避免前置 `%`（如 `%abc`）。优先使用前缀匹配（如 `abc%`）；若必须模糊搜索，考虑全文索引/检索方案。";
                        case "IN_LIST_SIZE" -> "缩小 `IN (...)` 列表规模（建议分批 <= 100），或改为临时表/批量表 JOIN。";
                        case "LOCK_TABLE" -> "避免 `LOCK TABLE`；如需并发控制，优先使用事务 + `SELECT ... FOR UPDATE`。";
                        case "UNION_ALL" -> "若业务不需要去重，将 `UNION` 改为 `UNION ALL`，减少排序和去重开销。";
                        case "COUNT_USAGE" -> "确认 `count()` 是否必须实时精确；大表可考虑缓存计数、近似统计或增加更精确的过滤条件。";
                        case "REQUIRE_LIMIT" -> "为大结果集查询增加分页条件（如 `LIMIT ? OFFSET ?`），并搭配稳定排序（如 `ORDER BY id`）。";
                        case "UPDATE_LIMIT" -> "OpenGauss 不支持 `UPDATE ... LIMIT`；可改为子查询/CTE 先选主键，再按主键 UPDATE。";
                        case "UPDATE_REQUIRE_WHERE" -> "为 `UPDATE` 语句补充 WHERE 条件，并先用 SELECT 验证影响行范围后再执行。";
                        case "DELETE_REQUIRE_WHERE" -> "为 `DELETE` 语句补充 WHERE 条件，并先用 SELECT 验证影响行范围后再执行。";
                        case "REQUIRE_WHERE" -> "为 `" + type.toUpperCase(Locale.ROOT) + "` 语句补充 WHERE 条件，并先用 SELECT 验证影响行范围后再执行。";
                        case "DELETE_TRUNCATE" -> "若确实要清空整表，改为 `TRUNCATE TABLE 表名`；若只删部分数据，请补充 WHERE 条件。";
                        case "JOIN_TABLE_COUNT" -> "拆分过长联表查询（>8 表）为中间结果/临时表/CTE 分步处理，并优先保留核心过滤条件。";
                        case "IMPLICIT_JOIN" -> "将逗号连接改为显式 `JOIN ... ON ...`，例如 `FROM a JOIN b ON a.id = b.a_id`。";
                        case "SUBQUERY_IN_TARGET" -> "将 SELECT 列中的子查询改写为 JOIN 或预聚合子查询（CTE/派生表），便于优化器下推。";
                        case "SUBQUERY_DEPTH" -> "降低子查询嵌套层级（建议 <= 2），可用 CTE（`WITH`）分层表达业务逻辑。";
                        case "SCHEMA_PREFIX" -> "为表/函数引用补充 schema 前缀（如 `public.user_info`），减少对象解析歧义和额外开销。";
                        case "SQL_INJECTION_RISK" -> "将 MyBatis `${}` 字符串拼接改为 `#{}` 参数绑定；若必须拼接标识符，请做白名单校验。";
                        case "KEYWORD_UPPERCASE" -> "统一 SQL 关键字大小写风格（推荐大写，如 `SELECT`, `FROM`, `WHERE`），并在格式化工具中固化规则。";
                        default -> buildGenericSuggestion(rule, fragment);
                };
        }

        private String buildExampleSql(AuditRule rule, SqlFragment fragment, CheckResult result) {
                String checkerName = rule.getCheckerName();
                if (checkerName == null || checkerName.isBlank()) {
                        return buildGenericExampleSql(fragment, result);
                }

                String sql = fragment.getSqlText();
                if (sql == null || sql.isBlank()) {
                        return null;
                }

                return switch (checkerName) {
                        case "NO_SELECT_STAR" -> rewriteSelectStar(sql);
                        case "NULL_COMPARISON" -> rewriteNullComparison(sql);
                        case "LIKE_PERCENT_START" -> rewriteLikeLeadingPercent(sql);
                        case "UNION_ALL" -> rewriteUnionAll(sql);
                        case "REQUIRE_LIMIT" -> rewriteRequireLimit(sql);
                        case "UPDATE_LIMIT" -> rewriteUpdateLimit(sql);
                        case "UPDATE_REQUIRE_WHERE" -> rewriteRequireWhere(sql, "update");
                        case "DELETE_REQUIRE_WHERE" -> rewriteRequireWhere(sql, "delete");
                        case "REQUIRE_WHERE" -> rewriteRequireWhere(sql, fragment.getStatementType());
                        case "DELETE_TRUNCATE" -> rewriteDeleteTruncate(sql);
                        case "SQL_INJECTION_RISK" -> rewriteMyBatisDollarPlaceholder(sql);
                        case "SCHEMA_PREFIX" -> rewriteSchemaPrefix(sql);
                        case "KEYWORD_UPPERCASE" -> rewriteKeywordUppercase(sql);
                        case "LOCK_TABLE" -> rewriteLockTable(sql);
                        case "IMPLICIT_JOIN" -> rewriteImplicitJoin(sql, result == null ? null : result.matchedText());
                        case "NOT_EQUAL_OPS" -> rewriteNotEqualOps(sql, result == null ? null : result.matchedText());
                        case "IN_LIST_SIZE" -> rewriteInListSize(sql);
                        case "COUNT_USAGE" -> rewriteCountUsage(sql);
                        case "WHERE_FUNCTION" -> rewriteWhereFunction(sql, result == null ? null : result.matchedText());
                        case "SUBQUERY_IN_TARGET" -> rewriteSubqueryTarget(sql);
                        case "SUBQUERY_DEPTH" -> rewriteSubqueryDepth(sql);
                        case "JOIN_TABLE_COUNT" -> rewriteJoinTableCount(sql);
                        default -> buildGenericExampleSql(fragment, result);
                };
        }

        private String buildRegexExampleSql(AuditRule rule, SqlFragment fragment, String matchedText) {
                String sql = fragment.getSqlText();
                if (sql == null || sql.isBlank()) {
                        return null;
                }

                String desc = Optional.ofNullable(rule.getDescription()).orElse("").toLowerCase(Locale.ROOT);
                if (desc.contains("${") || desc.contains("注入") || desc.contains("injection")) {
                        return rewriteMyBatisDollarPlaceholder(sql);
                }

                return "-- 示例改写（请结合自定义规则人工确认）\n"
                                + "-- 当前命中模式: " + sanitizeInlineComment(matchedText) + "\n"
                                + ensureSemicolon(sql);
        }

        private String buildRegexSuggestion(AuditRule rule, String matchedText) {
                if (rule.getDescription() != null && !rule.getDescription().isBlank()) {
                        return "请根据自定义规则描述修复该处 SQL：" + rule.getDescription()
                                        + (matchedText != null ? "（当前匹配到: `" + matchedText + "`）" : "");
                }
                return "请修改或删除匹配到的 SQL 模式，确保不再命中自定义正则规则。";
        }

        private String buildGenericSuggestion(AuditRule rule, SqlFragment fragment) {
                String category = rule.getCategory() == null ? "SQL 规范" : rule.getCategory();
                String type = fragment.getStatementType() == null ? "SQL" : fragment.getStatementType().toUpperCase(Locale.ROOT);
                if (rule.getDescription() != null && !rule.getDescription().isBlank()) {
                        return "按 " + category + " 规范修正该 " + type + "："
                                        + rule.getDescription()
                                        + "。建议先在测试环境验证执行计划和影响行数。";
                }
                return "请根据规则要求修正该 " + type + "，并在测试环境验证语义和性能无回归。";
        }

        private String rewriteSelectStar(String sql) {
                Matcher matcher = SELECT_STAR_REWRITE.matcher(sql);
                if (!matcher.find()) {
                        return ensureSemicolon(sql);
                }
                String distinct = matcher.group(1) == null ? "" : matcher.group(1).toUpperCase(Locale.ROOT);
                String replacement = "SELECT " + distinct + "/* TODO: 显式列名 */ col1, col2";
                return ensureSemicolon(matcher.replaceFirst(Matcher.quoteReplacement(replacement)));
        }

        private String rewriteNullComparison(String sql) {
                String rewritten = NULL_NE_REWRITE.matcher(sql).replaceAll("IS NOT NULL");
                rewritten = NULL_EQ_REWRITE.matcher(rewritten).replaceAll("IS NULL");
                return ensureSemicolon(rewritten);
        }

        private String rewriteLikeLeadingPercent(String sql) {
                Matcher matcher = LIKE_PREFIX_LITERAL.matcher(sql);
                StringBuffer sb = new StringBuffer();
                boolean changed = false;
                while (matcher.find()) {
                        changed = true;
                        String quote = matcher.group(1);
                        String tail = matcher.group(2);
                        String replacement = "LIKE " + quote + tail + "%" + quote
                                        + " /* TODO: 示例改写，需确认语义 */";
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                }
                if (!changed) {
                        return "-- 示例：将前置 % 的 LIKE 调整为可走索引的匹配（需确认业务语义）\n" + ensureSemicolon(sql);
                }
                matcher.appendTail(sb);
                return ensureSemicolon(sb.toString());
        }

        private String rewriteUnionAll(String sql) {
                return ensureSemicolon(UNION_WITHOUT_ALL.matcher(sql).replaceAll("UNION ALL"));
        }

        private String rewriteRequireLimit(String sql) {
                String base = stripTrailingSemicolon(sql);
                String upper = base.toUpperCase(Locale.ROOT);
                if (upper.contains(" LIMIT ")) {
                        return ensureSemicolon(base);
                }
                if (upper.contains(" ORDER BY ")) {
                        return ensureSemicolon(base + "\nLIMIT 100");
                }
                return ensureSemicolon(base + "\nORDER BY /* TODO: 稳定排序列 */ id\nLIMIT 100");
        }

        private String rewriteUpdateLimit(String sql) {
                String base = stripTrailingSemicolon(sql);
                String noLimit = UPDATE_LIMIT_CLAUSE.matcher(base).replaceFirst("");
                if (!noLimit.equals(base)) {
                        if (!noLimit.toUpperCase(Locale.ROOT).contains(" WHERE ")) {
                                noLimit = noLimit + "\nWHERE /* TODO: 用明确条件替代 LIMIT */";
                        }
                        return ensureSemicolon(noLimit);
                }
                return "-- OpenGauss 不支持 UPDATE ... LIMIT，示例请改为明确 WHERE 条件\n"
                                + ensureSemicolon(base + "\nWHERE /* TODO: 主键/业务条件 */");
        }

        private String rewriteRequireWhere(String sql, String statementType) {
                String base = stripTrailingSemicolon(sql);
                if (base.toUpperCase(Locale.ROOT).contains(" WHERE ")) {
                        return ensureSemicolon(base);
                }
                String label = "delete".equalsIgnoreCase(statementType) ? "删除条件" : "更新条件";
                return ensureSemicolon(base + "\nWHERE /* TODO: " + label + " */");
        }

        private String rewriteDeleteTruncate(String sql) {
                Matcher matcher = SIMPLE_DELETE_TABLE.matcher(sql);
                if (matcher.matches()) {
                        return "TRUNCATE TABLE " + matcher.group(1) + ";";
                }
                return "-- 若目标是清空整表，可改用 TRUNCATE TABLE；否则请补充 WHERE\n" + ensureSemicolon(sql);
        }

        private String rewriteMyBatisDollarPlaceholder(String sql) {
                String rewritten = DOLLAR_PARAM.matcher(sql).replaceAll("#{$1}");
                if (rewritten.equals(sql)) {
                        return "-- 示例：将 `${param}` 改为 `#{param}` 参数绑定\n" + ensureSemicolon(sql);
                }
                return ensureSemicolon(rewritten);
        }

        private String rewriteSchemaPrefix(String sql) {
                Matcher matcher = TABLE_REF_NO_SCHEMA.matcher(sql);
                StringBuffer sb = new StringBuffer();
                boolean changed = false;
                while (matcher.find()) {
                        changed = true;
                        String replacement = matcher.group(1) + " public." + matcher.group(2);
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                }
                matcher.appendTail(sb);
                if (!changed) {
                        return "-- 示例：在表名/函数名前补充 schema（如 public.xxx）\n" + ensureSemicolon(sql);
                }
                return ensureSemicolon(sb.toString());
        }

        private String rewriteKeywordUppercase(String sql) {
                Matcher matcher = WORD_PATTERN.matcher(sql);
                StringBuffer sb = new StringBuffer();
                boolean changed = false;
                while (matcher.find()) {
                        String word = matcher.group(1);
                        String lower = word.toLowerCase(Locale.ROOT);
                        if (SQL_KEYWORDS.contains(lower) && !word.equals(word.toUpperCase(Locale.ROOT))) {
                                changed = true;
                                matcher.appendReplacement(sb, Matcher.quoteReplacement(word.toUpperCase(Locale.ROOT)));
                        } else {
                                matcher.appendReplacement(sb, Matcher.quoteReplacement(word));
                        }
                }
                matcher.appendTail(sb);
                return ensureSemicolon(changed ? sb.toString() : sql);
        }

        private String rewriteLockTable(String sql) {
                Matcher matcher = LOCK_TABLE_PATTERN.matcher(sql);
                if (matcher.find()) {
                        String table = matcher.group(1);
                        return "BEGIN;\nSELECT *\nFROM " + table
                                        + "\nWHERE /* TODO: 锁定目标行 */\nFOR UPDATE;\n-- ...执行业务 SQL...\nCOMMIT;";
                }
                return "-- 示例：使用事务 + SELECT ... FOR UPDATE 代替 LOCK TABLE\n"
                                + "BEGIN;\nSELECT * FROM your_table WHERE id = ? FOR UPDATE;\nCOMMIT;";
        }

        private String rewriteImplicitJoin(String sql, String matchedText) {
                List<String> tables = extractTablesFromImplicitJoin(matchedText);
                if (tables.size() >= 2) {
                        return "SELECT /* TODO: columns */\nFROM " + tables.get(0) + " t1\nJOIN " + tables.get(1)
                                        + " t2 ON t1.id = t2." + guessFkColumn(tables.get(0))
                                        + "\nWHERE /* TODO: conditions */;";
                }
                return "-- 示例：将隐式 JOIN 改为显式 JOIN ... ON ...\n" + ensureSemicolon(sql);
        }

        private String rewriteNotEqualOps(String sql, String matched) {
                String prefix;
                if (matched == null) {
                        prefix = "-- 示例：改写为正向条件（如 IN / 范围 / EXISTS），以提升索引利用率";
                } else if ("not in".equalsIgnoreCase(matched)) {
                        prefix = "-- 示例：将 NOT IN 评估为 NOT EXISTS 或 LEFT JOIN ... IS NULL（按执行计划选择）";
                } else {
                        prefix = "-- 示例：将 `!=`/`<>` 改写为更易利用索引的正向条件（需按业务确认）";
                }
                return prefix + "\n" + ensureSemicolon(sql);
        }

        private String rewriteInListSize(String sql) {
                return "-- 示例方案：\n"
                                + "-- 1) 分批执行，每批 IN 列表 <= 100\n"
                                + "-- 2) 将参数写入临时表后 JOIN\n"
                                + "-- 3) 按驱动支持情况改为 = ANY(?)\n"
                                + ensureSemicolon(sql);
        }

        private String rewriteCountUsage(String sql) {
                return "-- 若仅判断是否存在，示例可改为：\n"
                                + "SELECT 1\nFROM /* TODO: table */\nWHERE /* TODO: conditions */\nLIMIT 1;\n"
                                + "\n-- 若必须精确计数，请保留 count() 并优化过滤条件/索引\n"
                                + ensureSemicolon(sql);
        }

        private String rewriteWhereFunction(String sql, String matchedText) {
                return "-- 示例：避免在 WHERE 左侧列上使用函数（需按业务改写）\n"
                                + "-- 原命中片段: " + sanitizeInlineComment(matchedText) + "\n"
                                + ensureSemicolon(sql)
                                + "\n-- 例如：DATE(create_time) = ?  改为  create_time >= ? AND create_time < ?";
        }

        private String rewriteSubqueryTarget(String sql) {
                return "-- 示例：将 SELECT 列子查询改成预聚合 + JOIN\n"
                                + "WITH agg AS (\n"
                                + "  SELECT key_col, /* TODO: 聚合列 */\n"
                                + "  FROM child_table\n"
                                + "  GROUP BY key_col\n"
                                + ")\n"
                                + "SELECT m./* TODO */, a./* TODO */\n"
                                + "FROM main_table m\n"
                                + "LEFT JOIN agg a ON a.key_col = m.id;";
        }

        private String rewriteSubqueryDepth(String sql) {
                return "-- 示例：用 CTE 拆分多层子查询，降低嵌套深度\n"
                                + "WITH step1 AS (\n  /* TODO */\n),\n"
                                + "step2 AS (\n  SELECT * FROM step1 /* TODO */\n)\n"
                                + "SELECT * FROM step2;\n\n-- 原 SQL\n"
                                + ensureSemicolon(sql);
        }

        private String rewriteJoinTableCount(String sql) {
                return "-- 示例：将超长联表拆成分步查询（CTE/临时表）\n"
                                + "WITH base AS (\n"
                                + "  SELECT /* TODO: 核心字段 */\n"
                                + "  FROM /* TODO: 核心表 */\n"
                                + "  WHERE /* TODO: 先过滤 */\n"
                                + ")\n"
                                + "SELECT /* TODO */\nFROM base\nJOIN /* 其他表 */ ON /* TODO */;\n\n-- 原 SQL\n"
                                + ensureSemicolon(sql);
        }

        private String buildGenericExampleSql(SqlFragment fragment, CheckResult result) {
                String sql = fragment.getSqlText();
                if (sql == null || sql.isBlank()) {
                        return null;
                }
                return "-- 示例改写（请结合业务语义人工确认）\n"
                                + "-- 当前命中: " + sanitizeInlineComment(result == null ? null : result.matchedText()) + "\n"
                                + ensureSemicolon(sql);
        }

        private String sanitizeInlineComment(String text) {
                if (text == null || text.isBlank()) {
                        return "N/A";
                }
                return text.replace('\n', ' ').replace('\r', ' ').trim();
        }

        private String stripTrailingSemicolon(String sql) {
                return TRAILING_SEMICOLON.matcher(sql.trim()).replaceFirst("");
        }

        private String ensureSemicolon(String sql) {
                if (sql == null || sql.isBlank()) {
                        return sql;
                }
                String trimmed = sql.trim();
                return trimmed.endsWith(";") ? trimmed : trimmed + ";";
        }

        private List<String> extractTablesFromImplicitJoin(String matchedText) {
                if (matchedText == null || matchedText.isBlank()) {
                        return List.of();
                }
                String lower = matchedText.toLowerCase(Locale.ROOT);
                int fromIndex = lower.indexOf("from");
                if (fromIndex < 0) {
                        return List.of();
                }
                String fromClause = matchedText.substring(fromIndex + 4).trim();
                String[] parts = fromClause.split(",");
                List<String> tables = new ArrayList<>();
                for (String part : parts) {
                        String table = part.trim();
                        if (table.isBlank()) {
                                continue;
                        }
                        String firstToken = table.split("\\s+")[0];
                        if (!firstToken.isBlank()) {
                                tables.add(firstToken);
                        }
                }
                return tables;
        }

        private String guessFkColumn(String parentTable) {
                String base = parentTable;
                int dot = base.lastIndexOf('.');
                if (dot >= 0 && dot + 1 < base.length()) {
                        base = base.substring(dot + 1);
                }
                return base + "_id";
        }

        private boolean ruleAppliesToFragment(AuditRule rule, SqlFragment fragment) {
                AppliesTo appliesTo = rule.getAppliesTo() == null ? AppliesTo.ALL : rule.getAppliesTo();
                boolean sqlScript = fragment != null && "sql-script".equalsIgnoreCase(fragment.getNamespace());
                return switch (appliesTo) {
                        case ALL -> true;
                        case SQL_SCRIPT_ONLY -> sqlScript;
                        case MYBATIS_ONLY -> !sqlScript;
                };
        }

        private boolean ruleAppliesToSqlScript(AuditRule rule) {
                AppliesTo appliesTo = rule.getAppliesTo() == null ? AppliesTo.ALL : rule.getAppliesTo();
                return appliesTo == AppliesTo.ALL || appliesTo == AppliesTo.SQL_SCRIPT_ONLY;
        }

        private AuditRule defaultBuiltInRule(String id, String section, String category, String name,
                        String description, Severity severity, String checkerName) {
                return defaultBuiltInRule(id, section, category, name, description, severity, checkerName, AppliesTo.ALL);
        }

        private AuditRule defaultBuiltInRule(String id, String section, String category, String name,
                        String description, Severity severity, String checkerName, AppliesTo appliesTo) {
                return AuditRule.builder()
                                .id(id)
                                .section(section)
                                .category(category)
                                .name(name)
                                .description(description)
                                .severity(severity)
                                .type(RuleType.BUILT_IN)
                                .checkerName(checkerName)
                                .source(RuleSource.DEFAULT)
                                .appliesTo(appliesTo)
                                .build();
        }

        /**
         * 构建默认内置规则
         */
        private List<AuditRule> buildDefaultRules() {
                List<AuditRule> rules = new ArrayList<>();

                // 共享默认规则（21）
                rules.add(defaultBuiltInRule("OG_3_2_2", "3.2.2", "对象访问", "建议使用Schema前缀",
                                "访问对象（表，函数等）时建议带上SCHEMA名称，避免不必要的性能开销",
                                Severity.WARNING, "SCHEMA_PREFIX"));
                rules.add(defaultBuiltInRule("OG_3_3_1", "3.3.1", "WHERE子句", "禁止用 = 或 != 判断 NULL",
                                "查询条件中判断 NULL 时，禁止使用 = 和 !=，应使用 IS NULL 或 IS NOT NULL",
                                Severity.ERROR, "NULL_COMPARISON"));
                rules.add(defaultBuiltInRule("OG_3_3_3", "3.3.3", "WHERE子句", "WHERE 条件字段禁用函数",
                                "不建议 WHERE 条件字段使用表达式或函数，会导致索引失效",
                                Severity.WARNING, "WHERE_FUNCTION"));
                rules.add(defaultBuiltInRule("OG_3_3_4", "3.3.4", "WHERE子句", "少用负向操作符",
                                "查询条件中尽量少使用 !=, <>, NOT IN 等无法利用索引的操作符",
                                Severity.WARNING, "NOT_EQUAL_OPS"));
                rules.add(defaultBuiltInRule("OG_3_3_5", "3.3.5", "WHERE子句", "LIKE 禁止前缀 %",
                                "LIKE 语句 % 不应放在首字符位置，会导致全表扫描",
                                Severity.WARNING, "LIKE_PERCENT_START"));
                rules.add(defaultBuiltInRule("OG_3_3_6", "3.3.6", "WHERE子句", "IN 子集不宜过大",
                                "WHERE 条件中 IN 的候选子集不宜超过100个",
                                Severity.WARNING, "IN_LIST_SIZE"));
                rules.add(defaultBuiltInRule("OG_3_4_1", "3.4.1", "SELECT", "禁止 SELECT *",
                                "SELECT 语句中禁用通配符字段 *，请明确指定查询列",
                                Severity.ERROR, "NO_SELECT_STAR"));
                rules.add(defaultBuiltInRule("OG_3_4_3", "3.4.3", "SELECT", "禁止 LOCK TABLE",
                                "禁止使用 LOCK TABLE 语句加锁，仅允许使用 SELECT .. FOR UPDATE",
                                Severity.ERROR, "LOCK_TABLE"));
                rules.add(defaultBuiltInRule("OG_3_4_4", "3.4.4", "SELECT", "优先使用 UNION ALL",
                                "考虑使用 UNION ALL 代替 UNION，避免不必要的去重排序开销",
                                Severity.WARNING, "UNION_ALL"));
                rules.add(defaultBuiltInRule("OG_3_4_5", "3.4.5", "SELECT", "慎用 count()",
                                "避免频繁使用 count() 获取大表行数，资源消耗较大",
                                Severity.INFO, "COUNT_USAGE"));
                rules.add(defaultBuiltInRule("OG_3_4_LIMIT", "3.4.6", "SELECT", "SELECT 建议分页",
                                "SELECT 语句建议使用 LIMIT 分页，避免全表查询",
                                Severity.WARNING, "REQUIRE_LIMIT"));
                rules.add(defaultBuiltInRule("OG_3_6_1", "3.6.1", "UPDATE", "UPDATE 禁用 LIMIT",
                                "OpenGauss 不支持 UPDATE 语句中使用 LIMIT",
                                Severity.ERROR, "UPDATE_LIMIT"));
                rules.add(defaultBuiltInRule("OG_3_6_3", "3.6.3", "UPDATE", "UPDATE 必须有 WHERE",
                                "UPDATE 语句中必须有 WHERE 子句，避免全表更新",
                                Severity.ERROR, "UPDATE_REQUIRE_WHERE"));
                rules.add(defaultBuiltInRule("OG_3_7_2", "3.7.2", "DELETE", "全表删除用 TRUNCATE",
                                "清空一张表建议使用 TRUNCATE，而不是 DELETE",
                                Severity.ERROR, "DELETE_TRUNCATE"));
                rules.add(defaultBuiltInRule("OG_3_7_3", "3.7.3", "DELETE", "DELETE 必须有 WHERE",
                                "DELETE 语句中必须有 WHERE 子句，避免全表删除",
                                Severity.ERROR, "DELETE_REQUIRE_WHERE"));
                rules.add(defaultBuiltInRule("OG_3_8_1", "3.8.1", "关联查询", "限制关联表数量",
                                "禁止超过8表关联，建议不超过5个",
                                Severity.ERROR, "JOIN_TABLE_COUNT"));
                rules.add(defaultBuiltInRule("OG_3_8_3", "3.8.3", "关联查询", "禁止隐式 JOIN",
                                "避免使用逗号分隔的隐式 JOIN，应使用显式 INNER/LEFT/RIGHT JOIN",
                                Severity.ERROR, "IMPLICIT_JOIN"));
                rules.add(defaultBuiltInRule("OG_3_9_3", "3.9.3", "子查询", "目标列禁用子查询",
                                "避免在 SELECT 目标列中使用子查询，可能导致计划无法下推影响执行性能",
                                Severity.WARNING, "SUBQUERY_IN_TARGET"));
                rules.add(defaultBuiltInRule("OG_3_9_4", "3.9.4", "子查询", "子查询嵌套不超过2层",
                                "子查询嵌套深度不建议超过2层",
                                Severity.WARNING, "SUBQUERY_DEPTH"));
                rules.add(defaultBuiltInRule("OG_MYBATIS_INJECTION", "MyBatis", "安全", "SQL 注入风险",
                                "MyBatis 中使用 ${} 存在 SQL 注入风险，应使用 #{} 参数绑定",
                                Severity.ERROR, "SQL_INJECTION_RISK"));
                rules.add(defaultBuiltInRule("OG_STYLE_1", "Style", "代码风格", "SQL 关键字统一大写",
                                "SQL 关键字建议统一使用大写风格",
                                Severity.WARNING, "KEYWORD_UPPERCASE"));

                // SQL 脚本专用默认规则（20）
                rules.add(defaultBuiltInRule("OG_2_2_3", "2.2.3", "对象命名", "避免使用双引号对象名",
                                "避免使用双引号括起来的字符串来定义数据库对象名称，除非必须区分大小写",
                                Severity.WARNING, "QUOTED_OBJECT_NAME", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_2_4A", "2.2.4", "对象命名", "对象名字符集限制",
                                "对象名限定使用小写字母、下划线、数字三类字符",
                                Severity.ERROR, "OBJECT_NAME_CHARS", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_2_4B", "2.2.4", "对象命名", "对象名禁止特殊前缀",
                                "对象名禁止以 pg、gs、mlog、redis、数字、下划线开头",
                                Severity.ERROR, "OBJECT_NAME_PREFIX", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_2_5", "2.2.5", "对象命名", "别名字符限制",
                                "SQL 语句中的别名只使用小写字母、下划线、数字三类字符",
                                Severity.WARNING, "ALIAS_NAME_CHARS", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_3_4", "2.3.4", "数据库设计", "数据库字符集必须为 UTF8",
                                "创建数据库时必须指定字符集为 UTF8，并与客户端统一编码",
                                Severity.ERROR, "DATABASE_UTF8", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_3_5", "2.3.5", "数据库设计", "数据库 Locale 必须显式指定",
                                "创建数据库时必须指定 LC_CTYPE='en_US.UTF8' 和 LC_COLLATE='C'",
                                Severity.ERROR, "DATABASE_LOCALE", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_6_4", "2.6.4", "表设计", "所有表必须有主键",
                                "所有表必须定义主键",
                                Severity.ERROR, "TABLE_PRIMARY_KEY", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_6_5", "2.6.5", "表设计", "禁止使用外键",
                                "禁止使用外键，建议在应用层维护关联关系",
                                Severity.ERROR, "FOREIGN_KEY_FORBIDDEN", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_6_7", "2.6.7", "表设计", "表存储必须使用 ustore",
                                "表存储必须使用 ustore，禁止使用 astore",
                                Severity.ERROR, "USTORE_REQUIRED", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_6_8", "2.6.8", "表设计", "禁止使用列存表",
                                "列存表不满足当前商用约束，禁止使用",
                                Severity.ERROR, "COLUMN_STORE_FORBIDDEN", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_6_10", "2.6.10", "表设计", "视图定义尽量避免排序",
                                "视图定义中尽量避免 ORDER BY 排序操作",
                                Severity.WARNING, "VIEW_ORDER_BY", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_7_1", "2.7.1", "字段设计", "禁止使用 money 类型",
                                "禁止使用 money 类型，建议使用 NUMERIC(precision, scale)",
                                Severity.ERROR, "MONEY_TYPE_FORBIDDEN", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_7_12", "2.7.12", "字段设计", "字段定义建议补充注释",
                                "字段定义时建议同时创建 COMMENT 注释信息",
                                Severity.WARNING, "COLUMN_COMMENT_REQUIRED", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_8_3", "2.8.3", "索引设计", "组合索引字段数不超过5个",
                                "组合索引字段个数不超过5个",
                                Severity.ERROR, "INDEX_COLUMN_COUNT", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_9_1", "2.9.1", "其他对象", "禁止使用触发器",
                                "禁止使用触发器",
                                Severity.ERROR, "TRIGGER_FORBIDDEN", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_9_2", "2.9.2", "其他对象", "禁止使用存储过程",
                                "禁止使用存储过程",
                                Severity.ERROR, "PROCEDURE_FORBIDDEN", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_9_3", "2.9.3", "其他对象", "禁止使用函数",
                                "禁止使用函数，特殊情况使用需要经过审批",
                                Severity.ERROR, "FUNCTION_FORBIDDEN", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_9_4A", "2.9.4", "其他对象", "禁止使用外部表",
                                "禁止使用外部表",
                                Severity.ERROR, "EXTERNAL_TABLE_FORBIDDEN", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_9_4B", "2.9.4", "其他对象", "禁止使用 dblink",
                                "禁止使用 dblink",
                                Severity.ERROR, "DBLINK_FORBIDDEN", AppliesTo.SQL_SCRIPT_ONLY));
                rules.add(defaultBuiltInRule("OG_2_9_4C", "2.9.4", "其他对象", "禁止使用大对象",
                                "禁止使用大对象相关语法",
                                Severity.ERROR, "LARGE_OBJECT_FORBIDDEN", AppliesTo.SQL_SCRIPT_ONLY));

                return rules;
        }
}
