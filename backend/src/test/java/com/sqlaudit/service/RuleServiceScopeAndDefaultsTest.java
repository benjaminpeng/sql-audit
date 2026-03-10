package com.sqlaudit.service;

import com.sqlaudit.model.AuditRule;
import com.sqlaudit.model.SqlFragment;
import com.sqlaudit.model.Violation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RuleServiceScopeAndDefaultsTest {

    @Autowired
    private RuleService ruleService;

    @Test
    void shouldExposeExpectedDefaultRuleCountsAndScopes() {
        List<AuditRule> defaultRules = ruleService.getDefaultRules();

        assertEquals(41, defaultRules.size());
        assertEquals(21, defaultRules.stream().filter(rule -> rule.getAppliesTo() == AuditRule.AppliesTo.ALL).count());
        assertEquals(20, defaultRules.stream().filter(rule -> rule.getAppliesTo() == AuditRule.AppliesTo.SQL_SCRIPT_ONLY).count());
        assertTrue(defaultRules.stream().anyMatch(rule -> "KEYWORD_UPPERCASE".equals(rule.getCheckerName())));
    }

    @Test
    void shouldNotApplySqlScriptOnlyRulesToMyBatisFragments() {
        SqlFragment mybatisFragment = SqlFragment.builder()
                .statementId("createTrigger")
                .statementType("create")
                .namespace("com.demo.Mapper")
                .sqlText("CREATE TRIGGER trg BEFORE INSERT ON demo_user FOR EACH ROW EXECUTE PROCEDURE demo_proc();")
                .build();

        List<Violation> violations = ruleService.checkSql(mybatisFragment);

        assertFalse(violations.stream().anyMatch(v -> "TRIGGER_FORBIDDEN".equals(v.getRule().getCheckerName())));
    }

    @Test
    void shouldSplitUpdateAndDeleteRequireWhereRules() {
        SqlFragment updateFragment = SqlFragment.builder()
                .statementId("u1")
                .statementType("update")
                .namespace("sql-script")
                .sqlText("UPDATE demo_user SET name = 'alice'")
                .build();
        SqlFragment deleteFragment = SqlFragment.builder()
                .statementId("d1")
                .statementType("delete")
                .namespace("sql-script")
                .sqlText("DELETE FROM demo_user")
                .build();

        List<Violation> updateViolations = ruleService.checkSql(updateFragment);
        List<Violation> deleteViolations = ruleService.checkSql(deleteFragment);

        assertTrue(updateViolations.stream().anyMatch(v -> "UPDATE_REQUIRE_WHERE".equals(v.getRule().getCheckerName())));
        assertFalse(updateViolations.stream().anyMatch(v -> "DELETE_REQUIRE_WHERE".equals(v.getRule().getCheckerName())));
        assertTrue(deleteViolations.stream().anyMatch(v -> "DELETE_REQUIRE_WHERE".equals(v.getRule().getCheckerName())));
        assertFalse(deleteViolations.stream().anyMatch(v -> "UPDATE_REQUIRE_WHERE".equals(v.getRule().getCheckerName())));
    }

    @Test
    void shouldDetectKeywordUppercaseOnlyWhenNeeded() {
        SqlFragment badFragment = SqlFragment.builder()
                .statementId("s1")
                .statementType("select")
                .namespace("sql-script")
                .sqlText("select id from demo_user where id = 1")
                .build();
        SqlFragment goodFragment = SqlFragment.builder()
                .statementId("s2")
                .statementType("select")
                .namespace("sql-script")
                .sqlText("SELECT id FROM demo_user WHERE id = 1")
                .build();

        assertTrue(ruleService.checkSql(badFragment).stream()
                .anyMatch(v -> "KEYWORD_UPPERCASE".equals(v.getRule().getCheckerName())));
        assertFalse(ruleService.checkSql(goodFragment).stream()
                .anyMatch(v -> "KEYWORD_UPPERCASE".equals(v.getRule().getCheckerName())));
    }
}
