package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.3.4 【Should】查询条件中尽量少使用 !=, <>, NOT IN 等无法利用索引的操作符
 */
@Component
public class NotEqualOpsChecker implements SqlChecker {

    private static final Pattern NEG_OPS = Pattern.compile(
            "(!=|<>|\\bnot\\s+in\\b)", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "NOT_EQUAL_OPS";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        Matcher matcher = NEG_OPS.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail(
                    "检测到负向操作符 '" + matcher.group(1) + "'，可能无法利用索引",
                    matcher.group(1)
            );
        }
        return CheckResult.pass();
    }
}
