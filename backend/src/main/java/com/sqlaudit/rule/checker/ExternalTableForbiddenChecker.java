package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ExternalTableForbiddenChecker implements SqlChecker {

    private static final Pattern EXTERNAL_TABLE = Pattern.compile("(?is)\\bCREATE\\s+(?:FOREIGN|EXTERNAL)\\s+TABLE\\b");

    @Override
    public String name() {
        return "EXTERNAL_TABLE_FORBIDDEN";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (EXTERNAL_TABLE.matcher(fragment.getSqlText()).find()) {
            return CheckResult.fail("禁止使用外部表", "EXTERNAL TABLE");
        }
        return CheckResult.pass();
    }
}
