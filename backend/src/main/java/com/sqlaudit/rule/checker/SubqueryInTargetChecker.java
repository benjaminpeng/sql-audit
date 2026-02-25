package com.sqlaudit.rule.checker;

import com.sqlaudit.model.SqlFragment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 3.9.3 【Should】避免在SELECT目标列中使用子查询
 */
@Component
public class SubqueryInTargetChecker implements SqlChecker {

    private static final Pattern FROM_PATTERN = Pattern.compile("\\bfrom\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "SUBQUERY_IN_TARGET";
    }

    @Override
    public CheckResult check(SqlFragment fragment) {
        if (!"select".equalsIgnoreCase(fragment.getStatementType())) {
            return CheckResult.pass();
        }

        String sql = fragment.getSqlText();
        // 简单分割 FROM，取第一部分作为目标列区域
        // 注意：这种分割对于嵌套子查询可能不准确，但作为静态扫描足够覆盖大多数情况
        String[] parts = FROM_PATTERN.split(sql, 2);
        
        if (parts.length > 1) {
            String targetList = parts[0];
            // 检查目标列中是否有 SELECT
            // 排除 SELECT 开头的那个（即主 SELECT）
            String innerContent = targetList.trim().substring(6); // 去掉 "SELECT"
            
            if (innerContent.toLowerCase().contains("select")) {
                 return CheckResult.warn(
                        "SELECT 目标列中包含子查询，可能导致无法下推影响执行性能",
                        "SELECT ... (subquery) ..."
                );
            }
        }
        return CheckResult.pass();
    }
}
