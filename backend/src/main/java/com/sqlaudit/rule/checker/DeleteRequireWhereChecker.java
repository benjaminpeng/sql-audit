package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class DeleteRequireWhereChecker implements SqlChecker {

    private static final Pattern DELETE_WITHOUT_WHERE = Pattern.compile(
            "\\bDELETE\\s+FROM\\b(?!.*\\bWHERE\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Override
    public String name() {
        return "DELETE_REQUIRE_WHERE";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (!"delete".equalsIgnoreCase(fragment.getStatementType())) {
            return CheckResult.pass();
        }
        if (DELETE_WITHOUT_WHERE.matcher(fragment.getSqlText()).find()) {
            String sql = fragment.getSqlText();
            return CheckResult.fail(
                    "DELETE 语句必须包含 WHERE 子句，防止全表删除",
                    sql.length() > 100 ? sql.substring(0, 100) + "..." : sql
            );
        }
        return CheckResult.pass();
    }
}
