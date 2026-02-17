package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.8.3 【Must】避免使用逗号分隔的隐式 JOIN，应使用显式 INNER/LEFT/RIGHT JOIN
 */
@Component
public class ImplicitJoinChecker implements SqlChecker {

    private static final Pattern IMPLICIT_JOIN = Pattern.compile(
            "from\\s+[\\w.]+\\s*,\\s*[\\w.]+", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "IMPLICIT_JOIN";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        Matcher matcher = IMPLICIT_JOIN.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail(
                    "禁止使用逗号分隔的隐式 JOIN，应使用 INNER JOIN/LEFT JOIN 等显式连接",
                    matcher.group()
            );
        }
        return CheckResult.pass();
    }
}
