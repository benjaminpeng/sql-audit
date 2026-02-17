package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

/**
 * 3.4.3 【Must】禁止使用 LOCK TABLE 语句加锁，仅允许使用 SELECT .. FOR UPDATE
 */
@Component
public class LockTableChecker implements SqlChecker {

    @Override
    public String name() {
        return "LOCK_TABLE";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (fragment.getSqlText().toLowerCase().contains("lock table")) {
            return CheckResult.fail(
                    "禁止使用 LOCK TABLE 手动锁表，仅允许使用 SELECT .. FOR UPDATE",
                    "LOCK TABLE"
            );
        }
        return CheckResult.pass();
    }
}
