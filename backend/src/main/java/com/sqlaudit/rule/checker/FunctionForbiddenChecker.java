package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class FunctionForbiddenChecker implements SqlChecker {

    private static final Pattern CREATE_FUNCTION = Pattern.compile("(?is)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?FUNCTION\\b");

    @Override
    public String name() {
        return "FUNCTION_FORBIDDEN";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (CREATE_FUNCTION.matcher(fragment.getSqlText()).find()) {
            return CheckResult.fail("禁止使用函数，特殊情况需经过审批", "CREATE FUNCTION");
        }
        return CheckResult.pass();
    }
}
