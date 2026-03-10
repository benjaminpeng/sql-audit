package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ObjectNameCharsChecker implements SqlChecker {

    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9_]+$");

    @Override
    public String name() {
        return "OBJECT_NAME_CHARS";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String sql = fragment.getSqlText();
        return SqlScriptRuleSupport.extractPrimaryObjectName(sql)
                .map(identifier -> {
                    for (String part : SqlScriptRuleSupport.splitIdentifierParts(identifier)) {
                        if (!ALLOWED.matcher(part).matches()) {
                            return CheckResult.fail(
                                    "对象名仅允许使用小写字母、数字和下划线",
                                    identifier
                            );
                        }
                    }
                    return CheckResult.pass();
                })
                .orElseGet(CheckResult::pass);
    }
}
