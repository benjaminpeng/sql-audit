# Default Rules

This skill ships with `41` built-in default rules.

## Shared rules (`21`)

These apply to both MyBatis mapper scans and `.sql` script scans.

| Checker | Section | Severity | Rule |
| --- | --- | --- | --- |
| `SCHEMA_PREFIX` | `3.2.2` | `WARNING` | 建议使用 Schema 前缀 |
| `NULL_COMPARISON` | `3.3.1` | `ERROR` | 禁止用 `=` 或 `!=` 判断 `NULL` |
| `WHERE_FUNCTION` | `3.3.3` | `WARNING` | `WHERE` 条件字段禁用函数 |
| `NOT_EQUAL_OPS` | `3.3.4` | `WARNING` | 少用负向操作符 |
| `LIKE_PERCENT_START` | `3.3.5` | `WARNING` | `LIKE` 禁止前缀 `%` |
| `IN_LIST_SIZE` | `3.3.6` | `WARNING` | `IN` 子集不宜过大 |
| `NO_SELECT_STAR` | `3.4.1` | `ERROR` | 禁止 `SELECT *` |
| `LOCK_TABLE` | `3.4.3` | `ERROR` | 禁止 `LOCK TABLE` |
| `UNION_ALL` | `3.4.4` | `WARNING` | 优先使用 `UNION ALL` |
| `COUNT_USAGE` | `3.4.5` | `INFO` | 慎用 `count()` |
| `REQUIRE_LIMIT` | `3.4.6` | `WARNING` | `SELECT` 建议分页 |
| `UPDATE_LIMIT` | `3.6.1` | `ERROR` | `UPDATE` 禁用 `LIMIT` |
| `UPDATE_REQUIRE_WHERE` | `3.6.3` | `ERROR` | `UPDATE` 必须有 `WHERE` |
| `DELETE_TRUNCATE` | `3.7.2` | `ERROR` | 全表删除用 `TRUNCATE` |
| `DELETE_REQUIRE_WHERE` | `3.7.3` | `ERROR` | `DELETE` 必须有 `WHERE` |
| `JOIN_TABLE_COUNT` | `3.8.1` | `ERROR` | 限制关联表数量 |
| `IMPLICIT_JOIN` | `3.8.3` | `ERROR` | 禁止隐式 `JOIN` |
| `SUBQUERY_IN_TARGET` | `3.9.3` | `WARNING` | 目标列禁用子查询 |
| `SUBQUERY_DEPTH` | `3.9.4` | `WARNING` | 子查询嵌套不超过 2 层 |
| `SQL_INJECTION_RISK` | `MyBatis` | `ERROR` | MyBatis `${}` 注入风险 |
| `KEYWORD_UPPERCASE` | `Style` | `WARNING` | SQL 关键字统一大写 |

## SQL script only rules (`20`)

These only apply to `.sql` script scans.

| Checker | Section | Severity | Rule |
| --- | --- | --- | --- |
| `QUOTED_OBJECT_NAME` | `2.2.3` | `WARNING` | 避免使用双引号对象名 |
| `OBJECT_NAME_CHARS` | `2.2.4` | `ERROR` | 对象名字符集限制 |
| `OBJECT_NAME_PREFIX` | `2.2.4` | `ERROR` | 对象名禁止特殊前缀 |
| `ALIAS_NAME_CHARS` | `2.2.5` | `WARNING` | 别名字符限制 |
| `DATABASE_UTF8` | `2.3.4` | `ERROR` | 数据库字符集必须为 `UTF8` |
| `DATABASE_LOCALE` | `2.3.5` | `ERROR` | 数据库 Locale 必须显式指定 |
| `TABLE_PRIMARY_KEY` | `2.6.4` | `ERROR` | 所有表必须有主键 |
| `FOREIGN_KEY_FORBIDDEN` | `2.6.5` | `ERROR` | 禁止使用外键 |
| `USTORE_REQUIRED` | `2.6.7` | `ERROR` | 表存储必须使用 `ustore` |
| `COLUMN_STORE_FORBIDDEN` | `2.6.8` | `ERROR` | 禁止使用列存表 |
| `VIEW_ORDER_BY` | `2.6.10` | `WARNING` | 视图定义尽量避免排序 |
| `MONEY_TYPE_FORBIDDEN` | `2.7.1` | `ERROR` | 禁止使用 `money` 类型 |
| `COLUMN_COMMENT_REQUIRED` | `2.7.12` | `WARNING` | 字段定义建议补充注释 |
| `INDEX_COLUMN_COUNT` | `2.8.3` | `ERROR` | 组合索引字段数不超过 5 个 |
| `TRIGGER_FORBIDDEN` | `2.9.1` | `ERROR` | 禁止使用触发器 |
| `PROCEDURE_FORBIDDEN` | `2.9.2` | `ERROR` | 禁止使用存储过程 |
| `FUNCTION_FORBIDDEN` | `2.9.3` | `ERROR` | 禁止使用函数 |
| `EXTERNAL_TABLE_FORBIDDEN` | `2.9.4` | `ERROR` | 禁止使用外部表 |
| `DBLINK_FORBIDDEN` | `2.9.4` | `ERROR` | 禁止使用 `dblink` |
| `LARGE_OBJECT_FORBIDDEN` | `2.9.4` | `ERROR` | 禁止使用大对象相关语法 |

## Notes

- `TABLE_PRIMARY_KEY` and `COLUMN_COMMENT_REQUIRED` are script-level rules and evaluate the full parsed script, not one statement at a time.
- SQL-script-only rules are filtered out during MyBatis directory scans through `AuditRule.appliesTo`.
