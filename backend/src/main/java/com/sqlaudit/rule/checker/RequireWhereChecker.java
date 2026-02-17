package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 检查 UPDATE/DELETE 语句是否有 WHERE 子句
 */
@Component
public class RequireWhereChecker implements SqlChecker {

    private static final Pattern UPDATE_WITHOUT_WHERE = Pattern.compile(
            "\\bUPDATE\\b.*\\bSET\\b(?!.*\\bWHERE\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern DELETE_WITHOUT_WHERE = Pattern.compile(
            "\\bDELETE\\s+FROM\\b(?!.*\\bWHERE\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Override
    public String name() {
        return "REQUIRE_WHERE";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String type = fragment.getStatementType();
        String sql = fragment.getSqlText();

        if ("update".equalsIgnoreCase(type)) {
            if (UPDATE_WITHOUT_WHERE.matcher(sql).find()) {
                return CheckResult.fail(
                        "UPDATE 语句必须包含 WHERE 子句，防止全表更新",
                        sql.length() > 100 ? sql.substring(0, 100) + "..." : sql
                );
            }
        }

        if ("delete".equalsIgnoreCase(type)) {
            if (DELETE_WITHOUT_WHERE.matcher(sql).find()) {
                return CheckResult.fail(
                        "DELETE 语句必须包含 WHERE 子句，防止全表删除",
                        sql.length() > 100 ? sql.substring(0, 100) + "..." : sql
                );
            }
        }

        return CheckResult.pass();
    }
}
