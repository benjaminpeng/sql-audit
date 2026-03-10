package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LargeObjectForbiddenChecker implements SqlChecker {

    private static final Pattern LARGE_OBJECT = Pattern.compile(
            "(?is)\\blo_(?:create|import|export|unlink)\\b|\\bCREATE\\s+LARGE\\s+OBJECT\\b");

    @Override
    public String name() {
        return "LARGE_OBJECT_FORBIDDEN";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        Matcher matcher = LARGE_OBJECT.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail("禁止使用大对象相关语法，建议改用 bytea 或外部存储", matcher.group());
        }
        return CheckResult.pass();
    }
}
