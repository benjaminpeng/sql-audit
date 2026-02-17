package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.3.5 【Should】LIKE 语句 % 不应放在首字符位置（导致全表扫描）
 */
@Component
public class LikePercentStartChecker implements SqlChecker {

    private static final Pattern LIKE_PERCENT = Pattern.compile(
            "like\\s+['\"]%", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "LIKE_PERCENT_START";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        Matcher matcher = LIKE_PERCENT.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail(
                    "LIKE 查询 '%' 在首位将导致全表扫描，无法利用索引",
                    matcher.group()
            );
        }
        return CheckResult.pass();
    }
}
