package org.example.smarttransportation.controller;

import org.example.smarttransportation.dto.ChatRequest;
import org.example.smarttransportation.dto.ChatResponse;
import org.example.smarttransportation.entity.ChatHistory;
import org.example.smarttransportation.service.AIAssistantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * AI对话控制器
 * 
 * @author pojin
 * @date 2025/11/23
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private AIAssistantService aiAssistantService;

    /**
     * 处理用户对话请求
     */
    @PostMapping("/message")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        try {
            // 参数验证
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ChatResponse.error(request.getSessionId(), "消息内容不能为空"));
            }

            // 生成会话ID（如果没有提供）
            if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
                request.setSessionId(UUID.randomUUID().toString());
            }

            logger.info("收到对话请求 - 会话ID: {}, 消息: {}, 深度搜索: {}", 
                request.getSessionId(), request.getMessage(), request.getEnableSearch());

            // 调用AI助手服务
            ChatResponse response = aiAssistantService.chat(request);

            logger.info("对话处理完成 - 会话ID: {}, 耗时: {}ms", 
                response.getSessionId(), response.getProcessingTimeMs());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("对话处理失败", e);
            return ResponseEntity.internalServerError()
                .body(ChatResponse.error(request.getSessionId(), "服务器内部错误"));
        }
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatHistory>> getChatHistory(@PathVariable String sessionId) {
        try {
            List<ChatHistory> history = aiAssistantService.getChatHistory(sessionId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            logger.error("获取对话历史失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 创建新会话
     */
    @PostMapping("/session/new")
    public ResponseEntity<String> createNewSession() {
        try {
            String sessionId = UUID.randomUUID().toString();
            logger.info("创建新会话: {}", sessionId);
            return ResponseEntity.ok(sessionId);
        } catch (Exception e) {
            logger.error("创建会话失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 系统状态检查
     */
    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        return ResponseEntity.ok("AI助手服务运行正常");
    }

    /**
     * 获取AI助手介绍
     */
    @GetMapping("/intro")
    public ResponseEntity<ChatResponse> getIntroduction() {
        try {
            String introMessage = """
                👋 您好！我是T-Agent，您的智慧交通AI助手。
                
                我可以帮您：
                🚗 分析纽约曼哈顿区的交通事故数据
                🌤️ 了解天气对交通的影响
                🚇 查询地铁客流量信息
                📅 分析许可事件对交通的影响
                ⚠️ 提供交通风险预警和建议
                
                请随时向我提问，比如：
                • "最近有哪些交通事故？"
                • "今天的天气会影响交通吗？"
                • "地铁客流量如何？"
                • "有什么交通风险需要注意？"
                
                我会基于实时数据为您提供专业的分析和建议！
                """;

            ChatResponse response = ChatResponse.success("intro", introMessage);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取介绍失败", e);
            return ResponseEntity.internalServerError()
                .body(ChatResponse.error("intro", "获取介绍信息失败"));
        }
    }
}
