package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class UstoreRequiredChecker implements SqlChecker {

    private static final Pattern CREATE_TABLE = Pattern.compile("(?is)^\\s*CREATE\\s+TABLE\\b");
    private static final Pattern USTORE = Pattern.compile("(?is)\\bustore\\b");
    private static final Pattern ASTORE = Pattern.compile("(?is)\\bastore\\b");

    @Override
    public String name() {
        return "USTORE_REQUIRED";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String sql = fragment.getSqlText();
        if (!CREATE_TABLE.matcher(sql).find()) {
            return CheckResult.pass();
        }
        if (ASTORE.matcher(sql).find()) {
            return CheckResult.fail("表存储必须使用 ustore，禁止使用 astore", "astore");
        }
        if (!USTORE.matcher(sql).find()) {
            return CheckResult.fail("表定义应显式声明 ustore 存储", "CREATE TABLE");
        }
        return CheckResult.pass();
    }
}
