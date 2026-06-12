-- ============================================
-- 场景 15: MySQL 配置不当
-- 模拟: innodb_buffer_pool_size 过小
-- ============================================

-- 检查 InnoDB Buffer Pool 大小
SHOW VARIABLES LIKE 'innodb_buffer_pool_size';
SELECT @@innodb_buffer_pool_size / 1024 / 1024 / 1024 AS buffer_pool_gb;

-- 检查 Buffer Pool 命中率
SELECT
    (1 - (SUM(IF(VARIABLE_NAME LIKE 'Innodb_buffer_pool_reads', VARIABLE_VALUE, 0))
    / NULLIF(SUM(IF(VARIABLE_NAME LIKE 'Innodb_buffer_pool_read_requests', VARIABLE_VALUE, 0)), 0))) * 100
    AS buffer_pool_hit_ratio
FROM performance_schema.global_status
WHERE VARIABLE_NAME LIKE 'Innodb_buffer_pool_read%';

-- 如果命中率 < 95%，建议增大 innodb_buffer_pool_size
-- 建议值: 物理内存的 60-70%

-- 检查其他重要配置:
SHOW VARIABLES LIKE 'innodb_log_file_size';
SHOW VARIABLES LIKE 'innodb_flush_log_at_trx_commit';
SHOW VARIABLES LIKE 'sync_binlog';
SHOW VARIABLES LIKE 'max_connections';
