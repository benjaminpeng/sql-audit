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
        String sql = fragment.getSqlText().toLowerCase().trim();
        // 只在简单 DELETE FROM table 时建议用 TRUNCATE
        // 有 WHERE 的 DELETE 不触发；有 JOIN/子查询的复杂 DELETE 也不触发
        if (!sql.contains("where") && !sql.contains("join") && !sql.contains("using")) {
            // 只有看起来像 DELETE FROM single_table 的简单语句才建议 TRUNCATE
            if (sql.matches("(?s)delete\\s+from\\s+[\\w.]+\\s*;?\\s*")) {
                return CheckResult.fail(
                        "若需清空整表，建议使用 TRUNCATE TABLE 代替 DELETE（性能更优，释放空间）",
                        "DELETE without WHERE on simple table");
            }
        }
        return CheckResult.pass();
    }
}
