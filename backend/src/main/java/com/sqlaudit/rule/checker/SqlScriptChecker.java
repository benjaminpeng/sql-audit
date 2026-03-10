package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;

import java.util.List;

public interface SqlScriptChecker {

    String name();

    List<CheckResult> check(List<SqlFragment> fragments);

    record CheckResult(SqlFragment fragment, String message, String matchedText) {
    }
}
