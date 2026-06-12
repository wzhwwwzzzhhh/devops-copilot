-- ============================================================
-- DevOps Copilot 自愈演示 - 场景模拟脚本
-- 用途: 模拟数据库问题，让 AI Agent 发现并修复
-- 用法: 在 MySQL 客户端中逐段执行
-- ============================================================

-- ===== 场景1：造一个缺索引的表 =====
-- 创建一个测试表，故意不加索引，然后插入大量数据
DROP TABLE IF EXISTS `devops_copilot`.`order_audit_log`;
CREATE TABLE `devops_copilot`.`order_audit_log` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL,
    `action` VARCHAR(32) NOT NULL,
    `operator` VARCHAR(64) NOT NULL,
    `detail` TEXT,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB;

-- 插入 2000 条数据（注意：order_id 没有索引，按 order_id 查询会全表扫描）
INSERT INTO `devops_copilot`.`order_audit_log` (`order_id`, `action`, `operator`, `detail`, `created_at`)
SELECT
    FLOOR(RAND() * 100000),
    ELT(FLOOR(1 + RAND() * 4), 'CREATE', 'UPDATE', 'DELETE', 'APPROVE'),
    ELT(FLOOR(1 + RAND() * 5), 'admin', 'ops', 'system', 'user01', 'bot'),
    REPEAT('x', FLOOR(50 + RAND() * 200)),
    NOW() - INTERVAL FLOOR(RAND() * 30) DAY
FROM (
    SELECT @i := @i + 1 AS n FROM
    (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) a,
    (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) b,
    (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) c,
    (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) d,
    (SELECT @i := 0) e
) numbers WHERE @i <= 2000;

-- 验证：查询某 order_id 的审计记录（全表扫描）
SELECT COUNT(*) FROM `devops_copilot`.`order_audit_log` WHERE order_id = 12345;

-- 验证表健康（应该显示 order_audit_log 表存在）
-- 但注意：order_id 没有索引！

-- ===== 场景2：模拟碎片 =====
-- 制造碎片：反复插入和删除
INSERT INTO `devops_copilot`.`order_audit_log` (`order_id`, `action`, `operator`, `detail`, `created_at`)
VALUES (999999, 'CREATE', 'test', REPEAT('data', 100), NOW());

DELETE FROM `devops_copilot`.`order_audit_log` WHERE order_id = 999999;

-- 再重复几次
INSERT INTO `devops_copilot`.`order_audit_log` (`order_id`, `action`, `operator`, `detail`, `created_at`)
VALUES (999998, 'UPDATE', 'test', REPEAT('data', 100), NOW());
DELETE FROM `devops_copilot`.`order_audit_log` WHERE order_id = 999998;

-- ===== 验证用查询 =====
-- 1. 查看表健康（能看到 order_audit_log 没有 order_id 索引告警）
-- 2. 查看数据库大小（能看到 order_audit_log 占空间）
-- 3. 查看运行中查询

-- ===== 演示完成后清理 =====
-- DROP TABLE IF EXISTS `devops_copilot`.`order_audit_log`;
