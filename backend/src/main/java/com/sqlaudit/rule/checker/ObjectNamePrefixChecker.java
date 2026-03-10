package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ObjectNamePrefixChecker implements SqlChecker {

    @Override
    public String name() {
        return "OBJECT_NAME_PREFIX";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        return SqlScriptRuleSupport.extractPrimaryObjectName(fragment.getSqlText())
                .map(identifier -> {
                    for (String part : SqlScriptRuleSupport.splitIdentifierParts(identifier)) {
                        String lower = part.toLowerCase(Locale.ROOT);
                        if (lower.startsWith("_")
                                || Character.isDigit(lower.charAt(0))
                                || lower.startsWith("pg")
                                || lower.startsWith("gs")
                                || lower.startsWith("mlog")
                                || lower.startsWith("redis")) {
                            return SqlChecker.CheckResult.fail(
                                    "对象名禁止以 pg、gs、mlog、redis、数字或下划线开头",
                                    identifier
                            );
                        }
                    }
                    return SqlChecker.CheckResult.pass();
                })
                .orElseGet(SqlChecker.CheckResult::pass);
    }
}
