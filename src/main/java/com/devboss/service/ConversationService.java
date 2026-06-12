package com.devboss.service;

import com.devboss.entity.Conversation;
import com.devboss.repository.ConversationRepository;
import com.devboss.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository, MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public Conversation createOrUpdate(String sessionId, String userMessage) {
        Conversation conv = conversationRepository.findBySessionId(sessionId).orElse(null);
        if (conv == null) {
            conv = new Conversation(sessionId, userMessage);
            conv.setTitle(generateTitle(userMessage));
            conv = conversationRepository.save(conv);
            log.info("会话已创建: sessionId={}, title={}", sessionId, conv.getTitle());
        } else {
            conv.setUpdatedAt(java.time.LocalDateTime.now());
            conversationRepository.save(conv);
        }
        return conv;
    }

    public List<Conversation> getRecentSessions() {
        return conversationRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;  // nulls last
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .toList();
    }

    public Optional<Conversation> findBySessionId(String sessionId) {
        return conversationRepository.findBySessionId(sessionId);
    }

    public boolean updateTitle(String sessionId, String newTitle) {
        return conversationRepository.findBySessionId(sessionId).map(conv -> {
            conv.setTitle(newTitle);
            conversationRepository.save(conv);
            log.info("会话标题已更新: sessionId={}, title={}", sessionId, newTitle);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean deleteSession(String sessionId) {
        if (!conversationRepository.findBySessionId(sessionId).isPresent()) {
            return false;
        }
        messageRepository.deleteBySessionId(sessionId);
        conversationRepository.deleteBySessionId(sessionId);
        log.info("会话已删除: sessionId={}", sessionId);
        return true;
    }

    private String generateTitle(String message) {
        String title = message.trim();
        if (title.length() > 20) {
            title = title.substring(0, 20) + "...";
        }
        return title;
    }
}
