-- ============================================
-- 场景 4: 连接池满
-- 模拟: 大量连接未释放，耗尽连接池
-- ============================================

-- 模拟方式 1: 快速打开多个连接但不关闭
-- 可以在应用层调用
-- for i in {1..200}; do
--   mysql -u root -e "SELECT SLEEP(100)" &
-- done

-- 检查当前连接数:
SELECT COUNT(*) AS total_connections FROM information_schema.PROCESSLIST;

-- 查看最大连接数:
SHOW VARIABLES LIKE 'max_connections';

-- 查看当前活跃连接数:
SHOW STATUS LIKE 'Threads_connected';
SHOW STATUS LIKE 'Threads_running';

-- 优化:
-- 1. 临时增加 max_connections: SET GLOBAL max_connections = 500;
-- 2. 排查未关闭连接的代码
-- 3. 启用连接池 (如 HikariCP) 并设置合理大小
