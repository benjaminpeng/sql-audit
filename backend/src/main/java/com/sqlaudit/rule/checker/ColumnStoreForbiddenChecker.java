package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ColumnStoreForbiddenChecker implements SqlChecker {

    private static final Pattern COLUMN_STORE = Pattern.compile("(?is)\\bORIENTATION\\s*=\\s*COLUMN\\b|\\bCOLUMN\\s+STORE\\b");

    @Override
    public String name() {
        return "COLUMN_STORE_FORBIDDEN";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        Matcher matcher = COLUMN_STORE.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail(
                    "禁止使用列存表",
                    matcher.group()
            );
        }
        return CheckResult.pass();
    }
}
