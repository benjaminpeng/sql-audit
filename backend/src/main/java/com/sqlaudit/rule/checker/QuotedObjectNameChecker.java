package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QuotedObjectNameChecker implements SqlChecker {

    private static final Pattern QUOTED_IDENTIFIER = Pattern.compile("\"[^\"]+\"");
    private static final Pattern DDL_PATTERN = Pattern.compile("(?is)^\\s*(CREATE|ALTER|DROP|COMMENT)\\b");

    @Override
    public String name() {
        return "QUOTED_OBJECT_NAME";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String sql = fragment.getSqlText();
        if (!DDL_PATTERN.matcher(sql).find()) {
            return CheckResult.pass();
        }
        Matcher matcher = QUOTED_IDENTIFIER.matcher(sql);
        if (matcher.find()) {
            return CheckResult.fail(
                    "避免使用双引号定义数据库对象名称，除非必须区分大小写",
                    matcher.group()
            );
        }
        return CheckResult.pass();
    }
}
