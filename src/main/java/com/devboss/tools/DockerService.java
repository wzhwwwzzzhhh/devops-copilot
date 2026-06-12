package com.devboss.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerService.class);
    private final ObjectMapper objectMapper;

    public DockerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 通过 ProcessBuilder 执行 docker CLI 命令，返回 stdout 字符串
     */
    private String executeCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("docker 命令失败, exit=" + exitCode + ": " + output);
            }
            return output;
        } catch (Exception e) {
            throw new RuntimeException("执行 docker 命令异常: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 docker CLI 的 JSON Lines 输出（每行一个 JSON 对象）为 JsonNode 数组
     */
    private List<JsonNode> parseJsonLines(String output) {
        List<JsonNode> result = new ArrayList<>();
        try {
            String[] lines = output.split("\\r?\\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    result.add(objectMapper.readTree(line));
                }
            }
        } catch (Exception e) {
            log.warn("解析 docker JSON Lines 失败", e);
        }
        return result;
    }

    // ==================== 公开 API ====================

    /**
     * 获取所有容器（包含已停止）
     */
    public String getContainers() {
        try {
            String output = executeCommand("docker", "ps", "-a", "--format", "{{json .}}");
            List<JsonNode> items = parseJsonLines(output);
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode containers = result.putArray("containers");
            for (JsonNode item : items) {
                ObjectNode c = containers.addObject();
                c.put("ID", item.path("ID").asText(""));
                c.put("Image", item.path("Image").asText(""));
                c.put("Command", item.path("Command").asText(""));
                c.put("CreatedAt", item.path("CreatedAt").asText(""));
                c.put("Status", item.path("Status").asText(""));
                c.put("Ports", item.path("Ports").asText(""));
                c.put("Names", item.path("Names").asText(""));
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("获取容器列表失败 (docker CLI 不可用), 降级 Mock 数据: {}", e.getMessage());
            return getMockContainers();
        }
    }

    /**
     * 获取运行中的容器
     */
    public String getRunningContainers() {
        try {
            String output = executeCommand("docker", "ps", "--format", "{{json .}}");
            List<JsonNode> items = parseJsonLines(output);
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode containers = result.putArray("containers");
            for (JsonNode item : items) {
                ObjectNode c = containers.addObject();
                c.put("ID", item.path("ID").asText(""));
                c.put("Image", item.path("Image").asText(""));
                c.put("Command", item.path("Command").asText(""));
                c.put("CreatedAt", item.path("CreatedAt").asText(""));
                c.put("Status", item.path("Status").asText(""));
                c.put("Ports", item.path("Ports").asText(""));
                c.put("Names", item.path("Names").asText(""));
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("获取运行中容器失败 (docker CLI 不可用), 降级 Mock 数据: {}", e.getMessage());
            return getMockRunningContainers();
        }
    }

    /**
     * 获取容器资源使用统计
     */
    public String getContainerStats() {
        try {
            String output = executeCommand("docker", "stats", "--no-stream", "--format", "{{json .}}");
            List<JsonNode> items = parseJsonLines(output);
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode stats = result.putArray("stats");
            for (JsonNode item : items) {
                ObjectNode s = stats.addObject();
                s.put("Container", item.path("Container").asText(""));
                s.put("Name", item.path("Name").asText(""));
                s.put("CPUPerc", item.path("CPUPerc").asText(""));
                s.put("MemPerc", item.path("MemPerc").asText(""));
                s.put("MemUsage", item.path("MemUsage").asText(""));
                s.put("NetIO", item.path("NetIO").asText(""));
                s.put("BlockIO", item.path("BlockIO").asText(""));
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("获取容器资源统计失败 (docker CLI 不可用), 降级 Mock 数据: {}", e.getMessage());
            return getMockContainerStats();
        }
    }

    /**
     * 获取镜像列表
     */
    public String getImages() {
        try {
            String output = executeCommand("docker", "images", "--format", "{{json .}}");
            List<JsonNode> items = parseJsonLines(output);
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode images = result.putArray("images");
            for (JsonNode item : items) {
                ObjectNode img = images.addObject();
                img.put("Repository", item.path("Repository").asText(""));
                img.put("Tag", item.path("Tag").asText(""));
                img.put("ImageID", item.path("ImageID").asText(""));
                img.put("CreatedAt", item.path("CreatedAt").asText(""));
                img.put("Size", item.path("Size").asText(""));
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("获取镜像列表失败 (docker CLI 不可用), 降级 Mock 数据: {}", e.getMessage());
            return getMockImages();
        }
    }

    /**
     * 获取 Docker 全量状态（容器 + 镜像 + 统计摘要）
     */
    public String getFullStatus() {
        try {
            ObjectNode result = objectMapper.createObjectNode();

            // 获取容器列表
            String containersJson = getContainers();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> containersMap = objectMapper.readValue(containersJson, java.util.Map.class);
            result.set("containers", objectMapper.valueToTree(containersMap.get("containers")));

            // 获取镜像列表
            String imagesJson = getImages();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> imagesMap = objectMapper.readValue(imagesJson, java.util.Map.class);
            result.set("images", objectMapper.valueToTree(imagesMap.get("images")));

            // 获取资源统计
            String statsJson = getContainerStats();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> statsMap = objectMapper.readValue(statsJson, java.util.Map.class);
            result.set("stats", objectMapper.valueToTree(statsMap.get("stats")));

            // 统计摘要
            ArrayNode containers = (ArrayNode) result.get("containers");
            ArrayNode images = (ArrayNode) result.get("images");
            int running = 0;
            int stopped = 0;
            if (containers != null) {
                for (Iterator<JsonNode> it = containers.elements(); it.hasNext(); ) {
                    JsonNode c = it.next();
                    String status = c.path("Status").asText("");
                    if (status.startsWith("Up") || status.contains("running")) {
                        running++;
                    } else {
                        stopped++;
                    }
                }
            }
            result.put("running_containers", running);
            result.put("stopped_containers", stopped);
            result.put("total_containers", containers != null ? containers.size() : 0);
            result.put("total_images", images != null ? images.size() : 0);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("获取 Docker 全量状态失败", e);
            return "{\"error\": \"获取 Docker 全量状态失败: " + e.getMessage() + "\"}";
        }
    }

    // ==================== Mock Data ====================

    private String getMockContainers() {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode containers = result.putArray("containers");

            ObjectNode c1 = containers.addObject();
            c1.put("ID", "a1b2c3d4e5f6");
            c1.put("Image", "nginx:alpine");
            c1.put("Command", "\"/docker-entrypoint.sh\"");
            c1.put("CreatedAt", "2026-06-10 08:30:00");
            c1.put("Status", "Up 2 days");
            c1.put("Ports", "0.0.0.0:80->80/tcp");
            c1.put("Names", "nginx");

            ObjectNode c2 = containers.addObject();
            c2.put("ID", "b2c3d4e5f6a7");
            c2.put("Image", "myapp:latest");
            c2.put("Command", "\"java -jar app.jar\"");
            c2.put("CreatedAt", "2026-06-09 14:20:00");
            c2.put("Status", "Up 3 days");
            c2.put("Ports", "0.0.0.0:8080->8080/tcp");
            c2.put("Names", "app");

            ObjectNode c3 = containers.addObject();
            c3.put("ID", "c3d4e5f6a7b8");
            c3.put("Image", "mysql:8.0");
            c3.put("Command", "\"docker-entrypoint.sh\"");
            c3.put("CreatedAt", "2026-06-08 10:00:00");
            c3.put("Status", "Up 4 days");
            c3.put("Ports", "0.0.0.0:3306->3306/tcp");
            c3.put("Names", "mysql");

            ObjectNode c4 = containers.addObject();
            c4.put("ID", "d4e5f6a7b8c9");
            c4.put("Image", "redis:7-alpine");
            c4.put("Command", "\"redis-server\"");
            c4.put("CreatedAt", "2026-06-07 09:15:00");
            c4.put("Status", "Up 5 days");
            c4.put("Ports", "0.0.0.0:6379->6379/tcp");
            c4.put("Names", "redis");

            ObjectNode c5 = containers.addObject();
            c5.put("ID", "e5f6a7b8c9d0");
            c5.put("Image", "old-app:v1");
            c5.put("Command", "\"npm start\"");
            c5.put("CreatedAt", "2026-05-20 11:00:00");
            c5.put("Status", "Exited (0) 2 weeks ago");
            c5.put("Ports", "");
            c5.put("Names", "old-app");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("生成 Mock 容器列表失败", e);
            return "{\"error\": \"生成 Mock 容器列表失败: " + e.getMessage() + "\"}";
        }
    }

    private String getMockRunningContainers() {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode containers = result.putArray("containers");

            ObjectNode c1 = containers.addObject();
            c1.put("ID", "a1b2c3d4e5f6");
            c1.put("Image", "nginx:alpine");
            c1.put("Command", "\"/docker-entrypoint.sh\"");
            c1.put("CreatedAt", "2026-06-10 08:30:00");
            c1.put("Status", "Up 2 days");
            c1.put("Ports", "0.0.0.0:80->80/tcp");
            c1.put("Names", "nginx");

            ObjectNode c2 = containers.addObject();
            c2.put("ID", "b2c3d4e5f6a7");
            c2.put("Image", "myapp:latest");
            c2.put("Command", "\"java -jar app.jar\"");
            c2.put("CreatedAt", "2026-06-09 14:20:00");
            c2.put("Status", "Up 3 days");
            c2.put("Ports", "0.0.0.0:8080->8080/tcp");
            c2.put("Names", "app");

            ObjectNode c3 = containers.addObject();
            c3.put("ID", "c3d4e5f6a7b8");
            c3.put("Image", "mysql:8.0");
            c3.put("Command", "\"docker-entrypoint.sh\"");
            c3.put("CreatedAt", "2026-06-08 10:00:00");
            c3.put("Status", "Up 4 days");
            c3.put("Ports", "0.0.0.0:3306->3306/tcp");
            c3.put("Names", "mysql");

            ObjectNode c4 = containers.addObject();
            c4.put("ID", "d4e5f6a7b8c9");
            c4.put("Image", "redis:7-alpine");
            c4.put("Command", "\"redis-server\"");
            c4.put("CreatedAt", "2026-06-07 09:15:00");
            c4.put("Status", "Up 5 days");
            c4.put("Ports", "0.0.0.0:6379->6379/tcp");
            c4.put("Names", "redis");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("生成 Mock 运行中容器列表失败", e);
            return "{\"error\": \"生成 Mock 运行中容器列表失败: " + e.getMessage() + "\"}";
        }
    }

    private String getMockContainerStats() {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode stats = result.putArray("stats");

            ObjectNode s1 = stats.addObject();
            s1.put("Container", "a1b2c3d4e5f6");
            s1.put("Name", "nginx");
            s1.put("CPUPerc", "2.50%");
            s1.put("MemPerc", "0.25%");
            s1.put("MemUsage", "45.2MiB / 15.6GiB");
            s1.put("NetIO", "1.5GB / 3.2GB");
            s1.put("BlockIO", "12MB / 4.5MB");

            ObjectNode s2 = stats.addObject();
            s2.put("Container", "b2c3d4e5f6a7");
            s2.put("Name", "app");
            s2.put("CPUPerc", "12.30%");
            s2.put("MemPerc", "3.28%");
            s2.put("MemUsage", "512.0MiB / 15.6GiB");
            s2.put("NetIO", "8.2GB / 15.6GB");
            s2.put("BlockIO", "256MB / 128MB");

            ObjectNode s3 = stats.addObject();
            s3.put("Container", "c3d4e5f6a7b8");
            s3.put("Name", "mysql");
            s3.put("CPUPerc", "8.10%");
            s3.put("MemPerc", "7.69%");
            s3.put("MemUsage", "1.2GiB / 15.6GiB");
            s3.put("NetIO", "4.8GB / 2.1GB");
            s3.put("BlockIO", "1.2GB / 890MB");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("生成 Mock 容器资源统计失败", e);
            return "{\"error\": \"生成 Mock 容器资源统计失败: " + e.getMessage() + "\"}";
        }
    }

    private String getMockImages() {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode images = result.putArray("images");

            ObjectNode img1 = images.addObject();
            img1.put("Repository", "nginx");
            img1.put("Tag", "alpine");
            img1.put("ImageID", "sha256:a1b2c3d4e5f6");
            img1.put("CreatedAt", "2026-05-15 10:00:00");
            img1.put("Size", "142MB");

            ObjectNode img2 = images.addObject();
            img2.put("Repository", "myapp");
            img2.put("Tag", "latest");
            img2.put("ImageID", "sha256:b2c3d4e5f6a7");
            img2.put("CreatedAt", "2026-06-01 08:30:00");
            img2.put("Size", "856MB");

            ObjectNode img3 = images.addObject();
            img3.put("Repository", "redis");
            img3.put("Tag", "7-alpine");
            img3.put("ImageID", "sha256:c3d4e5f6a7b8");
            img3.put("CreatedAt", "2026-04-20 14:00:00");
            img3.put("Size", "32.1MB");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("生成 Mock 镜像列表失败", e);
            return "{\"error\": \"生成 Mock 镜像列表失败: " + e.getMessage() + "\"}";
        }
    }
}
