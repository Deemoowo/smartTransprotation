package org.example.smarttransportation.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.example.smarttransportation.dto.ChatRequest;
import org.example.smarttransportation.dto.ChatResponse;
import org.example.smarttransportation.entity.ChatHistory;
import org.example.smarttransportation.repository.ChatHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI智能助手服务
 *
 * @author pojin
 * @date 2025/11/23
 */
@Service
public class AIAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(AIAssistantService.class);

    private final ChatClient chatClient;

    @Autowired
    private ChatHistoryRepository chatHistoryRepository;

    @Autowired
    private TrafficDataAnalysisService trafficDataAnalysisService;

    public AIAssistantService(ChatModel chatModel) {
        // 构建ChatClient，设置专门的交通助手参数
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(
                        DashScopeChatOptions.builder()
                                .withTopP(0.8)
                                .withTemperature(0.7)
                                .build()
                )
                .defaultSystem("""
                    你是T-Agent，一个专业的智慧交通AI助手。你的主要职责是：
                    
                    1. 帮助用户分析纽约市曼哈顿区的交通数据和风险
                    2. 基于实时数据提供交通风险预警和建议
                    3. 回答关于交通事故、天气影响、许可事件和地铁客流的问题
                    4. 提供专业的交通管理建议和决策支持
                    5. 利用互联网搜索获取最新的交通新闻、政策和实时路况
                    
                    你可以访问以下数据：
                    - 交通事故数据 (nyc_traffic_accidents)
                    - 天气数据 (nyc_weather_data) 
                    - 许可事件数据 (nyc_permitted_events)
                    - 地铁客流数据 (subway_ridership)
                    - 互联网实时信息 (通过 webSearch 工具)
                    
                    请用专业但友好的语调回答，并在适当时候主动提供相关的数据洞察。
                    如果用户的问题涉及数据查询，请在回答中明确说明你查询了哪些数据源。
                    """)
                .build();
    }

    /**
     * 处理用户对话请求
     */
    @Transactional
    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 生成会话ID（如果没有提供）
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.trim().isEmpty()) {
                sessionId = UUID.randomUUID().toString();
            }

            // 检查是否需要数据查询
            boolean needsDataQuery = isDataQueryRequired(request.getMessage());
            String enhancedMessage = request.getMessage();
            List<String> queriedTables = new ArrayList<>();

            if (needsDataQuery) {
                // 调用数据分析服务
                try {
                    String dataAnalysis = trafficDataAnalysisService.analyzeUserQuery(request.getMessage());
                    if (dataAnalysis != null && !dataAnalysis.trim().isEmpty()) {
                        enhancedMessage = request.getMessage() + "\n\n【数据查询结果】\n" + dataAnalysis;
                        queriedTables = extractQueriedTables(request.getMessage());
                    }
                } catch (Exception e) {
                    logger.warn("数据查询失败: {}", e.getMessage());
                    enhancedMessage = request.getMessage() + "\n\n注意：当前无法访问实时数据，回答基于一般知识。";
                }
            }

            // 构建对话上下文
            ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt();

            // 如果启用了深度搜索，添加webSearch工具
            if (Boolean.TRUE.equals(request.getEnableSearch())) {
                logger.info("启用深度搜索 (Deep Research) - 挂载 webSearch 工具");
                requestSpec.functions("webSearch");
                
                // 强制提示 AI 使用搜索工具
                enhancedMessage += "\n\n【系统提示】用户已开启深度搜索模式。请务必使用 'webSearch' 工具搜索互联网上的最新信息来补充你的回答，特别是当本地数据不足或过时的时候。不要仅依赖训练数据。";
            }

            // 添加历史上下文
            if (request.getIncludeContext() != null && request.getIncludeContext()) {
                List<ChatHistory> recentChats = chatHistoryRepository
                    .findRecentChatsBySessionId(sessionId);

                int maxRounds = request.getMaxContextRounds() != null ?
                    request.getMaxContextRounds() : 3;

                StringBuilder contextBuilder = new StringBuilder();
                int contextCount = 0;
                for (int i = Math.min(recentChats.size() - 1, maxRounds - 1); i >= 0; i--) {
                    ChatHistory chat = recentChats.get(i);
                    if (chat.getUserMessage() != null && chat.getAssistantMessage() != null) {
                        contextBuilder.append("用户: ").append(chat.getUserMessage()).append("\n");
                        contextBuilder.append("助手: ").append(chat.getAssistantMessage()).append("\n\n");
                        contextCount++;
                    }
                }

                if (contextBuilder.length() > 0) {
                    enhancedMessage = "【对话历史】\n" + contextBuilder.toString() +
                                    "【当前问题】\n" + enhancedMessage;
                }
            }

            // 调用千问API
            String assistantReply = requestSpec.user(enhancedMessage).call().content();

            // 保存对话历史
            saveChatHistory(sessionId, request.getMessage(), assistantReply, needsDataQuery, queriedTables);

            // 构建响应
            ChatResponse response = ChatResponse.success(sessionId, assistantReply);
            response.setInvolvesDataQuery(needsDataQuery);
            response.setQueriedTables(queriedTables);
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

            if (needsDataQuery && !queriedTables.isEmpty()) {
                response.setDataQuerySummary("已查询交通相关数据并整合到回答中");
            }

            return response;

        } catch (Exception e) {
            logger.error("AI对话处理失败", e);
            ChatResponse errorResponse = ChatResponse.error(
                request.getSessionId(),
                "处理对话时发生错误: " + e.getMessage()
            );
            errorResponse.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return errorResponse;
        }
    }

    /**
     * 判断是否需要数据查询
     */
    private boolean isDataQueryRequired(String userMessage) {
        String message = userMessage.toLowerCase();

        // 关键词匹配
        String[] dataKeywords = {
            "事故", "accident", "天气", "weather", "地铁", "subway",
            "客流", "ridership", "许可", "permit", "事件", "event",
            "数据", "data", "统计", "statistics", "分析", "analysis",
            "查询", "query", "多少", "how many", "什么时候", "when",
            "哪里", "where", "趋势", "trend", "风险", "risk"
        };

        for (String keyword : dataKeywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 提取查询的数据表
     */
    private List<String> extractQueriedTables(String userMessage) {
        List<String> tables = new ArrayList<>();
        String message = userMessage.toLowerCase();

        if (message.contains("事故") || message.contains("accident")) {
            tables.add("nyc_traffic_accidents");
        }
        if (message.contains("天气") || message.contains("weather")) {
            tables.add("nyc_weather_data");
        }
        if (message.contains("地铁") || message.contains("subway") || message.contains("客流")) {
            tables.add("subway_ridership");
        }
        if (message.contains("许可") || message.contains("permit") || message.contains("事件")) {
            tables.add("nyc_permitted_events");
        }

        return tables;
    }

    /**
     * 保存对话历史
     */
    private void saveChatHistory(String sessionId, String userMessage, String assistantMessage,
                                boolean involvesDataQuery, List<String> queriedTables) {
        try {
            ChatHistory chatHistory = new ChatHistory();
            chatHistory.setSessionId(sessionId);
            chatHistory.setUserMessage(userMessage);
            chatHistory.setAssistantMessage(assistantMessage);
            chatHistory.setMessageType("conversation");
            chatHistory.setInvolvesDataQuery(involvesDataQuery);

            if (queriedTables != null && !queriedTables.isEmpty()) {
                chatHistory.setQueriedTables(String.join(",", queriedTables));
            }

            chatHistoryRepository.save(chatHistory);
        } catch (Exception e) {
            logger.error("保存对话历史失败", e);
        }
    }

    /**
     * 获取会话历史
     */
    public List<ChatHistory> getChatHistory(String sessionId) {
        return chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * 清理旧的对话历史
     */
    @Transactional
    public void cleanupOldChats(int daysToKeep) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(daysToKeep);
        chatHistoryRepository.deleteByCreatedAtBefore(cutoffTime);
    }
}