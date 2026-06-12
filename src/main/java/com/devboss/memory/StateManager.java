package com.devboss.memory;

import com.devboss.agent.InvestigationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 状态管理器
 * 负责管理会话上下文的状态，使用Redis进行存储和检索
 */
@Component
public class StateManager {

    private static final Logger log = LoggerFactory.getLogger(StateManager.class);
    /** Redis键前缀 */
    private static final String KEY_PREFIX = "devops:ctx:";
    /** 会话超时时间（小时） */
    private static final long TTL_HOURS = 2;

    /** Redis模板，用于操作Redis */
    private final RedisTemplate<String, InvestigationContext> redisTemplate;

    /**
     * 构造函数，注入RedisTemplate
     * @param redisTemplate Redis模板
     */
    public StateManager(RedisTemplate<String, InvestigationContext> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 保存会话上下文到Redis
     * @param sessionId 会话ID
     * @param ctx 调查上下文
     */
    public void saveContext(String sessionId, InvestigationContext ctx) {
        String key = buildKey(sessionId);
        redisTemplate.opsForValue().set(key, ctx, TTL_HOURS, TimeUnit.HOURS);
        log.debug("保存会话上下文: sessionId={}", sessionId);
    }

    /**
     * 从Redis获取会话上下文
     * @param sessionId 会话ID
     * @return 调查上下文，如果不存在则返回null
     */
    public InvestigationContext getContext(String sessionId) {
        String key = buildKey(sessionId);
        InvestigationContext ctx = redisTemplate.opsForValue().get(key);
        if (ctx == null) {
            log.warn("会话上下文不存在或已过期: sessionId={}", sessionId);
        }
        return ctx;
    }

    /**
     * 从Redis删除会话上下文
     * @param sessionId 会话ID
     */
    public void deleteContext(String sessionId) {
        String key = buildKey(sessionId);
        redisTemplate.delete(key);
        log.debug("删除会话上下文: sessionId={}", sessionId);
    }

    /**
     * 构建Redis键
     * @param sessionId 会话ID
     * @return Redis键
     */
    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
