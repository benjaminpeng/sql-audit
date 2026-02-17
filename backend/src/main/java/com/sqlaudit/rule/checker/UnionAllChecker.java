package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.4.4 【Should】优先使用 UNION ALL，少使用 UNION（需要去重排序开销大）
 */
@Component
public class UnionAllChecker implements SqlChecker {

    private static final Pattern UNION_WITHOUT_ALL = Pattern.compile(
            "\\bunion\\b(?!\\s+all)", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "UNION_ALL";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        Matcher matcher = UNION_WITHOUT_ALL.matcher(fragment.getSqlText());
        if (matcher.find()) {
            return CheckResult.fail(
                    "检测到 UNION，如无需去重请使用 UNION ALL 以提升性能",
                    matcher.group()
            );
        }
        return CheckResult.pass();
    }
}
