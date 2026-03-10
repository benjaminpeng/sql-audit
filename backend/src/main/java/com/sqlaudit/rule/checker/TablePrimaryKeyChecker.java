package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class TablePrimaryKeyChecker implements SqlScriptChecker {

    private static final Pattern PRIMARY_KEY = Pattern.compile("(?is)\\bPRIMARY\\s+KEY\\b");
    private static final Pattern CREATE_TABLE = Pattern.compile("(?is)^\\s*CREATE\\s+TABLE\\b");
    private static final Pattern ALTER_PRIMARY = Pattern.compile("(?is)^\\s*ALTER\\s+TABLE\\b.*\\bPRIMARY\\s+KEY\\b");

    @Override
    public String name() {
        return "TABLE_PRIMARY_KEY";
    }

    @Override
    public List<CheckResult> check(List<SqlFragment> fragments) {
        Set<String> tablesWithPrimaryKey = new HashSet<>();
        Map<String, SqlFragment> createTableFragments = new LinkedHashMap<>();

        for (SqlFragment fragment : fragments) {
            String sql = fragment.getSqlText();
            if (CREATE_TABLE.matcher(sql).find()) {
                SqlScriptRuleSupport.extractCreateTableName(sql)
                        .map(SqlScriptRuleSupport::normalizeIdentifier)
                        .ifPresent(table -> {
                            createTableFragments.put(table, fragment);
                            if (PRIMARY_KEY.matcher(sql).find()) {
                                tablesWithPrimaryKey.add(table);
                            }
                        });
                continue;
            }
            if (ALTER_PRIMARY.matcher(sql).find()) {
                SqlScriptRuleSupport.extractAlterTableName(sql)
                        .map(SqlScriptRuleSupport::normalizeIdentifier)
                        .ifPresent(tablesWithPrimaryKey::add);
            }
        }

        List<CheckResult> violations = new ArrayList<>();
        for (Map.Entry<String, SqlFragment> entry : createTableFragments.entrySet()) {
            if (!tablesWithPrimaryKey.contains(entry.getKey())) {
                violations.add(new CheckResult(
                        entry.getValue(),
                        "所有表必须定义主键，可在 CREATE TABLE 中内联 PRIMARY KEY，或后续使用 ALTER TABLE ... ADD PRIMARY KEY",
                        entry.getKey()
                ));
            }
        }
        return violations;
    }
}
