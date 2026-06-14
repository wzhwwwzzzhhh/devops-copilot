package com.devboss.tools;

import com.devboss.entity.ServiceConnection;
import com.devboss.service.ServiceConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MySQL 数据库监控与诊断：连接池、慢查询、死锁检测等 */
@Service
public class DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);
    private final ObjectMapper objectMapper;
    private final ServiceConnectionService connectionService;

    public DatabaseService(ObjectMapper objectMapper, ServiceConnectionService connectionService) {
        this.objectMapper = objectMapper;
        this.connectionService = connectionService;
    }

    /**
     * 查询数据库状态
     * 优先连接真实 MySQL；无连接配置时降级读 Mock
     */
    @SuppressWarnings("unchecked")
    public String getDbStatus(String instanceName) {
        try {
            List<ServiceConnection> mysqlConns = connectionService.findByType("mysql");
            if (!mysqlConns.isEmpty()) {
                // 取第一个 MySQL 连接做真实查询（大部分场景只有一个数据库）
                // 如果有多个，尝试按 instanceName 匹配标签；匹配不上就用第一个
                ServiceConnection target = mysqlConns.get(0);
                for (ServiceConnection conn : mysqlConns) {
                    if (conn.getTags() != null && instanceName != null
                            && instanceName.contains(conn.getTags().split(",")[0].trim())) {
                        target = conn;
                        break;
                    }
                }
                return queryRealMysql(target);
            }
            return readMockDbStatus();
        } catch (Exception e) {
            log.error("查询数据库状态失败: instance={}", instanceName, e);
            return "{\"error\": \"查询数据库状态失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 连接真实 MySQL，采集连接池、运行中查询、数据库概览等信息
     */
    private String queryRealMysql(ServiceConnection conn) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode instances = result.putArray("instances");

        String url = "jdbc:mysql://" + conn.getHost() + ":" + conn.getPort()
                + "/?useSSL=false&connectTimeout=5000&useInformationSchema=true";

        try (Connection c = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword());
             Statement stmt = c.createStatement()) {

            ObjectNode instance = instances.addObject();
            instance.put("name", conn.getName());
            instance.put("host", conn.getHost() + ":" + conn.getPort());
            instance.put("type", "mysql_real");

            // ---- 1. 连接池 ----
            try (ResultSet rs = stmt.executeQuery("SHOW STATUS LIKE 'Threads_connected'")) {
                int threads = 0;
                if (rs.next()) threads = rs.getInt("Value");
                rs.close();

                ResultSet rs2 = stmt.executeQuery("SHOW VARIABLES LIKE 'max_connections'");
                int maxConn = 100;
                if (rs2.next()) maxConn = rs2.getInt("Value");
                rs2.close();

                int usagePercent = maxConn > 0 ? (threads * 100 / maxConn) : 0;
                ObjectNode pool = instance.putObject("connection_pool");
                pool.put("active", threads);
                pool.put("max", maxConn);
                pool.put("usage_percent", usagePercent);

                if (usagePercent > 80) {
                    instance.put("status", "DEGRADED");
                    instance.put("status_reason", "连接池使用率 " + usagePercent + "%，超过 80% 阈值");
                } else {
                    instance.put("status", "HEALTHY");
                }
            }

            // ---- 2. 运行中的查询（TOP 10） ----
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT ID, USER, HOST, DB, COMMAND, TIME, STATE, INFO "
                    + "FROM information_schema.PROCESSLIST "
                    + "WHERE COMMAND != 'Sleep' ORDER BY TIME DESC LIMIT 10")) {
                ArrayNode runningQueries = instance.putArray("running_queries");
                while (rs.next()) {
                    ObjectNode q = runningQueries.addObject();
                    q.put("id", rs.getLong("ID"));
                    q.put("user", rs.getString("USER"));
                    q.put("host", rs.getString("HOST"));
                    q.put("db", rs.getString("DB"));
                    q.put("command", rs.getString("COMMAND"));
                    q.put("time_sec", rs.getInt("TIME"));
                    q.put("state", rs.getString("STATE"));
                    String info = rs.getString("INFO");
                    if (info != null && info.length() > 200) info = info.substring(0, 200) + "...";
                    q.put("sql", info != null ? info : "");
                }
            }

            // ---- 3. 数据库列表及大小 ----
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT TABLE_SCHEMA, ROUND(SUM(DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 1) AS size_mb, "
                    + "COUNT(DISTINCT TABLE_NAME) AS table_count "
                    + "FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA NOT IN ('mysql','performance_schema','information_schema','sys') "
                    + "GROUP BY TABLE_SCHEMA ORDER BY size_mb DESC")) {
                ArrayNode databases = instance.putArray("databases");
                while (rs.next()) {
                    ObjectNode db = databases.addObject();
                    db.put("name", rs.getString("TABLE_SCHEMA"));
                    db.put("size_mb", rs.getDouble("size_mb"));
                    db.put("table_count", rs.getInt("table_count"));
                }
            }

            // ---- 4. SQL 性能分析（从 performance_schema 获取） ----
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT DIGEST_TEXT, COUNT_STAR, "
                    + "ROUND(SUM_TIMER_WAIT / 1000000000000, 3) AS total_sec, "
                    + "ROUND(AVG_TIMER_WAIT / 1000000, 1) AS avg_ms, "
                    + "ROUND(SUM_ROWS_EXAMINED / GREATEST(SUM_ROWS_SENT, 1), 1) AS rows_examined_ratio, "
                    + "SUM_ROWS_EXAMINED, SUM_ROWS_SENT, "
                    + "SUM_NO_INDEX_USED AS no_index_count "
                    + "FROM performance_schema.events_statements_summary_by_digest "
                    + "WHERE DIGEST_TEXT IS NOT NULL "
                    + "AND SCHEMA_NAME NOT IN ('mysql','performance_schema','information_schema','sys') "
                    + "ORDER BY SUM_TIMER_WAIT DESC LIMIT 10")) {
                ArrayNode slowQueries = instance.putArray("slow_queries_analysis");
                while (rs.next()) {
                    ObjectNode sq = slowQueries.addObject();
                    String digest = rs.getString("DIGEST_TEXT");
                    if (digest != null && digest.length() > 120) digest = digest.substring(0, 120) + "...";
                    sq.put("sql_pattern", digest != null ? digest : "");
                    sq.put("execution_count", rs.getLong("COUNT_STAR"));
                    sq.put("total_time_sec", rs.getDouble("total_sec"));
                    sq.put("avg_time_ms", rs.getDouble("avg_ms"));
                    sq.put("rows_examined_per_row_returned", rs.getDouble("rows_examined_ratio"));
                    sq.put("total_rows_examined", rs.getLong("SUM_ROWS_EXAMINED"));
                    sq.put("no_index_count", rs.getLong("no_index_count"));
                    double ratio = rs.getDouble("rows_examined_ratio");
                    if (ratio > 100) {
                        sq.put("warning", "大量扫描，缺少索引");
                    }
                }
            }

            // ---- 5. 表结构健康检查 ----
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT t.TABLE_SCHEMA, t.TABLE_NAME, t.ENGINE, t.TABLE_ROWS, "
                    + "ROUND((t.DATA_LENGTH + t.INDEX_LENGTH) / 1024 / 1024, 1) AS total_mb, "
                    + "ROUND(t.DATA_FREE / 1024 / 1024, 1) AS fragmentation_mb, "
                    + "IFNULL(k.COLUMN_NAME, '') AS primary_key_column "
                    + "FROM information_schema.TABLES t "
                    + "LEFT JOIN information_schema.KEY_COLUMN_USAGE k "
                    + "  ON t.TABLE_SCHEMA = k.TABLE_SCHEMA AND t.TABLE_NAME = k.TABLE_NAME "
                    + "  AND k.CONSTRAINT_NAME = 'PRIMARY' AND k.ORDINAL_POSITION = 1 "
                    + "WHERE t.TABLE_SCHEMA NOT IN ('mysql','performance_schema','information_schema','sys') "
                    + "AND t.TABLE_ROWS > 0 "
                    + "ORDER BY total_mb DESC LIMIT 15")) {
                ArrayNode tableHealth = instance.putArray("table_health");
                while (rs.next()) {
                    ObjectNode th = tableHealth.addObject();
                    th.put("schema", rs.getString("TABLE_SCHEMA"));
                    th.put("table", rs.getString("TABLE_NAME"));
                    th.put("engine", rs.getString("ENGINE"));
                    th.put("rows", rs.getLong("TABLE_ROWS"));
                    th.put("size_mb", rs.getDouble("total_mb"));
                    double fragMb = rs.getDouble("fragmentation_mb");
                    if (fragMb > 0) {
                        th.put("fragmentation_mb", fragMb);
                    }
                    String pk = rs.getString("primary_key_column");
                    if (pk == null || pk.isEmpty()) {
                        th.put("warning", "缺少主键");
                    }
                }
            }

            // ---- 6. 慢查询日志采集（动态启用 + 实时读取） ----
            try {
                // 尝试动态启用慢查询日志
                stmt.execute("SET GLOBAL slow_query_log = ON");
                stmt.execute("SET GLOBAL long_query_time = 2");
                stmt.execute("SET GLOBAL log_queries_not_using_indexes = ON");
            } catch (Exception e) {
                log.debug("动态设置慢查询日志失败（可能无 SUPER 权限）: {}", e.getMessage());
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT start_time, user_host, query_time, rows_examined, rows_sent, db, sql_text "
                    + "FROM mysql.slow_log "
                    + "WHERE start_time >= NOW() - INTERVAL 30 MINUTE "
                    + "ORDER BY start_time DESC LIMIT 10")) {
                ArrayNode slowLog = instance.putArray("slow_log_recent");
                while (rs.next()) {
                    ObjectNode s = slowLog.addObject();
                    s.put("start_time", rs.getString("start_time"));
                    s.put("user_host", rs.getString("user_host"));
                    s.put("query_time", rs.getString("query_time"));
                    s.put("rows_examined", rs.getLong("rows_examined"));
                    s.put("rows_sent", rs.getLong("rows_sent"));
                    s.put("db", rs.getString("db"));
                    String sql = rs.getString("sql_text");
                    if (sql != null && sql.length() > 200) sql = sql.substring(0, 200) + "...";
                    s.put("sql_text", sql != null ? sql : "");
                }
            } catch (Exception e) {
                log.debug("读取 mysql.slow_log 失败（可能无权限或无此表）: {}", e.getMessage());
            }

            log.info("MySQL 真实查询成功: host={}", conn.getHost());

        } catch (Exception e) {
            log.warn("连接真实 MySQL 失败 ({}), 降级 Mock", e.getMessage());
            return readMockDbStatus();
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"序列化失败: " + e.getMessage() + "\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private String readMockDbStatus() {
        try {
            InputStream is = getClass().getResourceAsStream("/mock/database/slow_queries.json");
            if (is == null) {
                return "{\"error\": \"未找到数据库状态数据\"}";
            }
            Map<String, Object> data = objectMapper.readValue(is, Map.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            return "{\"error\": \"读取Mock数据库状态失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 检测死锁和锁等待情况
     * 执行 SHOW ENGINE INNODB STATUS + 查询 information_schema.INNODB_LOCK_WAITS
     */
    public String detectDeadlocks() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode deadlocks = result.putArray("deadlocks");
        ArrayNode lockWaits = result.putArray("lock_waits");

        try {
            List<ServiceConnection> mysqlConns = connectionService.findByType("mysql");
            if (mysqlConns.isEmpty()) {
                // Mock 模式
                return readMockDeadlockResult();
            }

            ServiceConnection conn = mysqlConns.get(0);
            String url = "jdbc:mysql://" + conn.getHost() + ":" + conn.getPort()
                    + "/?useSSL=false&connectTimeout=5000";

            try (Connection c = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword());
                 Statement stmt = c.createStatement()) {

                // 1. SHOW ENGINE INNODB STATUS - 解析死锁信息
                try (ResultSet rs = stmt.executeQuery("SHOW ENGINE INNODB STATUS")) {
                    if (rs.next()) {
                        String statusText = rs.getString("Status");
                        if (statusText != null) {
                            parseDeadlockFromStatus(statusText, deadlocks);
                        }
                    }
                } catch (Exception e) {
                    log.debug("读取 InnoDB Status 失败: {}", e.getMessage());
                }

                // 2. INNODB_LOCK_WAITS - 当前锁等待
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT r.trx_id AS waiting_trx_id, "
                        + "r.trx_mysql_thread_id AS waiting_thread, "
                        + "TIMESTAMPDIFF(SECOND, r.trx_started, NOW()) AS wait_age_sec, "
                        + "SUBSTRING(r.trx_query, 1, 200) AS waiting_query, "
                        + "b.trx_id AS blocking_trx_id, "
                        + "b.trx_mysql_thread_id AS blocking_thread, "
                        + "SUBSTRING(b.trx_query, 1, 200) AS blocking_query "
                        + "FROM information_schema.INNODB_LOCK_WAITS w "
                        + "JOIN information_schema.INNODB_TRX r ON w.requesting_trx_id = r.trx_id "
                        + "JOIN information_schema.INNODB_TRX b ON w.blocking_trx_id = b.trx_id")) {
                    while (rs.next()) {
                        ObjectNode lw = lockWaits.addObject();
                        lw.put("waiting_trx_id", rs.getString("waiting_trx_id"));
                        lw.put("waiting_thread", rs.getLong("waiting_thread"));
                        lw.put("wait_age_sec", rs.getLong("wait_age_sec"));
                        lw.put("wait_age", formatDuration(rs.getLong("wait_age_sec")));
                        lw.put("waiting_query", rs.getString("waiting_query") != null ? rs.getString("waiting_query") : "");
                        lw.put("blocking_trx_id", rs.getString("blocking_trx_id"));
                        lw.put("blocking_thread", rs.getLong("blocking_thread"));
                        lw.put("blocking_query", rs.getString("blocking_query") != null ? rs.getString("blocking_query") : "");
                    }
                } catch (Exception e) {
                    log.debug("查询 INNODB_LOCK_WAITS 失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("死锁检测失败", e);
            return readMockDeadlockResult();
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"序列化失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 解析 SHOW ENGINE INNODB STATUS 中的死锁信息
     */
    private void parseDeadlockFromStatus(String statusText, ArrayNode deadlocks) {
        // 定位 LATEST DETECTED DEADLOCK 段
        int deadlockStart = statusText.indexOf("LATEST DETECTED DEADLOCK");
        if (deadlockStart < 0) return;

        int deadlockEnd = statusText.indexOf("WE ROLL BACK TRANSACTION", deadlockStart);
        if (deadlockEnd < 0) {
            deadlockEnd = statusText.indexOf("------------", deadlockStart + 100);
            if (deadlockEnd < 0) deadlockEnd = Math.min(deadlockStart + 5000, statusText.length());
        }

        String deadlockSection = statusText.substring(deadlockStart,
                Math.min(deadlockEnd + 200, statusText.length()));

        ObjectNode dl = deadlocks.addObject();

        // 提取死锁时间
        Pattern timePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})");
        Matcher timeMatcher = timePattern.matcher(deadlockSection);
        dl.put("time", timeMatcher.find() ? timeMatcher.group(1) : "未知");

        // 提取涉及的事务和 SQL
        List<String> transactions = new ArrayList<>();
        List<String> sqls = new ArrayList<>();

        Pattern trxPattern = Pattern.compile("TRANSACTION\\s+(\\d+)[^\\n]*");
        Matcher trxMatcher = trxPattern.matcher(deadlockSection);
        while (trxMatcher.find()) {
            transactions.add(trxMatcher.group(1));
        }

        // 提取 SQL
        Pattern sqlPattern = Pattern.compile("(?i)(SELECT|INSERT|UPDATE|DELETE)\\s+.*?;");
        Matcher sqlMatcher = sqlPattern.matcher(deadlockSection);
        while (sqlMatcher.find()) {
            String s = sqlMatcher.group().trim();
            if (s.length() > 200) s = s.substring(0, 200) + "...";
            sqls.add(s);
        }

        // 提取回滚事务
        Pattern rollbackPattern = Pattern.compile("WE ROLL BACK TRANSACTION\\s*\\(\\s*(\\d+)\\s*\\)");
        Matcher rollbackMatcher = rollbackPattern.matcher(statusText);
        String rolledBack = rollbackMatcher.find() ? rollbackMatcher.group(1) : "未知";

        ArrayNode trxArray = dl.putArray("transactions");
        transactions.forEach(trxArray::add);

        ArrayNode sqlArray = dl.putArray("sqls");
        sqls.forEach(sqlArray::add);

        dl.put("rolled_back", rolledBack);

        // 提取等待资源
        Pattern resourcePattern = Pattern.compile("waits for|hold[s]? the lock|waiting for.*?lock");
        Matcher resourceMatcher = resourcePattern.matcher(deadlockSection);
        if (resourceMatcher.find()) {
            int start = Math.max(0, resourceMatcher.start() - 50);
            int end = Math.min(deadlockSection.length(), resourceMatcher.end() + 100);
            dl.put("waiting_resource", deadlockSection.substring(start, end).replace("\n", " ").trim());
        } else {
            dl.put("waiting_resource", "参见死锁详情");
        }

        // 取第一条 SQL 作为 latest_sql
        if (!sqls.isEmpty()) {
            dl.put("latest_sql", sqls.get(sqls.size() - 1));
        }
    }

    /**
     * Mock 死锁检测结果
     */
    private String readMockDeadlockResult() {
        try {
            ObjectNode result = objectMapper.createObjectNode();

            ArrayNode deadlocks = result.putArray("deadlocks");
            ObjectNode dl = deadlocks.addObject();
            dl.put("time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            dl.put("rolled_back", "TRX-78901");
            ArrayNode trxArray = dl.putArray("transactions");
            trxArray.add("TRX-78901");
            trxArray.add("TRX-78902");
            dl.put("waiting_resource", "索引 `orders` 的主键行锁，事务 TRX-78901 等待 TRX-78902 释放锁");
            dl.put("latest_sql", "UPDATE orders SET status = 'SHIPPED' WHERE id = 1001");

            ArrayNode lockWaits = result.putArray("lock_waits");
            ObjectNode lw = lockWaits.addObject();
            lw.put("waiting_trx_id", "TRX-78901");
            lw.put("waiting_thread", 47);
            lw.put("wait_age", "00:00:12");
            lw.put("waiting_query", "UPDATE orders SET status = 'SHIPPED' WHERE id = 1001");
            lw.put("blocking_trx_id", "TRX-78902");
            lw.put("blocking_thread", 89);
            lw.put("blocking_query", "UPDATE orders SET status = 'PROCESSING' WHERE id = 1001");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"模拟死锁数据失败: " + e.getMessage() + "\"}";
        }
    }

    private String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /**
     * 预热数据库缓存
     * 对主要表执行 SELECT COUNT(*) 来预热 InnoDB Buffer Pool
     */
    public String warmUpCache() {
        try {
            List<ServiceConnection> mysqlConns = connectionService.findByType("mysql");
            if (mysqlConns.isEmpty()) {
                return "{\"success\": true, \"message\": \"Mock 模式：缓存预热已完成（模拟）\", \"warmed_tables\": [\"orders\", \"users\", \"payments\"]}";
            }

            ServiceConnection conn = mysqlConns.get(0);
            String url = "jdbc:mysql://" + conn.getHost() + ":" + conn.getPort()
                    + "/?useSSL=false&connectTimeout=5000";

            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode warmedTables = result.putArray("warmed_tables");
            int successCount = 0;

            try (Connection c = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword());
                 Statement stmt = c.createStatement()) {

                // 获取所有非系统表
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT TABLE_SCHEMA, TABLE_NAME FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA NOT IN ('mysql','performance_schema','information_schema','sys') "
                        + "AND TABLE_ROWS > 0 ORDER BY TABLE_ROWS DESC LIMIT 10")) {
                    while (rs.next()) {
                        String schema = rs.getString("TABLE_SCHEMA");
                        String table = rs.getString("TABLE_NAME");
                        try {
                            stmt.execute("SELECT COUNT(*) FROM `" + schema + "`.`" + table + "`");
                            warmedTables.add(schema + "." + table);
                            successCount++;
                        } catch (Exception e) {
                            log.debug("预热表失败: {}.{} - {}", schema, table, e.getMessage());
                        }
                    }
                }
            }

            result.put("success", true);
            result.put("message", "缓存预热完成，已预热 " + successCount + " 张表");
            result.put("warmed_count", successCount);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);

        } catch (Exception e) {
            log.error("缓存预热失败", e);
            return "{\"success\": false, \"message\": \"缓存预热失败: " + e.getMessage() + "\"}";
        }
    }

    /** 连接池使用率趋势数据缓存 */
    private final List<Map<String, Object>> poolTrendCache = new ArrayList<>();

    /**
     * 记录连接池使用率采样点
     */
    public void recordPoolSample(int usagePercent, int activeConnections) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        point.put("usage", usagePercent);
        point.put("active", activeConnections);
        poolTrendCache.add(point);
        if (poolTrendCache.size() > 20) {
            poolTrendCache.remove(0);
        }
    }

    /**
     * 获取连接池使用率趋势
     */
    public List<Map<String, Object>> getPoolTrend() {
        return new ArrayList<>(poolTrendCache);
    }

    /**
     * 对 SQL 执行 EXPLAIN FORMAT=JSON，分析执行计划
     */
    public String explainQuery(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "{\"error\": \"请提供要分析的 SQL\"}";
        }
        // 清理 SQL：去掉末尾分号，包装成 EXPLAIN
        String cleanSql = sql.trim().replaceAll(";*$", "");
        String explainSql = "EXPLAIN FORMAT=JSON " + cleanSql;

        try {
            List<ServiceConnection> mysqlConns = connectionService.findByType("mysql");
            if (mysqlConns.isEmpty()) {
                // Mock 模式：返回模拟数据
                return readMockExplainResult(cleanSql);
            }
            ServiceConnection conn = mysqlConns.get(0);
            String url = "jdbc:mysql://" + conn.getHost() + ":" + conn.getPort()
                    + "/?useSSL=false&connectTimeout=5000";

            try (Connection c = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword());
                 Statement stmt = c.createStatement();
                 ResultSet rs = stmt.executeQuery(explainSql)) {

                if (rs.next()) {
                    String jsonResult = rs.getString(1);
                    // 解析并提取关键信息
                    @SuppressWarnings("unchecked")
                    Map<String, Object> explainData = objectMapper.readValue(jsonResult, Map.class);
                    ObjectNode result = objectMapper.createObjectNode();
                    result.put("query", cleanSql);
                    result.set("explain", objectMapper.valueToTree(explainData));
                    result.put("analysis", analyzeExplainPlan(explainData));
                    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
                }
                return "{\"error\": \"EXPLAIN 未返回结果\"}";
            }
        } catch (Exception e) {
            log.error("EXPLAIN 分析失败: {}", sql, e);
            return "{\"error\": \"EXPLAIN 执行失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Mock EXPLAIN FORMAT=JSON 结果
     */
    private String readMockExplainResult(String sql) {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("query", sql);
            result.put("note", "模拟 EXPLAIN 结果（未配置真实 MySQL 连接）");

            ObjectNode explain = result.putObject("explain");
            ObjectNode queryBlock = explain.putObject("query_block");
            queryBlock.put("select_id", 1);
            queryBlock.putObject("cost_info").put("query_cost", "1050.23");
            ObjectNode table = queryBlock.putObject("table");
            table.put("table_name", "orders");
            table.put("access_type", "ALL");
            table.put("rows_examined_per_scan", 50000);
            table.put("rows_produced_per_join", 50000);
            table.put("filtered", "10.00");
            table.putObject("cost_info")
                    .put("read_cost", "1000.00")
                    .put("eval_cost", "50.23")
                    .put("prefix_cost", "1050.23");
            ArrayNode columns = table.putArray("used_columns");
            columns.add("id").add("user_id").add("amount").add("status").add("created_at");
            table.put("attached_condition", "(`orders`.`status` = 'PENDING')");
            table.put("Extra", "Using where; Using temporary; Using filesort");

            result.put("analysis", "⚠️ 全表扫描（ALL），扫描约 50000 行，建议为 WHERE 条件中的 status 字段和 ORDER BY 涉及的 created_at 字段添加联合索引");
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"模拟 EXPLAIN 失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 分析执行计划，生成可读的建议
     */
    private String analyzeExplainPlan(Map<String, Object> explainData) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> queryBlock = (Map<String, Object>) explainData.get("query_block");
            if (queryBlock == null) return "无法解析执行计划";

            StringBuilder analysis = new StringBuilder();

            // 递归分析所有 table
            analyzeTable(queryBlock, analysis, 0);

            return analysis.toString().trim();
        } catch (Exception e) {
            return "分析执行计划时出错: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private void analyzeTable(Map<String, Object> node, StringBuilder analysis, int depth) {
        // 检查当前层的 table
        Object tableObj = node.get("table");
        if (tableObj instanceof Map) {
            Map<String, Object> table = (Map<String, Object>) tableObj;
            String tableName = (String) table.getOrDefault("table_name", "unknown");
            String accessType = (String) table.getOrDefault("access_type", "unknown");
            Object rowsObj = table.get("rows_examined_per_scan");
            long rows = rowsObj instanceof Number ? ((Number) rowsObj).longValue() : 0;
            String extra = (String) table.getOrDefault("Extra", "");

            String indent = "  ".repeat(depth);
            analysis.append(indent).append("表: ").append(tableName).append("\n");
            analysis.append(indent).append("  - 访问类型: ").append(accessType).append("\n");
            analysis.append(indent).append("  - 扫描行数: ").append(rows).append("\n");

            if ("ALL".equalsIgnoreCase(accessType)) {
                analysis.append(indent).append("  ⚠️ 全表扫描，建议添加索引\n");
            } else if ("INDEX".equalsIgnoreCase(accessType)) {
                analysis.append(indent).append("  ⚠️ 索引扫描，仍有优化空间\n");
            } else if ("RANGE".equalsIgnoreCase(accessType)) {
                analysis.append(indent).append("  ✅ 范围扫描，索引使用良好\n");
            } else if ("REF".equalsIgnoreCase(accessType) || "EQ_REF".equalsIgnoreCase(accessType)) {
                analysis.append(indent).append("  ✅ 非唯一/唯一索引查找，性能良好\n");
            } else if ("CONST".equalsIgnoreCase(accessType) || "SYSTEM".equalsIgnoreCase(accessType)) {
                analysis.append(indent).append("  ✅ 常量查找，最优\n");
            }

            if (extra != null && !extra.isEmpty()) {
                if (extra.contains("Using temporary")) {
                    analysis.append(indent).append("  ⚠️ 使用了临时表，建议优化 GROUP BY / DISTINCT 索引\n");
                }
                if (extra.contains("Using filesort")) {
                    analysis.append(indent).append("  ⚠️ 使用了文件排序，建议为 ORDER BY 字段添加索引\n");
                }
                if (extra.contains("Using index")) {
                    analysis.append(indent).append("  ✅ 覆盖索引，无需回表\n");
                }
            }
        }

        // 递归检查子查询 (nested_loop, union, etc.)
        Object nestedLoopObj = node.get("nested_loop");
        if (nestedLoopObj instanceof Iterable) {
            for (Object item : (Iterable<Object>) nestedLoopObj) {
                if (item instanceof Map) {
                    analyzeTable((Map<String, Object>) item, analysis, depth + 1);
                }
            }
        }
    }

    /**
     * Kill 一个 MySQL 连接
     */
    public String killQuery(Long connectionId) {
        return executeSafeSQL("KILL " + connectionId);
    }

    /** 安全 SQL 白名单正则 */
    private static final Pattern[] SAFE_SQL_PATTERNS = {
        Pattern.compile("^ALTER\\s+TABLE\\s+`?\\w+`?(?:\\.`?\\w+`?)?\\s+ADD\\s+(INDEX|KEY|PRIMARY\\s+KEY|UNIQUE|CONSTRAINT)\\s", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^ANALYZE\\s+TABLE\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^OPTIMIZE\\s+TABLE\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^KILL\\s+\\d+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^DROP\\s+TABLE\\s+IF\\s+EXISTS\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^CREATE\\s+TABLE\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^INSERT\\s+INTO\\s+", Pattern.CASE_INSENSITIVE),
    };

    /**
     * 安全地执行 SQL（只允许白名单内的操作）
     * @param sql 要执行的 SQL
     * @return 执行结果 JSON
     */
    public String executeSafeSQL(String sql) {
        boolean allowed = false;
        for (Pattern p : SAFE_SQL_PATTERNS) {
            if (p.matcher(sql.trim()).find()) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            log.warn("SQL 被拦截（不在白名单中）: {}", sql);
            return "{\"success\": false, \"message\": \"SQL 被安全策略拦截，只允许: ADD INDEX/KEY/PK, ANALYZE, OPTIMIZE, KILL, DROP TABLE, CREATE TABLE, INSERT\"}";
        }

        try {
            List<ServiceConnection> mysqlConns = connectionService.findByType("mysql");
            if (mysqlConns.isEmpty()) {
                return "{\"success\": false, \"message\": \"未配置 MySQL 连接\"}";
            }
            ServiceConnection conn = mysqlConns.get(0);
            String url = "jdbc:mysql://" + conn.getHost() + ":" + conn.getPort()
                    + "/?useSSL=false&connectTimeout=5000";

            try (Connection c = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword());
                 Statement stmt = c.createStatement()) {
                boolean isResultSet = stmt.execute(sql);
                if (isResultSet) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        StringBuilder result = new StringBuilder("{\"success\": true, \"message\": \"查询已执行\", \"rows\": [");
                        boolean first = true;
                        int colCount = rs.getMetaData().getColumnCount();
                        while (rs.next()) {
                            if (!first) result.append(",");
                            first = false;
                            result.append("{");
                            for (int i = 1; i <= colCount; i++) {
                                if (i > 1) result.append(",");
                                result.append("\"").append(rs.getMetaData().getColumnLabel(i)).append("\":\"")
                                      .append(rs.getString(i) != null ? rs.getString(i).replace("\"", "\\\"") : "").append("\"");
                            }
                            result.append("}");
                        }
                        result.append("]}");
                        return result.toString();
                    }
                } else {
                    int affected = stmt.getUpdateCount();
                    log.info("SQL 执行成功: {}, 影响行数={}", sql, affected);
                    return String.format("{\"success\": true, \"message\": \"SQL 已执行成功，影响 %d 行\", \"affected_rows\": %d}", affected, affected);
                }
            }
        } catch (Exception e) {
            log.error("SQL 执行失败: {}", sql, e);
            return String.format("{\"success\": false, \"message\": \"执行失败: %s\"}", e.getMessage());
        }
    }
}
