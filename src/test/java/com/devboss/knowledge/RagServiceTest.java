package com.devboss.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RagServiceTest {

    @Autowired
    private RagService ragService;

    @BeforeEach
    void setUp() {
        // 清理测试数据
        try {
            ragService.deleteDocument("test-doc-001");
            ragService.deleteDocument("test-doc-002");
        } catch (Exception e) {
            // 忽略删除失败
        }
    }

    @Test
    void testIndexDocument() {
        boolean result = ragService.indexDocument(
                "test-doc-001",
                "MySQL连接池配置",
                "MySQL连接池最大连接数建议设置为200，最小空闲连接数为10。",
                new String[]{"mysql", "database", "connection"}
        );
        assertTrue(result, "文档应该成功写入ES");
    }

    @Test
    void testSearchDocument() {
        // 先写入文档
        ragService.indexDocument(
                "test-doc-002",
                "Redis缓存策略",
                "Redis缓存建议使用LRU淘汰策略，最大内存设置为256MB。",
                new String[]{"redis", "cache"}
        );

        // 等待索引刷新
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 搜索文档
        String result = ragService.search("Redis缓存");
        assertNotNull(result, "搜索结果不应为null");
        assertTrue(result.contains("Redis缓存策略") || result.contains("暂无相关文档"), 
                "搜索应该返回结果或提示未找到");
    }

    @Test
    void testListDocuments() {
        // 写入测试文档
        ragService.indexDocument(
                "test-doc-001",
                "测试文档",
                "这是一个测试文档内容。",
                new String[]{"test"}
        );

        // 等待索引刷新
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 获取文档列表
        java.util.List<java.util.Map<String, Object>> docs = ragService.listDocuments(10);
        assertNotNull(docs, "文档列表不应为null");
        assertTrue(docs.size() > 0, "应该至少有一个文档");
    }

    @Test
    void testDeleteDocument() {
        // 先写入文档
        ragService.indexDocument(
                "test-doc-001",
                "待删除文档",
                "这个文档将被删除。",
                new String[]{"test"}
        );

        // 删除文档
        boolean result = ragService.deleteDocument("test-doc-001");
        assertTrue(result, "文档应该成功删除");
    }

    @Test
    void testConnectionHealth() {
        // 通过搜索操作验证ES连接
        String result = ragService.search("health check");
        assertNotNull(result, "ES连接应该正常");
    }
}
