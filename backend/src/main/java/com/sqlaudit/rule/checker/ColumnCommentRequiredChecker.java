package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ColumnCommentRequiredChecker implements SqlScriptChecker {

    private static final Pattern CREATE_TABLE = Pattern.compile("(?is)^\\s*CREATE\\s+TABLE\\b");
    private static final Pattern COMMENT_ON_COLUMN = Pattern.compile(
            "(?is)^\\s*COMMENT\\s+ON\\s+COLUMN\\s+([\"\\w.]+)\\s+IS\\s+");

    @Override
    public String name() {
        return "COLUMN_COMMENT_REQUIRED";
    }

    @Override
    public List<CheckResult> check(List<SqlFragment> fragments) {
        Set<String> commentedColumns = new HashSet<>();
        Map<String, SqlFragment> columnOwners = new LinkedHashMap<>();

        for (SqlFragment fragment : fragments) {
            String sql = fragment.getSqlText();
            Matcher commentMatcher = COMMENT_ON_COLUMN.matcher(sql);
            if (commentMatcher.find()) {
                commentedColumns.add(SqlScriptRuleSupport.normalizeIdentifier(commentMatcher.group(1)));
                continue;
            }

            if (CREATE_TABLE.matcher(sql).find()) {
                Optional<String> tableNameOpt = SqlScriptRuleSupport.extractCreateTableName(sql)
                        .map(SqlScriptRuleSupport::normalizeIdentifier);
                if (tableNameOpt.isEmpty()) {
                    continue;
                }
                String tableName = tableNameOpt.get();
                for (String column : SqlScriptRuleSupport.extractCreateTableColumns(sql)) {
                    columnOwners.put(tableName + "." + column, fragment);
                }
            }
        }

        List<CheckResult> violations = new ArrayList<>();
        for (Map.Entry<String, SqlFragment> entry : columnOwners.entrySet()) {
            if (!commentedColumns.contains(entry.getKey())) {
                violations.add(new CheckResult(
                        entry.getValue(),
                        "字段定义建议同时创建 COMMENT 注释信息，缺失字段: " + entry.getKey(),
                        entry.getKey()
                ));
            }
        }
        return violations;
    }
}
