package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class DatabaseLocaleChecker implements SqlChecker {

    private static final Pattern CREATE_DATABASE = Pattern.compile("(?is)^\\s*CREATE\\s+DATABASE\\b");
    private static final Pattern LC_CTYPE = Pattern.compile("(?is)\\bLC_CTYPE\\s*=\\s*'en_US\\.UTF8'|\\bLC_CTYPE\\s+'en_US\\.UTF8'");
    private static final Pattern LC_COLLATE = Pattern.compile("(?is)\\bLC_COLLATE\\s*=\\s*'C'|\\bLC_COLLATE\\s+'C'");

    @Override
    public String name() {
        return "DATABASE_LOCALE";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String sql = fragment.getSqlText();
        if (!CREATE_DATABASE.matcher(sql).find()) {
            return CheckResult.pass();
        }
        if (!LC_CTYPE.matcher(sql).find() || !LC_COLLATE.matcher(sql).find()) {
            return CheckResult.fail(
                    "CREATE DATABASE 必须显式指定 LC_CTYPE='en_US.UTF8' 且 LC_COLLATE='C'",
                    "CREATE DATABASE locale"
            );
        }
        return CheckResult.pass();
    }
}
