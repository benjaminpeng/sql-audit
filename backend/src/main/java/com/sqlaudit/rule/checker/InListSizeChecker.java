package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.3.6 【Should】IN 的候选子集不宜过大，建议不超过100个
 */
@Component
public class InListSizeChecker implements SqlChecker {

    private static final Pattern IN_LIST = Pattern.compile(
            "\\bin\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int MAX_IN_SIZE = 100;

    @Override
    public String name() {
        return "IN_LIST_SIZE";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        Matcher matcher = IN_LIST.matcher(fragment.getSqlText());
        while (matcher.find()) {
            String content = matcher.group(1);
            // 跳过子查询
            if (content.toLowerCase().contains("select")) continue;
            int count = content.split(",").length;
            if (count > MAX_IN_SIZE) {
                return CheckResult.fail(
                        "IN 子句参数个数(" + count + ")超过" + MAX_IN_SIZE + "，建议使用 ANY 表达式改写",
                        "IN (" + count + " items)"
                );
            }
        }
        return CheckResult.pass();
    }
}
