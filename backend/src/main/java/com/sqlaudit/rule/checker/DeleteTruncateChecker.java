package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

/**
 * 3.7.2 【Must】清空表建议使用 TRUNCATE，而不是没有 WHERE 的 DELETE
 */
@Component
public class DeleteTruncateChecker implements SqlChecker {

    @Override
    public String name() {
        return "DELETE_TRUNCATE";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (!"delete".equalsIgnoreCase(fragment.getStatementType())) {
            return CheckResult.pass();
        }
        if (!fragment.getSqlText().toLowerCase().contains("where")) {
            return CheckResult.fail(
                    "全表删除建议使用 TRUNCATE TABLE 代替 DELETE",
                    "DELETE without WHERE"
            );
        }
        return CheckResult.pass();
    }
}
