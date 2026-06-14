package com.devboss.controller;

import com.devboss.entity.ServiceConnection;
import com.devboss.service.ServiceConnectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务连接管理接口：添加、删除、测试远程连接配置
 */
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private static final Logger log = LoggerFactory.getLogger(ConnectionController.class);

    private final ServiceConnectionService service;

    public ConnectionController(ServiceConnectionService service) {
        this.service = service;
    }

    @GetMapping
    public List<ServiceConnection> list(@RequestParam(required = false) String type) {
        if (type != null && !type.isEmpty()) {
            return service.findByType(type);
        }
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceConnection> get(@PathVariable Long id) {
        ServiceConnection conn = service.findById(id);
        if (conn == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(conn);
    }

    @PostMapping
    public ServiceConnection create(@RequestBody ServiceConnection connection) {
        log.info("注册服务连接: name={}, type={}, host={}", connection.getName(), connection.getType(), connection.getHost());
        return service.create(connection);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceConnection> update(@PathVariable Long id, @RequestBody ServiceConnection connection) {
        ServiceConnection updated = service.update(id, connection);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/services")
    public List<String> serviceNames() {
        return service.getServiceNames();
    }

    @GetMapping("/types")
    public List<Map<String, String>> types() {
        return List.of(
                Map.of("type", "mysql", "desc", "MySQL 数据库"),
                Map.of("type", "redis", "desc", "Redis 缓存"),
                Map.of("type", "es", "desc", "Elasticsearch 搜索引擎"),
                Map.of("type", "prometheus", "desc", "Prometheus 监控系统"),
                Map.of("type", "k8s", "desc", "Kubernetes 集群"),
                Map.of("type", "log", "desc", "日志文件路径"),
                Map.of("type", "service", "desc", "微服务")
        );
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody ServiceConnection connection) {
        Map<String, Object> result = service.testConnection(connection);
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }
}
