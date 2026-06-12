package com.devboss.controller;

import com.devboss.agent.InvestigationContext;
import com.devboss.agent.Orchestrator;
import com.devboss.graph.NodeResult;
import com.devboss.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final Orchestrator orchestrator;
    private final MessageService messageService;

    public ChatController(Orchestrator orchestrator, MessageService messageService) {
        this.orchestrator = orchestrator;
        this.messageService = messageService;
    }

    /**
     * 处理聊天请求（POST方法）
     * @param message 用户消息
     * @param sessionId 会话ID（可选）
     * @return SSE发射器，用于流式响应
     */
    @PostMapping("/chat")
    public SseEmitter chat(@RequestParam("message") String message,
                           @RequestParam(value = "sessionId", required = false) String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().substring(0, 8);
        }

        SseEmitter emitter = new SseEmitter(300000L);

        String finalSessionId = sessionId;
        log.info("收到用户消息: sessionId={}, message={}", finalSessionId, message);

        asyncProcess(emitter, finalSessionId, message);

        return emitter;
    }

    /**
     * 处理聊天请求（GET方法）
     * @param message 用户消息
     * @param sessionId 会话ID（可选）
     * @return SSE发射器，用于流式响应
     */
    @GetMapping("/chat")
    public SseEmitter chatGet(@RequestParam("message") String message,
                              @RequestParam(value = "sessionId", required = false) String sessionId) {
        return chat(message, sessionId);
    }

    /**
     * 健康检查接口
     * @return 健康状态信息
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "devops-copilot",
                "timestamp", System.currentTimeMillis()
        );
    }

    @GetMapping("/chat/sync")
    public String chatSync(@RequestParam("message") String message,
                           @RequestParam(value = "sessionId", required = false) String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().substring(0, 8);
        }
        log.info("同步聊天: sessionId={}, message={}", sessionId, message);
        try {
            InvestigationContext ctx = orchestrator.loadContext(sessionId);
            boolean isNewSession = (ctx == null);
            if (isNewSession) {
                ctx = orchestrator.createContext(sessionId, message);
            } else if (ctx.isAwaitingApproval()) {
                ctx.setUserMessage(message);
            } else {
                ctx.setUserMessage(message);
                ctx.addMessage("user", message);
                messageService.saveMessage(sessionId, "user", message);
                ctx.setCurrentNodeId("START");
            }
            StringBuilder full = new StringBuilder();
            while (!orchestrator.isFinished(ctx)) {
                NodeResult result = orchestrator.executeStep(ctx);
                if (result.output() != null && !result.output().isEmpty()) {
                    full.append(result.output());
                }
            }
            return full.toString();
        } catch (Exception e) {
            log.error("同步聊天失败: sessionId={}", sessionId, e);
            return "处理失败: " + e.getMessage();
        }
    }

    /**
     * 异步处理用户消息
     * @param emitter SSE发射器
     * @param sessionId 会话ID
     * @param message 用户消息
     */
    private void asyncProcess(SseEmitter emitter, String sessionId, String message) {
        new Thread(() -> {
            try {
                InvestigationContext ctx = orchestrator.loadContext(sessionId);
                boolean isNewSession = (ctx == null);

                if (isNewSession) {
                    ctx = orchestrator.createContext(sessionId, message);
                } else if (ctx.isAwaitingApproval()) {
                    ctx.setUserMessage(message);
                } else {
                    ctx.setUserMessage(message);
                    ctx.addMessage("user", message);
                    messageService.saveMessage(sessionId, "user", message);
                    ctx.setCurrentNodeId("START");
                }

                while (!orchestrator.isFinished(ctx)) {
                    NodeResult result = orchestrator.executeStep(ctx);
                    if (result.output() != null && !result.output().isEmpty()) {
                        // 确保发送的是字符串，避免 SSE 转换器问题
                        String data = result.output();
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(data));
                    }
                    Thread.sleep(100);
                }

                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(""));
                emitter.complete();

            } catch (Exception e) {
                log.error("SSE 处理失败: sessionId={}", sessionId, e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("处理失败: " + e.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        }).start();
    }
}
