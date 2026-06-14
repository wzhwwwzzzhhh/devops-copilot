package com.devboss.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 系统资源监控：CPU、内存、磁盘、网络、进程等 */
@Service
public class SystemMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SystemMonitorService.class);
    private final ObjectMapper objectMapper;

    public SystemMonitorService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 获取 CPU 信息（Mock 数据）
     * 包含使用率、负载均值、核心数、TOP 5 CPU 进程
     */
    public String getCpuInfo() {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("usage_percent", 45.2);
            result.put("load_1min", 2.15);
            result.put("load_5min", 1.89);
            result.put("load_15min", 1.76);
            result.put("core_count", 8);

            ArrayNode topProcesses = result.putArray("top_processes");
            addMockProcess(topProcesses, 1234, "java", 12.5, 8.2, 256000, "appuser", "/opt/app/bin/java -Xmx2g -jar app.jar");
            addMockProcess(topProcesses, 5678, "nginx", 8.1, 1.5, 48000, "www-data", "nginx: worker process");
            addMockProcess(topProcesses, 9012, "mysql", 6.3, 12.8, 384000, "mysql", "/usr/sbin/mysqld --basedir=/usr");
            addMockProcess(topProcesses, 3456, "redis", 3.7, 2.1, 64000, "redis", "redis-server *:6379");
            addMockProcess(topProcesses, 7890, "python", 2.8, 0.9, 28000, "deploy", "python3 /opt/scripts/health_check.py");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("获取 CPU 信息失败", e);
            return "{\"error\": \"获取 CPU 信息失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取内存信息（Mock 数据）
     * 32GB 总量，使用 18.5GB，可用 13.5GB
     */
    public String getMemoryInfo() {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("total", "32G");
            result.put("used", "18.5G");
            result.put("available", "13.5G");
            result.put("swap_total", "4G");
            result.put("swap_used", "0.5G");
            result.put("usage_percent", 57.8);
            result.put("total_bytes", 34359738368L);
            result.put("used_bytes", 19864223744L);
            result.put("available_bytes", 14495514624L);
            result.put("swap_total_bytes", 4294967296L);
            result.put("swap_used_bytes", 536870912L);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("获取内存信息失败", e);
            return "{\"error\": \"获取内存信息失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取磁盘信息（Mock 数据）
     * 4 个挂载点：/, /data, /var, /tmp
     */
    public String getDiskInfo() {
        try {
            ArrayNode disks = objectMapper.createArrayNode();

            ObjectNode root = disks.addObject();
            root.put("filesystem", "/dev/sda2");
            root.put("mounted_on", "/");
            root.put("total", "100G");
            root.put("used", "65G");
            root.put("avail", "35G");
            root.put("use_percent", 65.0);

            ObjectNode data = disks.addObject();
            data.put("filesystem", "/dev/sdb1");
            data.put("mounted_on", "/data");
            data.put("total", "500G");
            data.put("used", "410G");
            data.put("avail", "90G");
            data.put("use_percent", 82.0);

            ObjectNode var = disks.addObject();
            var.put("filesystem", "/dev/sda3");
            var.put("mounted_on", "/var");
            var.put("total", "50G");
            var.put("used", "22G");
            var.put("avail", "28G");
            var.put("use_percent", 44.0);

            ObjectNode tmp = disks.addObject();
            tmp.put("filesystem", "tmpfs");
            tmp.put("mounted_on", "/tmp");
            tmp.put("total", "16G");
            tmp.put("used", "1.2G");
            tmp.put("avail", "14.8G");
            tmp.put("use_percent", 7.5);

            ObjectNode result = objectMapper.createObjectNode();
            result.set("disks", disks);
            result.put("warning", "/data 磁盘使用率达到 82%，超过 80% 阈值");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("获取磁盘信息失败", e);
            return "{\"error\": \"获取磁盘信息失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取网络信息（Mock 数据）
     * 3 个接口：eth0, eth1, lo
     */
    public String getNetworkInfo() {
        try {
            ArrayNode interfaces = objectMapper.createArrayNode();

            ObjectNode eth0 = interfaces.addObject();
            eth0.put("interface", "eth0");
            eth0.put("rx_bytes", 158726348912L);
            eth0.put("tx_bytes", 89234156789L);
            eth0.put("rx_packets", 120456789L);
            eth0.put("tx_packets", 85432100L);
            eth0.put("errors", 0);
            eth0.put("drops", 12);

            ObjectNode eth1 = interfaces.addObject();
            eth1.put("interface", "eth1");
            eth1.put("rx_bytes", 23456789123L);
            eth1.put("tx_bytes", 12345678901L);
            eth1.put("rx_packets", 23456789L);
            eth1.put("tx_packets", 12345678L);
            eth1.put("errors", 3);
            eth1.put("drops", 45);

            ObjectNode lo = interfaces.addObject();
            lo.put("interface", "lo");
            lo.put("rx_bytes", 567891234L);
            lo.put("tx_bytes", 567891234L);
            lo.put("rx_packets", 567890L);
            lo.put("tx_packets", 567890L);
            lo.put("errors", 0);
            lo.put("drops", 0);

            ObjectNode result = objectMapper.createObjectNode();
            result.set("interfaces", interfaces);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("获取网络信息失败", e);
            return "{\"error\": \"获取网络信息失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取 TOP 10 进程（Mock 数据）
     */
    public String getTopProcesses() {
        try {
            ArrayNode processes = objectMapper.createArrayNode();

            addMockProcess(processes, 1234, "java", 12.5, 8.2, 256000, "appuser", "/opt/app/bin/java -Xmx2g -jar app.jar");
            addMockProcess(processes, 5678, "nginx", 8.1, 1.5, 48000, "www-data", "nginx: worker process");
            addMockProcess(processes, 9012, "mysql", 6.3, 12.8, 384000, "mysql", "/usr/sbin/mysqld --basedir=/usr");
            addMockProcess(processes, 3456, "redis", 3.7, 2.1, 64000, "redis", "redis-server *:6379");
            addMockProcess(processes, 7890, "python", 2.8, 0.9, 28000, "deploy", "python3 /opt/scripts/health_check.py");
            addMockProcess(processes, 1111, "node", 2.1, 1.8, 72000, "nodeuser", "node /opt/web/server.js");
            addMockProcess(processes, 2222, "sshd", 1.5, 0.3, 9600, "root", "sshd: root@pts/0");
            addMockProcess(processes, 3333, "prometheus", 1.2, 0.6, 32000, "prom", "/usr/bin/prometheus --config.file=/etc/prometheus/prometheus.yml");
            addMockProcess(processes, 4444, "grafana", 0.9, 1.1, 44000, "grafana", "/usr/sbin/grafana-server --config=/etc/grafana/grafana.ini");
            addMockProcess(processes, 5555, "cron", 0.3, 0.1, 3200, "root", "/usr/sbin/cron -f");

            ObjectNode result = objectMapper.createObjectNode();
            result.set("processes", processes);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("获取进程信息失败", e);
            return "{\"error\": \"获取进程信息失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取全量系统状态
     * 组合 CPU + 内存 + 磁盘 + 网络 + 进程
     */
    public String getFullStatus() {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("hostname", "dev-server-01");
            result.put("os", "Linux 5.15.0-91-generic x86_64");
            result.put("uptime", "15 days, 7 hours, 32 minutes");

            // CPU
            ObjectNode cpu = result.putObject("cpu");
            cpu.put("usage_percent", 45.2);
            cpu.put("load_1min", 2.15);
            cpu.put("load_5min", 1.89);
            cpu.put("load_15min", 1.76);
            cpu.put("core_count", 8);
            ArrayNode topProcesses = cpu.putArray("top_processes");
            addMockProcess(topProcesses, 1234, "java", 12.5, 8.2, 256000, "appuser", "/opt/app/bin/java -Xmx2g -jar app.jar");
            addMockProcess(topProcesses, 5678, "nginx", 8.1, 1.5, 48000, "www-data", "nginx: worker process");
            addMockProcess(topProcesses, 9012, "mysql", 6.3, 12.8, 384000, "mysql", "/usr/sbin/mysqld --basedir=/usr");

            // Memory
            ObjectNode memory = result.putObject("memory");
            memory.put("total", "32G");
            memory.put("used", "18.5G");
            memory.put("available", "13.5G");
            memory.put("swap_total", "4G");
            memory.put("swap_used", "0.5G");
            memory.put("usage_percent", 57.8);
            memory.put("total_bytes", 34359738368L);
            memory.put("used_bytes", 19864223744L);
            memory.put("available_bytes", 14495514624L);
            memory.put("swap_total_bytes", 4294967296L);
            memory.put("swap_used_bytes", 536870912L);

            // Disk
            ArrayNode disks = result.putArray("disks");
            ObjectNode root = disks.addObject();
            root.put("filesystem", "/dev/sda2");
            root.put("mounted_on", "/");
            root.put("total", "100G");
            root.put("used", "65G");
            root.put("avail", "35G");
            root.put("use_percent", 65.0);
            ObjectNode data = disks.addObject();
            data.put("filesystem", "/dev/sdb1");
            data.put("mounted_on", "/data");
            data.put("total", "500G");
            data.put("used", "410G");
            data.put("avail", "90G");
            data.put("use_percent", 82.0);
            ObjectNode var = disks.addObject();
            var.put("filesystem", "/dev/sda3");
            var.put("mounted_on", "/var");
            var.put("total", "50G");
            var.put("used", "22G");
            var.put("avail", "28G");
            var.put("use_percent", 44.0);
            ObjectNode tmp = disks.addObject();
            tmp.put("filesystem", "tmpfs");
            tmp.put("mounted_on", "/tmp");
            tmp.put("total", "16G");
            tmp.put("used", "1.2G");
            tmp.put("avail", "14.8G");
            tmp.put("use_percent", 7.5);

            // Network
            ArrayNode interfaces = result.putArray("interfaces");
            ObjectNode eth0 = interfaces.addObject();
            eth0.put("interface", "eth0");
            eth0.put("rx_bytes", 158726348912L);
            eth0.put("tx_bytes", 89234156789L);
            eth0.put("rx_packets", 120456789L);
            eth0.put("tx_packets", 85432100L);
            eth0.put("errors", 0);
            eth0.put("drops", 12);
            ObjectNode eth1 = interfaces.addObject();
            eth1.put("interface", "eth1");
            eth1.put("rx_bytes", 23456789123L);
            eth1.put("tx_bytes", 12345678901L);
            eth1.put("rx_packets", 23456789L);
            eth1.put("tx_packets", 12345678L);
            eth1.put("errors", 3);
            eth1.put("drops", 45);
            ObjectNode lo = interfaces.addObject();
            lo.put("interface", "lo");
            lo.put("rx_bytes", 567891234L);
            lo.put("tx_bytes", 567891234L);
            lo.put("rx_packets", 567890L);
            lo.put("tx_packets", 567890L);
            lo.put("errors", 0);
            lo.put("drops", 0);

            // Processes
            ArrayNode processes = result.putArray("processes");
            addMockProcess(processes, 1234, "java", 12.5, 8.2, 256000, "appuser", "/opt/app/bin/java -Xmx2g -jar app.jar");
            addMockProcess(processes, 5678, "nginx", 8.1, 1.5, 48000, "www-data", "nginx: worker process");
            addMockProcess(processes, 9012, "mysql", 6.3, 12.8, 384000, "mysql", "/usr/sbin/mysqld --basedir=/usr");
            addMockProcess(processes, 3456, "redis", 3.7, 2.1, 64000, "redis", "redis-server *:6379");
            addMockProcess(processes, 7890, "python", 2.8, 0.9, 28000, "deploy", "python3 /opt/scripts/health_check.py");
            addMockProcess(processes, 1111, "node", 2.1, 1.8, 72000, "nodeuser", "node /opt/web/server.js");
            addMockProcess(processes, 2222, "sshd", 1.5, 0.3, 9600, "root", "sshd: root@pts/0");
            addMockProcess(processes, 3333, "prometheus", 1.2, 0.6, 32000, "prom", "/usr/bin/prometheus --config.file=/etc/prometheus/prometheus.yml");
            addMockProcess(processes, 4444, "grafana", 0.9, 1.1, 44000, "grafana", "/usr/sbin/grafana-server --config=/etc/grafana/grafana.ini");
            addMockProcess(processes, 5555, "cron", 0.3, 0.1, 3200, "root", "/usr/sbin/cron -f");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("获取系统状态失败", e);
            return "{\"error\": \"获取系统状态失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 向 ArrayNode 中添加一条模拟进程数据
     */
    private void addMockProcess(ArrayNode array, int pid, String name, double cpuPercent,
                                double memPercent, int rss, String user, String command) {
        ObjectNode proc = array.addObject();
        proc.put("pid", pid);
        proc.put("name", name);
        proc.put("cpu_percent", cpuPercent);
        proc.put("mem_percent", memPercent);
        proc.put("rss", rss);
        proc.put("user", user);
        proc.put("command", command);
    }
}
