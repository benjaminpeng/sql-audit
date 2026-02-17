package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.9.4 【Should】子查询嵌套深度不建议超过2层
 */
@Component
public class SubqueryDepthChecker implements SqlChecker {

    private static final Pattern SELECT_PATTERN = Pattern.compile("\\bselect\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "SUBQUERY_DEPTH";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        // 统计 SELECT 关键字出现次数，>3 表示嵌套可能超过2层
        Matcher matcher = SELECT_PATTERN.matcher(fragment.getSqlText());
        int count = 0;
        while (matcher.find()) count++;

        if (count > 3) {
            return CheckResult.fail(
                    "子查询嵌套深度可能超过2层(检测到 " + count + " 个 SELECT)，建议简化",
                    count + " nested SELECTs"
            );
        }
        return CheckResult.pass();
    }
}
