package org.example.smarttransportation.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.smarttransportation.dto.ChartData;
import org.example.smarttransportation.dto.ChatRequest;
import org.example.smarttransportation.dto.ChatResponse;
import org.example.smarttransportation.dto.WeatherAnswer;
import org.example.smarttransportation.entity.ChatHistory;
import org.example.smarttransportation.repository.ChatHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private RiskWarningService riskWarningService;

    @Autowired
    private RAGService ragService;

    @Autowired
    private WeatherApiService weatherApiService;

    @Autowired
    private NL2SQLService nl2sqlService;
    private ObjectMapper objectMapper;

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
                    - 天气数据（通过外部天气 API）
                    - 许可事件数据 (nyc_permitted_events)
                    - 地铁客流数据 (subway_ridership)
                    - 互联网实时信息 (通过 webSearch 工具)
                    
                    重要规则：
                    1. 当系统提供【数据查询结果】时，这些数据是经过SQL查询验证的准确数据，请直接使用，不要质疑其准确性
                    2. SQL查询中的日期范围条件（如 CRASH_DATETIME <= '2024-02-29 23:59:59'）只是为了限定查询范围，实际返回的数据是符合WHERE条件中所有条件的（包括具体日期如 CRASH DATE = '2024-02-10'）
                    3. 不要因为看到SQL中的日期范围就误判返回的数据日期，要以实际返回的数据记录中的日期字段为准
                    
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

            // 检查是否属于三大核心场景之一
            ScenarioType scenarioType = identifyScenario(request.getMessage());

            // 根据场景类型处理请求
            switch (scenarioType) {
                case PROACTIVE_WARNING:
                    return handleProactiveWarningScenario(request, sessionId, startTime);
                case EMERGENCY_RESPONSE:
                    return handleEmergencyResponseScenario(request, sessionId, startTime);
                case DATA_DRIVEN_GOVERNANCE:
                    return handleDataDrivenGovernanceScenario(request, sessionId, startTime);
                default:
                    return handleGeneralScenario(request, sessionId, startTime);
            }

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
     * 流式对话接口
     */
    public Flux<String> streamChat(ChatRequest request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        try {
            ScenarioType scenarioType = identifyScenario(request.getMessage());
            
            if (scenarioType == ScenarioType.GENERAL) {
                return streamGeneralScenario(request, sessionId, startTime);
            } else {
                // 其他场景暂时使用非流式处理，包装成Flux
                return Mono.fromCallable(() -> {
                    ChatResponse response = chat(request);
                    return response.getMessage();
                }).flux();
            }
        } catch (Exception e) {
            logger.error("流式对话初始化失败", e);
            return Flux.just("处理对话时发生错误: " + e.getMessage());
        }
    }

    private Flux<String> streamGeneralScenario(ChatRequest request, String sessionId, long startTime) {
        // 1. 准备上下文
        boolean needsDataQuery = isDataQueryRequired(request.getMessage());
        String enhancedMessage = request.getMessage();
        List<String> queriedTables = new ArrayList<>();
        
        if (needsDataQuery) {
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

        WeatherAnswer weatherAnswer = weatherApiService.findWeatherAnswerForMessage(request.getMessage());
        if (weatherAnswer != null) {
            needsDataQuery = true;
            queriedTables.add("weather_api_manhattan_2024_02");
            enhancedMessage = enhancedMessage + "\n\n【天气数据支持】\n" + weatherAnswer.getSummary();
        }

        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt();

        if (Boolean.TRUE.equals(request.getEnableSearch())) {
             requestSpec.functions("webSearch");
             enhancedMessage += "\n\n【系统提示】用户已开启深度搜索模式。请务必使用 'webSearch' 工具搜索互联网上的最新信息来补充你的回答，特别是当本地数据不足或过时的时候。不要仅依赖训练数据。";
        }

        if (request.getIncludeContext() != null && request.getIncludeContext()) {
             List<ChatHistory> recentChats = chatHistoryRepository.findRecentChatsBySessionId(sessionId);
             int maxRounds = request.getMaxContextRounds() != null ? request.getMaxContextRounds() : 3;
             StringBuilder contextBuilder = new StringBuilder();
             for (int i = Math.min(recentChats.size() - 1, maxRounds - 1); i >= 0; i--) {
                ChatHistory chat = recentChats.get(i);
                if (chat.getUserMessage() != null && chat.getAssistantMessage() != null) {
                    contextBuilder.append("用户: ").append(chat.getUserMessage()).append("\n");
                    contextBuilder.append("助手: ").append(chat.getAssistantMessage()).append("\n\n");
                }
            }
            if (contextBuilder.length() > 0) {
                enhancedMessage = "【对话历史】\n" + contextBuilder.toString() + "【当前问题】\n" + enhancedMessage;
            }
        }

        // 2. 调用流式API
        StringBuilder fullResponseBuilder = new StringBuilder();
        
        boolean finalNeedsDataQuery = needsDataQuery;
        List<String> finalQueriedTables = queriedTables;
        
        // 准备元数据
        List<ChartData> charts = new ArrayList<>();
        if (weatherAnswer != null && wantsCharts(request.getMessage()) && weatherAnswer.getCharts() != null) {
            charts.addAll(weatherAnswer.getCharts());
        }
        
        ChatResponse metaResponse = new ChatResponse();
        metaResponse.setInvolvesDataQuery(finalNeedsDataQuery);
        metaResponse.setQueriedTables(finalQueriedTables);
        metaResponse.setCharts(charts);
        if (finalNeedsDataQuery && !finalQueriedTables.isEmpty()) {
             String summary = "已查询交通相关数据并整合到回答中";
             if (weatherAnswer != null) {
                 summary = wantsCharts(request.getMessage()) ? "已接入天气数据并生成图表" : "已接入天气数据（未请求图表）";
             }
             metaResponse.setDataQuerySummary(summary);
        }

        Flux<String> aiStream = requestSpec.user(enhancedMessage)
                .stream()
                .content()
                .doOnNext(fullResponseBuilder::append)
                .doOnComplete(() -> {
                    saveChatHistory(sessionId, request.getMessage(), fullResponseBuilder.toString(), finalNeedsDataQuery, finalQueriedTables);
                })
                .doOnError(e -> logger.error("流式生成失败", e));
                
        // 在流结束后追加元数据
        return aiStream.concatWith(Mono.fromCallable(() -> {
            try {
                return "[METADATA]" + objectMapper.writeValueAsString(metaResponse);
            } catch (Exception e) {
                logger.error("序列化元数据失败", e);
                return "";
            }
        }));
    }

    /**
     * 识别场景类型
     */
    private ScenarioType identifyScenario(String userMessage) {
        String message = userMessage.toLowerCase();

        // 优先检查是否为数据查询，如果是数据查询则直接返回通用场景
        if (isDataQueryRequired(userMessage)) {
            return ScenarioType.GENERAL;
        }

        // 事前主动风险预警场景关键词
        String[] warningKeywords = {
            "风险预警", "风险预测", "预防", "预警", "暴雪", "结冰", "天气预警",
            "提前部署", "防范", "风险评估", "潜在风险", "snow", "icing", "blizzard"
        };

        // 事中智能应急响应场景关键词（排除数据查询类关键词）
        String[] emergencyKeywords = {
            "紧急", "应急", "突发", "车祸", "拥堵", "堵塞", "封闭",
            "救援", "处理", "应对", "emergency", "crash", "incident"
        };

        // 事后数据驱动治理场景关键词
        String[] governanceKeywords = {
            "治理", "整改", "优化", "改善", "分析", "复盘", "总结", "黑点",
            "根源", "原因", "治理方案", "改进措施", "governance", "improve",
            "analysis", "solution", "black spot"
        };

        // 检查是否匹配预警场景
        for (String keyword : warningKeywords) {
            if (message.contains(keyword)) {
                return ScenarioType.PROACTIVE_WARNING;
            }
        }

        // 检查是否匹配应急响应场景
        for (String keyword : emergencyKeywords) {
            if (message.contains(keyword)) {
                return ScenarioType.EMERGENCY_RESPONSE;
            }
        }

        // 检查是否匹配治理场景
        for (String keyword : governanceKeywords) {
            if (message.contains(keyword)) {
                return ScenarioType.DATA_DRIVEN_GOVERNANCE;
            }
        }

        return ScenarioType.GENERAL;
    }

    /**
     * 处理事前主动风险预警场景
     */
    private ChatResponse handleProactiveWarningScenario(ChatRequest request, String sessionId, long startTime) {
        try {
            // 调用风险预警服务生成风险预警报告
            // 这里使用当前时间作为目标时间，实际应用中可以根据用户请求解析具体时间
            java.time.LocalDateTime targetDateTime = java.time.LocalDateTime.now();
            org.example.smarttransportation.dto.RiskWarningReport riskReport =
                riskWarningService.generateRiskWarning(targetDateTime);

            // 构建响应消息
            StringBuilder responseMessage = new StringBuilder();
            responseMessage.append("【T-Agent 风险预警报告】\n\n");
            responseMessage.append("风险等级: ").append(riskReport.getRiskLevel()).append("\n");
            responseMessage.append("风险类型: ").append(riskReport.getRiskType()).append("\n");
            responseMessage.append("时间窗口: ").append(riskReport.getTimeWindow()).append("\n");
            responseMessage.append("影响区域: ").append(riskReport.getAffectedArea()).append("\n\n");

            responseMessage.append("【风险分析】\n");
            org.example.smarttransportation.dto.RiskWarningReport.RiskAnalysis riskAnalysis = riskReport.getRiskAnalysis();
            responseMessage.append("综合风险评分: ").append(riskAnalysis.getOverallRiskScore()).append("\n");
            responseMessage.append("风险因子: ").append(riskAnalysis.getRiskFactors()).append("\n\n");

            responseMessage.append("【高风险区域】\n");
            if (riskReport.getHighRiskZones() != null && !riskReport.getHighRiskZones().isEmpty()) {
                for (org.example.smarttransportation.dto.RiskWarningReport.HighRiskZone zone : riskReport.getHighRiskZones()) {
                    responseMessage.append("- ").append(zone.getLocation()).append(" (").append(zone.getRiskLevel()).append(")\n");
                    responseMessage.append("  风险因素: ").append(zone.getRiskFactors()).append("\n");
                    responseMessage.append("  建议措施: ").append(String.join(", ", zone.getDeploymentSuggestions())).append("\n\n");
                }
            } else {
                responseMessage.append("暂无高风险区域。\n\n");
            }

            responseMessage.append("【建议措施】\n");
            if (riskReport.getRecommendations() != null && !riskReport.getRecommendations().isEmpty()) {
                for (int i = 0; i < riskReport.getRecommendations().size(); i++) {
                    responseMessage.append((i + 1)).append(". ").append(riskReport.getRecommendations().get(i)).append("\n");
                }
            }

            responseMessage.append("\n【参考标准】\n");
            responseMessage.append(riskReport.getSopReference()).append("\n");

            // 保存对话历史
            saveChatHistory(sessionId, request.getMessage(), responseMessage.toString(), false, null);

            // 构建响应
            ChatResponse response = ChatResponse.success(sessionId, responseMessage.toString());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return response;

        } catch (Exception e) {
            logger.error("处理风险预警场景失败", e);
            return handleGeneralScenario(request, sessionId, startTime);
        }
    }

    /**
     * 处理事中智能应急响应场景
     */
    private ChatResponse handleEmergencyResponseScenario(ChatRequest request, String sessionId, long startTime) {
        try {
            // 使用RAG服务处理应急响应场景
            RAGService.AnswerResult result = ragService.answer(request.getMessage(), sessionId);
            List<ChartData> charts = buildChartsFromQueryData(result.getQueryData());

            // 构建响应消息
            StringBuilder responseMessage = new StringBuilder();
            responseMessage.append("【T-Agent 应急响应快报】\n\n");
            responseMessage.append(result.getAnswer()).append("\n");

            if (result.getQueryData() != null && !result.getQueryData().isEmpty()) {
                responseMessage.append("\n【数据支撑】\n");
                responseMessage.append("查询到 ").append(result.getQueryData().size()).append(" 条相关数据。\n");
            }

            if (result.getRetrievedDocs() != null && !result.getRetrievedDocs().isEmpty()) {
                responseMessage.append("\n【知识参考】\n");
                responseMessage.append("检索到 ").append(result.getRetrievedDocs().size()).append(" 条相关知识。\n");
            }

            // 保存对话历史
            saveChatHistory(sessionId, request.getMessage(), responseMessage.toString(),
                          result.getQueryData() != null && !result.getQueryData().isEmpty(), null);

            // 构建响应
            ChatResponse response = ChatResponse.success(sessionId, responseMessage.toString());
            response.setCharts(charts);
            response.setInvolvesDataQuery(result.getQueryData() != null && !result.getQueryData().isEmpty());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return response;

        } catch (Exception e) {
            logger.error("处理应急响应场景失败", e);
            return handleGeneralScenario(request, sessionId, startTime);
        }
    }

    /**
     * 处理事后数据驱动治理场景
     */
    private ChatResponse handleDataDrivenGovernanceScenario(ChatRequest request, String sessionId, long startTime) {
        try {
            // 使用RAG服务处理数据驱动治理场景
            RAGService.AnswerResult result = ragService.answer(request.getMessage(), sessionId);
            List<ChartData> charts = buildChartsFromQueryData(result.getQueryData());

            // 构建响应消息
            StringBuilder responseMessage = new StringBuilder();
            responseMessage.append("【T-Agent 数据驱动治理分析报告】\n\n");
            responseMessage.append(result.getAnswer()).append("\n");

            if (result.getQueryData() != null && !result.getQueryData().isEmpty()) {
                responseMessage.append("\n【数据分析】\n");
                responseMessage.append("基于 ").append(result.getQueryData().size()).append(" 条数据进行分析。\n");
            }

            if (result.getRetrievedDocs() != null && !result.getRetrievedDocs().isEmpty()) {
                responseMessage.append("\n【治理建议参考】\n");
                responseMessage.append("参考了 ").append(result.getRetrievedDocs().size()).append(" 条治理经验和标准。\n");
            }

            // 保存对话历史
            saveChatHistory(sessionId, request.getMessage(), responseMessage.toString(),
                          result.getQueryData() != null && !result.getQueryData().isEmpty(), null);

            // 构建响应
            ChatResponse response = ChatResponse.success(sessionId, responseMessage.toString());
            response.setCharts(charts);
            response.setInvolvesDataQuery(result.getQueryData() != null && !result.getQueryData().isEmpty());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return response;

        } catch (Exception e) {
            logger.error("处理数据驱动治理场景失败", e);
            return handleGeneralScenario(request, sessionId, startTime);
        }
    }

    /**
     * 处理通用场景
     */
    private ChatResponse handleGeneralScenario(ChatRequest request, String sessionId, long startTime) {
        try {
            // 检查是否需要数据查询
            boolean needsDataQuery = isDataQueryRequired(request.getMessage());
            String enhancedMessage = request.getMessage();
            List<String> queriedTables = new ArrayList<>();
            List<ChartData> charts = new ArrayList<>();

            if (needsDataQuery) {
                // 调用数据分析服务
                try {
                    String dataAnalysis = trafficDataAnalysisService.analyzeUserQuery(request.getMessage());
                    if (dataAnalysis != null && !dataAnalysis.trim().isEmpty()) {
                        enhancedMessage = request.getMessage() + "\n\n【数据查询结果】\n" + dataAnalysis +
                            "\n\n【重要提示】以上数据查询结果是准确的，SQL查询已经正确执行并返回了符合条件的数据。请直接基于这些数据回答用户问题，不要质疑数据的准确性。如果SQL中包含日期范围条件（如 <= '2024-02-29'），那只是为了限定查询范围，实际返回的数据是符合WHERE条件中指定的具体日期的。";
                        queriedTables = extractQueriedTables(request.getMessage());
                    }
                } catch (Exception e) {
                    logger.warn("数据查询失败: {}", e.getMessage());
                    enhancedMessage = request.getMessage() + "\n\n注意：当前无法访问实时数据，回答基于一般知识。";
                }
            }

            // 判断是否命中曼哈顿 2024 年 2 月天气查询，注入接口/样例数据
            WeatherAnswer weatherAnswer = weatherApiService.findWeatherAnswerForMessage(request.getMessage());
            if (weatherAnswer != null) {
                needsDataQuery = true;
                queriedTables.add("weather_api_manhattan_2024_02");
                if (weatherAnswer.getCharts() != null) {
                    charts.addAll(weatherAnswer.getCharts());
                }
                enhancedMessage = enhancedMessage + "\n\n【天气数据支持】\n" + weatherAnswer.getSummary();
            }

            // 如果用户查询涉及数据，尝试基于查询结果生成可视化图表
            if (needsDataQuery && nl2sqlService != null && nl2sqlService.isNL2SQLServiceAvailable()) {
                try {
                    NL2SQLService.QueryResult sqlResult = nl2sqlService.executeQuery(request.getMessage());
                    if (sqlResult != null && sqlResult.isSuccess()
                        && sqlResult.getData() != null && !sqlResult.getData().isEmpty()) {
                        List<ChartData> queryCharts = buildChartsFromQueryData(sqlResult.getData());
                        if (!queryCharts.isEmpty()) {
                            charts.addAll(queryCharts);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("基于数据查询生成图表失败: {}", e.getMessage());
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
             for (int i = Math.min(recentChats.size() - 1, maxRounds - 1); i >= 0; i--) {
                    ChatHistory chat = recentChats.get(i);
                    if (chat.getUserMessage() != null && chat.getAssistantMessage() != null) {
                        contextBuilder.append("用户: ").append(chat.getUserMessage()).append("\n");
                        contextBuilder.append("助手: ").append(chat.getAssistantMessage()).append("\n\n");
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

            if (!charts.isEmpty()) {
                logger.info("返回图表数量: {}, 标题: {}", charts.size(),
                        charts.stream().map(ChartData::getTitle).toList());
            } else {
                logger.info("本次响应未包含图表");
            }

            // 构建响应
            ChatResponse response = ChatResponse.success(sessionId, assistantReply);
            response.setInvolvesDataQuery(needsDataQuery);
            response.setQueriedTables(queriedTables);
            response.setCharts(charts);
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

            if (needsDataQuery && (!queriedTables.isEmpty() || !charts.isEmpty())) {
                String summary = "已查询交通相关数据并整合到回答中";
                if (weatherAnswer != null) {
                    summary = !charts.isEmpty()
                        ? "已接入天气数据并生成图表"
                        : "已接入天气数据";
                } else if (!charts.isEmpty()) {
                    summary = "已生成数据图表用于辅助说明";
                }
                response.setDataQuerySummary(summary);
            }

            return response;

        } catch (Exception e) {
            logger.error("处理通用场景失败", e);
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
            "事故", "accident", "地铁", "subway",
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
        if (message.contains("地铁") || message.contains("subway") || message.contains("客流")) {
            tables.add("subway_ridership");
        }
        if (message.contains("许可") || message.contains("permit") || message.contains("事件")) {
            tables.add("nyc_permitted_events");
        }

        return tables;
    }

    /**
     * 从结构化查询结果中尝试生成基础图表
     */
    private List<ChartData> buildChartsFromQueryData(List<Map<String, Object>> queryData) {
        List<ChartData> charts = new ArrayList<>();
        if (queryData == null || queryData.isEmpty()) {
            return charts;
        }

        Map<String, Object> firstRow = queryData.get(0);
        if (firstRow == null || firstRow.isEmpty()) {
            return charts;
        }

        String labelKey = null;
        String valueKey = null;

        for (Map.Entry<String, Object> entry : firstRow.entrySet()) {
            if (labelKey == null && !(entry.getValue() instanceof Number)) {
                labelKey = entry.getKey();
            }
            if (valueKey == null && entry.getValue() instanceof Number) {
                valueKey = entry.getKey();
            }
        }

        if (labelKey == null && !firstRow.isEmpty()) {
            labelKey = firstRow.keySet().iterator().next();
        }
        if (valueKey == null) {
            return charts;
        }

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        int maxPoints = 20;

        for (Map<String, Object> row : queryData) {
            if (row == null) {
                continue;
            }
            Object valueObj = row.get(valueKey);
            if (!(valueObj instanceof Number)) {
                continue;
            }
            Object labelObj = row.get(labelKey);
            labels.add(labelObj != null ? labelObj.toString() : "未知");
            values.add(((Number) valueObj).doubleValue());

            if (labels.size() >= maxPoints) {
                break;
            }
        }

        if (labels.isEmpty()) {
            return charts;
        }

        String chartType = "bar";
        if (labelKey != null) {
            String lower = labelKey.toLowerCase();
            if (lower.contains("date") || lower.contains("time")) {
                chartType = "line";
            }
        }

        ChartData chart = ChartData.singleSeries(
            "查询结果 (" + valueKey + " 按 " + labelKey + ")",
            chartType,
            labels,
            values,
            valueKey
        );
        charts.add(chart);
        return charts;
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

    /**
     * 场景类型枚举
     */
    private enum ScenarioType {
        PROACTIVE_WARNING,      // 事前主动风险预警
        EMERGENCY_RESPONSE,     // 事中智能应急响应
        DATA_DRIVEN_GOVERNANCE, // 事后数据驱动治理
        GENERAL                 // 通用场景
    }
}
