package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class UpdateRequireWhereChecker implements SqlChecker {

    private static final Pattern UPDATE_WITHOUT_WHERE = Pattern.compile(
            "\\bUPDATE\\b.*\\bSET\\b(?!.*\\bWHERE\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Override
    public String name() {
        return "UPDATE_REQUIRE_WHERE";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (!"update".equalsIgnoreCase(fragment.getStatementType())) {
            return CheckResult.pass();
        }
        if (UPDATE_WITHOUT_WHERE.matcher(fragment.getSqlText()).find()) {
            String sql = fragment.getSqlText();
            return CheckResult.fail(
                    "UPDATE 语句必须包含 WHERE 子句，防止全表更新",
                    sql.length() > 100 ? sql.substring(0, 100) + "..." : sql
            );
        }
        return CheckResult.pass();
    }
}
