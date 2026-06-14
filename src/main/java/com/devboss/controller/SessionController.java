package com.devboss.controller;

import com.devboss.entity.Conversation;
import com.devboss.entity.Message;
import com.devboss.service.ConversationService;
import com.devboss.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 会话管理接口：对话历史的增删改查
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    public SessionController(ConversationService conversationService, MessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    @GetMapping
    public List<Conversation> list() {
        return conversationService.getRecentSessions();
    }

    @GetMapping("/{sessionId}/messages")
    public List<Message> getMessages(@PathVariable String sessionId) {
        return messageService.getMessagesBySession(sessionId);
    }

    @PutMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> updateTitle(@PathVariable String sessionId,
                                                            @RequestBody Map<String, String> body) {
        String newTitle = body.get("title");
        if (newTitle == null || newTitle.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "标题不能为空"));
        }
        boolean success = conversationService.updateTitle(sessionId, newTitle.trim());
        if (success) {
            return ResponseEntity.ok(Map.of("message", "标题已更新"));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(@PathVariable String sessionId) {
        boolean success = conversationService.deleteSession(sessionId);
        if (success) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
