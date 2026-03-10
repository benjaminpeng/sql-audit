package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ProcedureForbiddenChecker implements SqlChecker {

    private static final Pattern CREATE_PROCEDURE = Pattern.compile("(?is)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?PROCEDURE\\b");

    @Override
    public String name() {
        return "PROCEDURE_FORBIDDEN";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (CREATE_PROCEDURE.matcher(fragment.getSqlText()).find()) {
            return CheckResult.fail("禁止使用存储过程", "CREATE PROCEDURE");
        }
        return CheckResult.pass();
    }
}
