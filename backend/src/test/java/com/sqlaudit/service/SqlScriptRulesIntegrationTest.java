package com.sqlaudit.service;

import com.sqlaudit.model.ScanReport;
import com.sqlaudit.model.SqlFragment;
import com.sqlaudit.model.Violation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SqlScriptRulesIntegrationTest {

    @Autowired
    private RuleService ruleService;

    @Autowired
    private ScanService scanService;

    @ParameterizedTest
    @MethodSource("statementRuleCases")
    void shouldMatchStatementLevelSqlScriptRules(String checkerName, String badSql, String goodSql) {
        SqlFragment badFragment = fragment(badSql);
        SqlFragment goodFragment = fragment(goodSql);

        assertTrue(ruleService.checkSql(badFragment).stream()
                .anyMatch(v -> checkerName.equals(v.getRule().getCheckerName())));
        assertFalse(ruleService.checkSql(goodFragment).stream()
                .anyMatch(v -> checkerName.equals(v.getRule().getCheckerName())));
    }

    @Test
    void shouldEvaluateCrossStatementRulesForSqlScripts() {
        String badSql = """
                CREATE TABLE demo_user (
                  id bigint,
                  name varchar(64)
                ) WITH (storage_type=ustore);
                COMMENT ON COLUMN demo_user.id IS 'id';
                """;

        ScanReport report = scanService.scanSqlContent(badSql, "demo.sql");

        assertTrue(hasChecker(report.getViolations(), "TABLE_PRIMARY_KEY"));
        assertTrue(hasChecker(report.getViolations(), "COLUMN_COMMENT_REQUIRED"));
    }

    @Test
    void shouldAllowCrossStatementRulesWhenScriptProvidesPkAndComments() {
        String goodSql = """
                CREATE TABLE demo_user (
                  id bigint PRIMARY KEY,
                  name varchar(64)
                ) WITH (storage_type=ustore);
                COMMENT ON COLUMN demo_user.id IS 'id';
                COMMENT ON COLUMN demo_user.name IS 'name';
                """;

        ScanReport report = scanService.scanSqlContent(goodSql, "demo.sql");

        assertFalse(hasChecker(report.getViolations(), "TABLE_PRIMARY_KEY"));
        assertFalse(hasChecker(report.getViolations(), "COLUMN_COMMENT_REQUIRED"));
    }

    private static boolean hasChecker(List<Violation> violations, String checkerName) {
        return violations.stream().anyMatch(v -> checkerName.equals(v.getRule().getCheckerName()));
    }

    private static SqlFragment fragment(String sql) {
        return SqlFragment.builder()
                .statementId("stmt")
                .statementType("unknown")
                .namespace("sql-script")
                .sqlText(sql)
                .build();
    }

    private static Stream<Arguments> statementRuleCases() {
        return Stream.of(
                Arguments.of("QUOTED_OBJECT_NAME",
                        "CREATE TABLE \"BadName\" (id bigint)",
                        "CREATE TABLE good_name (id bigint)"),
                Arguments.of("OBJECT_NAME_CHARS",
                        "CREATE TABLE BadName (id bigint)",
                        "CREATE TABLE good_name (id bigint)"),
                Arguments.of("OBJECT_NAME_PREFIX",
                        "CREATE TABLE pg_user (id bigint)",
                        "CREATE TABLE good_name (id bigint)"),
                Arguments.of("ALIAS_NAME_CHARS",
                        "SELECT u.id AS UserAlias FROM user_info u",
                        "SELECT u.id AS user_alias FROM user_info u"),
                Arguments.of("DATABASE_UTF8",
                        "CREATE DATABASE demo_db",
                        "CREATE DATABASE demo_db ENCODING='UTF8'"),
                Arguments.of("DATABASE_LOCALE",
                        "CREATE DATABASE demo_db ENCODING='UTF8'",
                        "CREATE DATABASE demo_db ENCODING='UTF8' LC_CTYPE='en_US.UTF8' LC_COLLATE='C'"),
                Arguments.of("FOREIGN_KEY_FORBIDDEN",
                        "CREATE TABLE child_user(parent_id bigint REFERENCES parent_user(id))",
                        "CREATE TABLE child_user(parent_id bigint)"),
                Arguments.of("USTORE_REQUIRED",
                        "CREATE TABLE demo_user(id bigint PRIMARY KEY)",
                        "CREATE TABLE demo_user(id bigint PRIMARY KEY) WITH (storage_type=ustore)"),
                Arguments.of("COLUMN_STORE_FORBIDDEN",
                        "CREATE TABLE demo_user(id bigint) WITH (ORIENTATION = COLUMN)",
                        "CREATE TABLE demo_user(id bigint) WITH (storage_type=ustore)"),
                Arguments.of("VIEW_ORDER_BY",
                        "CREATE VIEW demo_view AS SELECT * FROM demo_user ORDER BY id",
                        "CREATE VIEW demo_view AS SELECT * FROM demo_user"),
                Arguments.of("MONEY_TYPE_FORBIDDEN",
                        "CREATE TABLE demo_user(balance money)",
                        "CREATE TABLE demo_user(balance numeric(18,2))"),
                Arguments.of("INDEX_COLUMN_COUNT",
                        "CREATE INDEX idx_demo ON demo_user(a,b,c,d,e,f)",
                        "CREATE INDEX idx_demo ON demo_user(a,b,c)"),
                Arguments.of("TRIGGER_FORBIDDEN",
                        "CREATE TRIGGER trg BEFORE INSERT ON demo_user FOR EACH ROW EXECUTE PROCEDURE demo_proc()",
                        "CREATE TABLE demo_user(id bigint)"),
                Arguments.of("PROCEDURE_FORBIDDEN",
                        "CREATE PROCEDURE demo_proc() LANGUAGE plpgsql AS $$ BEGIN NULL; END; $$",
                        "CREATE TABLE demo_user(id bigint)"),
                Arguments.of("FUNCTION_FORBIDDEN",
                        "CREATE FUNCTION demo_fn() RETURNS integer LANGUAGE plpgsql AS $$ BEGIN RETURN 1; END; $$",
                        "CREATE TABLE demo_user(id bigint)"),
                Arguments.of("EXTERNAL_TABLE_FORBIDDEN",
                        "CREATE FOREIGN TABLE ext_user(id bigint)",
                        "CREATE TABLE demo_user(id bigint)"),
                Arguments.of("DBLINK_FORBIDDEN",
                        "SELECT * FROM dblink('conn', 'select 1') AS t(x int)",
                        "SELECT 1"),
                Arguments.of("LARGE_OBJECT_FORBIDDEN",
                        "SELECT lo_create(1)",
                        "SELECT 1")
        );
    }
}
