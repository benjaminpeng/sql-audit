-- 测试 SQL 变更脚本 (包含多种违规场景)

-- OG_3_4_1: SELECT * 禁止
SELECT * FROM t_user WHERE status = 1;

-- OG_3_3_1: = NULL 判断
SELECT id, name FROM t_user WHERE email = NULL;

-- OG_3_6_1: UPDATE 无 WHERE
UPDATE t_user SET status = 0;

-- OG_3_6_1: DELETE 无 WHERE  
DELETE FROM t_log;

-- OG_3_3_5: LIKE 前缀 %
SELECT id FROM t_user WHERE name LIKE '%test%';

-- OG_3_4_4: UNION 代替 UNION ALL
SELECT id FROM t_user UNION SELECT id FROM t_admin;

-- OG_3_7_1: SQL 注入风险 (${}拼接)
SELECT id FROM t_orders WHERE user_id = ${userId};

-- 正常语句
SELECT id, username, email FROM t_user WHERE id = 1;
INSERT INTO t_log (action, created_at) VALUES ('login', NOW());
