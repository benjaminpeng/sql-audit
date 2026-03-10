package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DblinkForbiddenChecker implements SqlChecker {

    private static final Pattern DBLINK = Pattern.compile("(?i)\\bdblink\\b");

    @Override
    public String name() {
        return "DBLINK_FORBIDDEN";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        Matcher matcher = DBLINK.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail("禁止使用 dblink", matcher.group());
        }
        return CheckResult.pass();
    }
}
