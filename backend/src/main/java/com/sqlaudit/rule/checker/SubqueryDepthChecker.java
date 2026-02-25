package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

/**
 * 3.9.4 【Should】子查询嵌套深度不建议超过2层
 * <p>
 * 通过括号嵌套深度来计算实际子查询嵌套层级，
 * 避免简单 SELECT 计数对 UNION ALL 误报的问题。
 */
@Component
public class SubqueryDepthChecker implements SqlChecker {

    @Override
    public String name() {
        return "SUBQUERY_DEPTH";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        String sql = fragment.getSqlText();
        int maxDepth = computeMaxSubqueryDepth(sql);

        if (maxDepth > 2) {
            return CheckResult.fail(
                    "子查询嵌套深度为 " + maxDepth + " 层，超过推荐的 2 层上限，建议简化",
                    maxDepth + "-level nested subqueries");
        }
        return CheckResult.pass();
    }

    /**
     * 通过跟踪括号深度来计算最大子查询嵌套层级。
     * 每次在括号内遇到 SELECT 关键字，认为进入一层子查询。
     */
    private int computeMaxSubqueryDepth(String sql) {
        String upper = sql.toUpperCase();
        int parenDepth = 0;
        int maxSubqueryDepth = 0;
        int currentSubqueryDepth = 0;
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < upper.length(); i++) {
            char c = sql.charAt(i);

            // Handle string literals
            if (!inString && (c == '\'' || c == '"')) {
                inString = true;
                stringChar = c;
                continue;
            }
            if (inString) {
                if (c == stringChar)
                    inString = false;
                continue;
            }

            // Track parentheses
            if (c == '(') {
                parenDepth++;
                // Check if this opens a subquery: look for SELECT after (
                int j = i + 1;
                while (j < upper.length() && Character.isWhitespace(upper.charAt(j)))
                    j++;
                if (j + 5 < upper.length() && upper.substring(j, j + 6).equals("SELECT")
                        && (j + 6 >= upper.length() || !Character.isLetterOrDigit(upper.charAt(j + 6)))) {
                    currentSubqueryDepth++;
                    maxSubqueryDepth = Math.max(maxSubqueryDepth, currentSubqueryDepth);
                }
            } else if (c == ')') {
                if (parenDepth > 0) {
                    parenDepth--;
                    if (currentSubqueryDepth > 0) {
                        currentSubqueryDepth--;
                    }
                }
            }
        }
        return maxSubqueryDepth;
    }
}
