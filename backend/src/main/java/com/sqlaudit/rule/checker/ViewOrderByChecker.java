package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ViewOrderByChecker implements SqlChecker {

    private static final Pattern CREATE_VIEW = Pattern.compile("(?is)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\b");
    private static final Pattern ORDER_BY = Pattern.compile("(?is)\\bORDER\\s+BY\\b");

    @Override
    public String name() {
        return "VIEW_ORDER_BY";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String sql = fragment.getSqlText();
        if (!CREATE_VIEW.matcher(sql).find()) {
            return CheckResult.pass();
        }
        if (ORDER_BY.matcher(sql).find()) {
            return CheckResult.fail(
                    "视图定义中尽量避免排序操作，建议在调用视图时使用 ORDER BY",
                    "ORDER BY"
            );
        }
        return CheckResult.pass();
    }
}
