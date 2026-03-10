package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class TriggerForbiddenChecker implements SqlChecker {

    private static final Pattern CREATE_TRIGGER = Pattern.compile("(?is)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?TRIGGER\\b");

    @Override
    public String name() {
        return "TRIGGER_FORBIDDEN";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (CREATE_TRIGGER.matcher(fragment.getSqlText()).find()) {
            return CheckResult.fail("禁止使用触发器", "CREATE TRIGGER");
        }
        return CheckResult.pass();
    }
}
