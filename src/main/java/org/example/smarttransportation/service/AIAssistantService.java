package org.example.smarttransportation.service;

// import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions; // 原 DashScope 导入，已切换到 OpenAI
import org.springframework.ai.openai.OpenAiChatOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.smarttransportation.dto.ChartData;
import org.example.smarttransportation.dto.ChatRequest;
import org.example.smarttransportation.dto.ChatResponse;
import org.example.smarttransportation.dto.WeatherAnswer;
import org.example.smarttransportation.entity.ChatHistory;
import org.example.smarttransportation.repository.ChatHistoryRepository;
import org.example.smarttransportation.service.NL2SQLService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private DateTimeParserService dateTimeParserService;

    @Autowired
    private RAGService ragService;

    @Autowired
    private WeatherApiService weatherApiService;

    @Autowired
    private NL2SQLService nl2sqlService;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private MetadataCacheService metadataCacheService;

    public AIAssistantService(@Qualifier("openAiChatModel") ChatModel chatModel) {
        // 构建ChatClient，设置专门的交通助手参数
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(
                        // DashScopeChatOptions.builder() // 原 DashScope 配置，已切换到 OpenAI
                        //         .withTopP(0.8)
                        //         .withTemperature(0.7)
                        //         .build()
                        OpenAiChatOptions.builder()
                                .withTopP(0.8)
                                .withTemperature(0.7)
                                .build()
                )
                .defaultFunctions("trafficQuery", "weatherQuery", "webSearch")
                .defaultSystem("""
                    你是T-Agent，一个专业的智慧交通AI助手。
                    
                    【核心职责】
                    1. 交通数据分析：分析纽约曼哈顿区的交通事故、地铁客流、许可事件等数据。
                    2. 风险预警：基于数据提供交通风险预警。
                    3. 决策支持：提供交通管理建议。
                    
                    【工具使用指南】
                    - trafficQuery: **必须优先使用**。当用户询问"交通怎么样"、"拥堵情况"、"事故统计"、"客流趋势"或任何与交通状况相关的问题时，必须调用此工具。不要仅依赖天气数据来推断交通状况。
                    - weatherQuery: 当用户明确询问天气，或需要分析天气对交通的影响时使用。
                    - webSearch: 仅在本地数据不足或需要实时新闻时使用。
                    
                    【关键规则】
                    1. **Session ID 传递**：调用 `trafficQuery` 或 `weatherQuery` 时，**必须**将用户提示中提供的 `Current Session ID` 填入工具的 `sessionId` 参数。如果缺少此参数，工具将无法记录思考过程。
                    2. **思考过程**：在回答前，先思考需要哪些数据，然后调用相应工具。
                    
                    请用专业、数据驱动的方式回答。
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
        String userMessage = request.getMessage();
        StringBuilder contextBuilder = new StringBuilder();

        if (request.getIncludeContext() != null && request.getIncludeContext()) {
             List<ChatHistory> recentChats = chatHistoryRepository.findRecentChatsBySessionId(sessionId);
             int maxRounds = request.getMaxContextRounds() != null ? request.getMaxContextRounds() : 3;
             for (int i = Math.min(recentChats.size() - 1, maxRounds - 1); i >= 0; i--) {
                ChatHistory chat = recentChats.get(i);
                if (chat.getUserMessage() != null && chat.getAssistantMessage() != null) {
                    contextBuilder.append("用户: ").append(chat.getUserMessage()).append("\n");
                    contextBuilder.append("助手: ").append(chat.getAssistantMessage()).append("\n\n");
                }
            }
        }
        
        String finalPrompt = contextBuilder.length() > 0 
            ? "【对话历史】\n" + contextBuilder.toString() + "【当前问题】\n" + userMessage
            : userMessage;

        // 2. 调用流式API (ReAct模式，工具已在构造函数中注册)
        StringBuilder fullResponseBuilder = new StringBuilder();
        
        // 如果用户开启了搜索，可以在这里额外提示模型
        if (Boolean.TRUE.equals(request.getEnableSearch())) {
            finalPrompt += "\n\n(用户已开启深度搜索模式，请积极使用 webSearch 工具)";
        }
        
        // 注入Session ID
        finalPrompt += String.format("\n\n[SYSTEM INSTRUCTION]\nCurrent Session ID: %s\n(You MUST pass this ID to any tool calls to enable thought process logging.)", sessionId);

        Flux<String> aiStream = chatClient.prompt()
                .user(finalPrompt)
                .stream()
                .content()
                .doOnNext(fullResponseBuilder::append)
                .doOnComplete(() -> {
                    // 保存历史，暂时无法获取具体的工具调用详情
                    saveChatHistory(sessionId, request.getMessage(), fullResponseBuilder.toString(), false, new ArrayList<>());
                })
                .doOnError(e -> logger.error("流式生成失败", e));
                
        // 3. 从缓存中获取元数据并追加
        return aiStream.concatWith(Mono.fromCallable(() -> {
            try {
                ChatResponse metaResponse = metadataCacheService.getAndClear(sessionId);
                if (metaResponse == null) {
                    metaResponse = new ChatResponse();
                    metaResponse.setInvolvesDataQuery(false);
                    metaResponse.setQueriedTables(new ArrayList<>());
                    metaResponse.setCharts(new ArrayList<>());
                }
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
     * 处理事前主动风险预警场景（支持日期解析和多数据源）
     */
    private ChatResponse handleProactiveWarningScenario(ChatRequest request, String sessionId, long startTime) {
        try {
            // 从用户输入中解析日期时间
            java.time.LocalDateTime targetDateTime = dateTimeParserService.parseDateTime(request.getMessage());

            // 确定数据源策略
            boolean useNetworkSearch = dateTimeParserService.shouldUseNetworkSearch(targetDateTime);

            // 如果没有解析到日期，使用当前时间并启用网络搜索
            if (targetDateTime == null) {
                targetDateTime = java.time.LocalDateTime.now();
                useNetworkSearch = true;
            }

            // 记录数据源选择日志
            logger.info("风险预警请求 - 目标时间: {}, 使用网络搜索: {}", targetDateTime, useNetworkSearch);

            // 调用风险预警服务生成风险预警报告
            org.example.smarttransportation.dto.RiskWarningReport riskReport =
                riskWarningService.generateRiskWarning(targetDateTime, useNetworkSearch);

            // 构建响应消息
            StringBuilder responseMessage = new StringBuilder();
            responseMessage.append("【T-Agent 智能风险预警报告】\n\n");

            // 添加数据源说明
            if (useNetworkSearch) {
                responseMessage.append("📡 数据来源: 实时网络搜索 + 历史数据分析\n");
            } else {
                responseMessage.append("📊 数据来源: 历史数据库分析\n");
            }

            responseMessage.append("🕒 分析时间: ").append(riskReport.getTimeWindow()).append("\n");
            responseMessage.append("📍 影响区域: ").append(riskReport.getAffectedArea()).append("\n");
            responseMessage.append("⚠️ 风险等级: ").append(riskReport.getRiskLevel()).append("\n");
            responseMessage.append("🔍 风险类型: ").append(riskReport.getRiskType()).append("\n\n");

            // 风险分析详情
            responseMessage.append("【📈 综合风险分析】\n");
            org.example.smarttransportation.dto.RiskWarningReport.RiskAnalysis riskAnalysis = riskReport.getRiskAnalysis();
            responseMessage.append("• 综合风险评分: ").append(riskAnalysis.getOverallRiskScore()).append("/100\n");
            responseMessage.append("• 主要风险因子: ").append(riskAnalysis.getRiskFactors()).append("\n\n");

            // 详细风险分解
            if (riskAnalysis.getWeatherRisk() != null) {
                responseMessage.append("🌤️ 天气风险 (").append(riskAnalysis.getWeatherRisk().getRiskScore()).append("分): ");
                responseMessage.append(riskAnalysis.getWeatherRisk().getWeatherDescription()).append("\n");
            }

            if (riskAnalysis.getTrafficRisk() != null) {
                responseMessage.append("🚗 交通风险 (").append(riskAnalysis.getTrafficRisk().getRiskScore()).append("分): ");
                responseMessage.append(riskAnalysis.getTrafficRisk().getTrafficPattern()).append("\n");
            }

            if (riskAnalysis.getEventRisk() != null) {
                responseMessage.append("🎪 事件风险 (").append(riskAnalysis.getEventRisk().getRiskScore()).append("分): ");
                responseMessage.append(riskAnalysis.getEventRisk().getEventTypes()).append("\n\n");
            }

            // 高风险区域
            responseMessage.append("【🚨 重点关注区域】\n");
            if (riskReport.getHighRiskZones() != null && !riskReport.getHighRiskZones().isEmpty()) {
                for (org.example.smarttransportation.dto.RiskWarningReport.HighRiskZone zone : riskReport.getHighRiskZones()) {
                    responseMessage.append("📍 ").append(zone.getLocation()).append(" (").append(zone.getRiskLevel()).append(")\n");
                    responseMessage.append("   风险因素: ").append(zone.getRiskFactors()).append("\n");
                    responseMessage.append("   建议措施: ").append(String.join(", ", zone.getDeploymentSuggestions())).append("\n\n");
                }
            } else {
                responseMessage.append("✅ 当前暂无特别需要关注的高风险区域\n\n");
            }

            // 应对建议
            responseMessage.append("【💡 应对建议】\n");
            if (riskReport.getRecommendations() != null && !riskReport.getRecommendations().isEmpty()) {
                for (int i = 0; i < riskReport.getRecommendations().size(); i++) {
                    responseMessage.append((i + 1)).append(". ").append(riskReport.getRecommendations().get(i)).append("\n");
                }
            } else {
                responseMessage.append("1. 保持常规监控和准备\n");
                responseMessage.append("2. 关注天气和交通变化\n");
            }

            responseMessage.append("\n【📋 执行标准】\n");
            responseMessage.append(riskReport.getSopReference()).append("\n");

            // 添加免责声明
            if (useNetworkSearch) {
                responseMessage.append("\n💡 提示: 本报告基于实时网络数据分析，建议结合现场情况进行综合判断。");
            } else {
                responseMessage.append("\n💡 提示: 本报告基于历史数据分析，如需最新信息请咨询相关部门。");
            }

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
                // if (weatherAnswer != null) {
                //    summary = !charts.isEmpty()
                //        ? "已接入天气数据并生成图表"
                //        : "已接入天气数据";
                // } else if (!charts.isEmpty()) {
                if (!charts.isEmpty()) {
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
