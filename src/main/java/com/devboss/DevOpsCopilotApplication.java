package com.devboss;

import com.devboss.controller.CommandController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * DevOps Copilot应用程序主类
 * Spring Boot应用的入口点，负责启动应用和初始化命令行界面（如果需要）
 */
@SpringBootApplication(excludeName = {
        "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
        "org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
@EnableConfigurationProperties
public class DevOpsCopilotApplication implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevOpsCopilotApplication.class);

    /** 命令行控制器 */
    private final CommandController commandController;

    /**
     * 构造函数，注入CommandController
     * @param commandController 命令行控制器
     */
    public DevOpsCopilotApplication(CommandController commandController) {
        this.commandController = commandController;
    }

    public static void main(String[] args) {
        SpringApplication.run(DevOpsCopilotApplication.class, args);
    }

    /**
     * 应用程序启动后运行的方法
     * 如果指定了--cli参数，则启动命令行界面
     * @param args 应用程序参数
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("DevOps Copilot 启动成功!");
        log.info("Web 端口: 8080, SSH / API: /api/chat");

        boolean useCli = args.containsOption("cli") || args.getNonOptionArgs().contains("--cli");
        if (useCli) {
            commandController.startCli();
        }
    }
}
