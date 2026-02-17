package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.8.1 【Must】禁止超过8表关联，建议不超过5表
 */
@Component
public class JoinTableCountChecker implements SqlChecker {

    private static final Pattern JOIN_PATTERN = Pattern.compile("\\bjoin\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM_PART = Pattern.compile("from\\s+(.*?)(where|order|group|having|limit|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public String name() {
        return "JOIN_TABLE_COUNT";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String sql = fragment.getSqlText();
        int joinCount = 0;
        Matcher joinMatcher = JOIN_PATTERN.matcher(sql);
        while (joinMatcher.find()) joinCount++;

        int implicitCount = 0;
        Matcher fromMatcher = FROM_PART.matcher(sql);
        if (fromMatcher.find()) {
            String fromClause = fromMatcher.group(1);
            implicitCount = fromClause.split(",").length - 1;
            if (implicitCount < 0) implicitCount = 0;
        }

        int total = joinCount + implicitCount;
        if (total >= 8) {
            return CheckResult.fail(
                    "关联表数量(" + (total + 1) + ")超过8个，强制禁止",
                    total + 1 + " tables joined"
            );
        } else if (total >= 5) {
            return CheckResult.fail(
                    "关联表数量(" + (total + 1) + ")超过5个，建议优化减少关联",
                    total + 1 + " tables joined"
            );
        }
        return CheckResult.pass();
    }
}
