package com.devboss.controller;

import com.devboss.agent.InvestigationContext;
import com.devboss.agent.Orchestrator;
import com.devboss.graph.NodeResult;
import com.devboss.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import java.util.Scanner;
import java.util.UUID;

/**
 * 命令行交互控制器：处理控制台输入的命令
 */
@Controller
public class CommandController {

    private static final Logger log = LoggerFactory.getLogger(CommandController.class);

    private final Orchestrator orchestrator;
    private final MessageService messageService;

    public CommandController(Orchestrator orchestrator, MessageService messageService) {
        this.orchestrator = orchestrator;
        this.messageService = messageService;
    }

    public void startCli() {
        Scanner scanner = new Scanner(System.in);
        String sessionId = UUID.randomUUID().toString().substring(0, 8);

        System.out.println("=" .repeat(60));
        System.out.println("  DevOps Copilot - 智能研发效能助手");
        System.out.println("=" .repeat(60));
        System.out.println("  支持的命令示例：");
        System.out.println("  - order-service 的 /create 接口错误率突然到 20% 了，帮我看看");
        System.out.println("  - 跑一次健康巡检");
        System.out.println("  - 查一下 payment-service 当前部署的版本");
        System.out.println("  - exit / quit - 退出");
        System.out.println("=" .repeat(60));
        System.out.println();

        while (true) {
            System.out.print("You > ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                System.out.println("Bye!");
                break;
            }

            InvestigationContext ctx = orchestrator.loadContext(sessionId);
            boolean isNewSession = (ctx == null);

            if (isNewSession) {
                ctx = orchestrator.createContext(sessionId, input);
            } else if (ctx.isAwaitingApproval()) {
                ctx.setUserMessage(input);
            } else {
                ctx.setUserMessage(input);
                ctx.addMessage("user", input);
                messageService.saveMessage(sessionId, "user", input);
                ctx.setCurrentNodeId("START");
            }

            while (!orchestrator.isFinished(ctx)) {
                try {
                    NodeResult result = orchestrator.executeStep(ctx);
                    if (result.output() != null && !result.output().isEmpty()) {
                        System.out.println(result.output());
                    }
                } catch (Exception e) {
                    log.error("处理步骤失败: node={}", ctx.getCurrentNodeId(), e);
                    System.out.println("[错误] " + e.getMessage());
                    break;
                }
            }
            System.out.println();
        }

        scanner.close();
    }
}
