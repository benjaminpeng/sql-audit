package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AliasNameCharsChecker implements SqlChecker {

    private static final Pattern AS_ALIAS = Pattern.compile("(?i)\\bAS\\s+(\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_$]*)");
    private static final Pattern TABLE_ALIAS = Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+[\"\\w.()]+\\s+(?:AS\\s+)?(\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_$]*)");
    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9_]+$");
    private static final Set<String> RESERVED = Set.of("where", "group", "order", "limit", "having", "on", "using");

    @Override
    public String name() {
        return "ALIAS_NAME_CHARS";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        CheckResult result = checkPattern(fragment.getSqlText(), AS_ALIAS);
        if (result.violated()) {
            return result;
        }
        return checkPattern(fragment.getSqlText(), TABLE_ALIAS);
    }

    private CheckResult checkPattern(String sql, Pattern pattern) {
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String alias = matcher.group(1);
            String normalized = alias.startsWith("\"") && alias.endsWith("\"")
                    ? alias.substring(1, alias.length() - 1)
                    : alias;
            if (RESERVED.contains(normalized.toLowerCase())) {
                continue;
            }
            if (!ALLOWED.matcher(normalized).matches()) {
                return CheckResult.fail(
                        "SQL 别名只允许使用小写字母、数字和下划线",
                        alias
                );
            }
        }
        return CheckResult.pass();
    }
}
