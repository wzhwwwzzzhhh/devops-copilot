package com.devboss.service;

import com.devboss.entity.Message;
import com.devboss.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final int MAX_HISTORY_EXCHANGES = 5;

    private final MessageRepository repository;

    public MessageService(MessageRepository repository) {
        this.repository = repository;
    }

    public void saveMessage(String sessionId, String role, String content) {
        try {
            Message msg = new Message(sessionId, role, content);
            msg.setTokens(content.length());
            repository.save(msg);
            log.debug("消息已保存: session={}, role={}, length={}", sessionId, role, content.length());
        } catch (Exception e) {
            log.error("消息保存失败: session={}, role={}", sessionId, role, e);
        }
    }

    public String getHistoryContext(String sessionId) {
        try {
            List<Message> messages = repository.findBySessionIdOrderByCreatedAtAsc(sessionId);
            if (messages.isEmpty()) {
                return "";
            }

            int totalMessages = messages.size();
            int startIndex = Math.max(0, totalMessages - MAX_HISTORY_EXCHANGES * 2);

            return messages.subList(startIndex, totalMessages).stream()
                    .map(msg -> msg.getRole() + ": " + msg.getContent())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("获取对话历史失败: session={}", sessionId, e);
            return "";
        }
    }

    public List<Message> getMessagesBySession(String sessionId) {
        return repository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}
