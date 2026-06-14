package com.devboss.service;

import com.devboss.entity.ServiceConnection;
import com.devboss.repository.ServiceConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;

/** 服务连接管理：管理各类中间件的连接配置与健康检查 */
@Service
public class ServiceConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ServiceConnectionService.class);

    private final ServiceConnectionRepository repository;

    public ServiceConnectionService(ServiceConnectionRepository repository) {
        this.repository = repository;
    }

    public List<ServiceConnection> findAll() {
        return repository.findAll();
    }

    public List<ServiceConnection> findByType(String type) {
        return repository.findByTypeAndStatus(type, "ACTIVE");
    }

    public List<ServiceConnection> findByTag(String tag) {
        return repository.findByTagsContaining(tag);
    }

    public ServiceConnection findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public ServiceConnection create(ServiceConnection connection) {
        ServiceConnection saved = repository.save(connection);
        log.info("服务连接已注册: id={}, name={}, type={}", saved.getId(), saved.getName(), saved.getType());
        return saved;
    }

    public ServiceConnection update(Long id, ServiceConnection updated) {
        ServiceConnection existing = repository.findById(id).orElse(null);
        if (existing == null) return null;
        existing.setName(updated.getName());
        existing.setType(updated.getType());
        existing.setHost(updated.getHost());
        existing.setPort(updated.getPort());
        existing.setUsername(updated.getUsername());
        existing.setPassword(updated.getPassword());
        existing.setProperties(updated.getProperties());
        existing.setTags(updated.getTags());
        existing.setStatus(updated.getStatus());
        ServiceConnection saved = repository.save(existing);
        log.info("服务连接已更新: id={}", id);
        return saved;
    }

    public void delete(Long id) {
        repository.deleteById(id);
        log.info("服务连接已删除: id={}", id);
    }

    public List<String> getServiceNames() {
        List<ServiceConnection> services = repository.findByTypeAndStatus("service", "ACTIVE");
        return services.stream().map(ServiceConnection::getName).toList();
    }

    public Map<String, Object> testConnection(ServiceConnection connection) {
        String host = connection.getHost();
        int port = connection.getPort();
        int timeout = 3000;

        if (host == null || host.isEmpty() || port <= 0) {
            return Map.of("success", false, "message", "地址和端口不能为空");
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            log.info("连接测试成功: host={}, port={}", host, port);
            return Map.of("success", true, "message", "连接成功", "latency", "ok");
        } catch (Exception e) {
            log.warn("连接测试失败: host={}, port={}, error={}", host, port, e.getMessage());
            return Map.of("success", false, "message", "连接失败: " + e.getMessage());
        }
    }
}
