package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MoneyTypeForbiddenChecker implements SqlChecker {

    private static final Pattern MONEY_TYPE = Pattern.compile("(?i)\\bmoney\\b");

    @Override
    public String name() {
        return "MONEY_TYPE_FORBIDDEN";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        Matcher matcher = MONEY_TYPE.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail(
                    "禁止使用 money 类型，建议使用带精度的 NUMERIC(precision, scale)",
                    matcher.group()
            );
        }
        return CheckResult.pass();
    }
}
