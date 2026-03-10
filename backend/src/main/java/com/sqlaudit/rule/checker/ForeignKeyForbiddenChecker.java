package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ForeignKeyForbiddenChecker implements SqlChecker {

    private static final Pattern FOREIGN_KEY = Pattern.compile("(?is)\\bFOREIGN\\s+KEY\\b|\\bREFERENCES\\b");

    @Override
    public String name() {
        return "FOREIGN_KEY_FORBIDDEN";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        Matcher matcher = FOREIGN_KEY.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail(
                    "禁止使用外键约束，建议由应用层维护关联关系",
                    matcher.group()
            );
        }
        return CheckResult.pass();
    }
}
