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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
