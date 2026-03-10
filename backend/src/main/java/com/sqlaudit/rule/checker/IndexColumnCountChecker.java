package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class IndexColumnCountChecker implements SqlChecker {

    private static final Pattern CREATE_INDEX = Pattern.compile("(?is)^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\b");

    @Override
    public String name() {
        return "INDEX_COLUMN_COUNT";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String sql = fragment.getSqlText();
        if (!CREATE_INDEX.matcher(sql).find()) {
            return CheckResult.pass();
        }
        int columnCount = SqlScriptRuleSupport.extractIndexColumnCount(sql);
        if (columnCount > 5) {
            return CheckResult.fail(
                    "组合索引字段个数不能超过 5 个，当前为 " + columnCount + " 个",
                    columnCount + " columns"
            );
        }
        return CheckResult.pass();
    }
}
