package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class DatabaseUtf8Checker implements SqlChecker {

    private static final Pattern CREATE_DATABASE = Pattern.compile("(?is)^\\s*CREATE\\s+DATABASE\\b");
    private static final Pattern UTF8_ENCODING = Pattern.compile("(?is)\\bENCODING\\s*=\\s*'UTF8'|\\bENCODING\\s+'UTF8'");

    @Override
    public String name() {
        return "DATABASE_UTF8";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String sql = fragment.getSqlText();
        if (!CREATE_DATABASE.matcher(sql).find()) {
            return CheckResult.pass();
        }
        if (!UTF8_ENCODING.matcher(sql).find()) {
            return CheckResult.fail(
                    "CREATE DATABASE 必须显式指定 ENCODING='UTF8'",
                    "CREATE DATABASE"
            );
        }
        return CheckResult.pass();
    }
}
