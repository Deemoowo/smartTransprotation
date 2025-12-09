package org.example.smarttransportation.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * RAG (检索增强生成) 服务
 * 结合向量搜索和大语言模型提供智能问答
 * 
 * @author pojin
 * @date 2025/11/22
 */
@Service
public class RAGService {
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Autowired
    private NL2SQLService nl2sqlService;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired(required = false)
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    private ChatClient chatClient;
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final String CACHE_PREFIX = "rag:query:";
    private static final int CACHE_EXPIRE_HOURS = 2;
    private static final int MAX_CONTEXT_LENGTH = 4000;
    private static final int DEFAULT_RETRIEVE_COUNT = 5;
    
    /**
     * 智能问答主入口
     */
    public AnswerResult answer(String question, String sessionId) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("问题不能为空");
        }
        
        try {
            // 1. 检查缓存
            AnswerResult cachedResult = getCachedAnswer(question);
            if (cachedResult != null) {
                cachedResult.setFromCache(true);
                return cachedResult;
            }
            
            // 2. 查询意图识别和路由
            QueryIntent intent = identifyQueryIntent(question);
            
            // 3. 根据意图选择处理策略
            AnswerResult result = processQueryByIntent(question, intent, sessionId);
            
            // 4. 缓存结果
            cacheAnswer(question, result);
            
            return result;
            
        } catch (Exception e) {
            return new AnswerResult(
                false, 
                "处理问题时发生错误: " + e.getMessage(), 
                null, 
                QueryIntent.UNKNOWN, 
                null, 
                null
            );
        }
    }
    
    /**
     * 查询意图识别
     */
    private QueryIntent identifyQueryIntent(String question) {
        String lowerQuestion = question.toLowerCase();
        
        // 数据查询类问题
        if (containsDataQueryKeywords(lowerQuestion)) {
            return QueryIntent.DATA_QUERY;
        }
        
        // 知识问答类问题
        if (containsKnowledgeKeywords(lowerQuestion)) {
            return QueryIntent.KNOWLEDGE_QA;
        }
        
        // 分析类问题
        if (containsAnalysisKeywords(lowerQuestion)) {
            return QueryIntent.ANALYSIS;
        }
        
        // 推荐类问题
        if (containsRecommendationKeywords(lowerQuestion)) {
            return QueryIntent.RECOMMENDATION;
        }
        
        return QueryIntent.GENERAL;
    }
    
    /**
     * 根据意图处理查询
     */
    private AnswerResult processQueryByIntent(String question, QueryIntent intent, String sessionId) {
        switch (intent) {
            case DATA_QUERY:
                return handleDataQuery(question);
            case KNOWLEDGE_QA:
                return handleKnowledgeQA(question);
            case ANALYSIS:
                return handleAnalysisQuery(question);
            case RECOMMENDATION:
                return handleRecommendationQuery(question);
            case GENERAL:
            default:
                return handleGeneralQuery(question);
        }
    }
    
    /**
     * 处理数据查询类问题
     */
    private AnswerResult handleDataQuery(String question) {
        try {
            // 使用NL2SQL直接查询数据库
            NL2SQLService.QueryResult queryResult = nl2sqlService.executeQuery(question);
            
            if (queryResult.isSuccess() && queryResult.getData() != null) {
                // 将查询结果转换为自然语言回答
                String answer = formatDataQueryAnswer(question, queryResult);
                
                return new AnswerResult(
                    true,
                    answer,
                    null,
                    QueryIntent.DATA_QUERY,
                    queryResult.getData(),
                    queryResult.getSql()
                );
            } else {
                return new AnswerResult(
                    false,
                    "数据查询失败: " + queryResult.getMessage(),
                    null,
                    QueryIntent.DATA_QUERY,
                    null,
                    queryResult.getSql()
                );
            }
            
        } catch (Exception e) {
            return new AnswerResult(
                false,
                "数据查询处理失败: " + e.getMessage(),
                null,
                QueryIntent.DATA_QUERY,
                null,
                null
            );
        }
    }
    
    /**
     * 处理知识问答类问题
     */
    private AnswerResult handleKnowledgeQA(String question) {
        try {
            // 1. 向量检索相关知识，优先检索SOP和专家知识
            List<VectorStoreService.SearchResult> searchResults =
                vectorStoreService.semanticSearch(question, DEFAULT_RETRIEVE_COUNT);

            // 2. 如果没有找到相关结果，尝试使用更广泛的查询
            if (searchResults == null || searchResults.isEmpty()) {
                // 尝试添加关键词来扩大搜索范围
                String expandedQuery = question + " 交通管理 标准操作程序 专家建议";
                searchResults = vectorStoreService.semanticSearch(expandedQuery, DEFAULT_RETRIEVE_COUNT);
            }

            // 3. 构建上下文，优先使用高相关性的SOP和专家知识
            String context = buildEnhancedContext(searchResults);

            // 4. 生成回答，强调SOP和专家知识来源
            String answer = generateAnswerWithSOPReference(question, context);
            
            return new AnswerResult(
                true,
                answer,
                searchResults,
                QueryIntent.KNOWLEDGE_QA,
                null,
                null
            );
            
        } catch (Exception e) {
            return new AnswerResult(
                false,
                "知识问答处理失败: " + e.getMessage(),
                null,
                QueryIntent.KNOWLEDGE_QA,
                null,
                null
            );
        }
    }
    
    /**
     * 处理分析类问题
     */
    private AnswerResult handleAnalysisQuery(String question) {
        try {
            // 1. 先获取相关数据
            NL2SQLService.QueryResult queryResult = nl2sqlService.executeQuery(question);
            
            // 2. 向量检索相关分析知识
            List<VectorStoreService.SearchResult> searchResults = 
                vectorStoreService.semanticSearch(question + " 分析", DEFAULT_RETRIEVE_COUNT);
            
            // 3. 结合数据和知识生成分析
            String analysis = generateAnalysisAnswer(question, queryResult, searchResults);
            
            return new AnswerResult(
                true,
                analysis,
                searchResults,
                QueryIntent.ANALYSIS,
                queryResult.isSuccess() ? queryResult.getData() : null,
                queryResult.getSql()
            );
            
        } catch (Exception e) {
            return new AnswerResult(
                false,
                "分析处理失败: " + e.getMessage(),
                null,
                QueryIntent.ANALYSIS,
                null,
                null
            );
        }
    }
    
    /**
     * 处理推荐类问题
     */
    private AnswerResult handleRecommendationQuery(String question) {
        try {
            // 1. 检索相关推荐知识
            List<VectorStoreService.SearchResult> searchResults = 
                vectorStoreService.semanticSearch(question + " 推荐 建议", DEFAULT_RETRIEVE_COUNT);
            
            // 2. 获取相关数据支撑
            NL2SQLService.QueryResult queryResult = nl2sqlService.executeQuery(question);
            
            // 3. 生成推荐建议
            String recommendation = generateRecommendationAnswer(question, queryResult, searchResults);
            
            return new AnswerResult(
                true,
                recommendation,
                searchResults,
                QueryIntent.RECOMMENDATION,
                queryResult.isSuccess() ? queryResult.getData() : null,
                queryResult.getSql()
            );
            
        } catch (Exception e) {
            return new AnswerResult(
                false,
                "推荐处理失败: " + e.getMessage(),
                null,
                QueryIntent.RECOMMENDATION,
                null,
                null
            );
        }
    }
    
    /**
     * 处理通用问题
     */
    private AnswerResult handleGeneralQuery(String question) {
        try {
            // 1. 向量检索
            List<VectorStoreService.SearchResult> searchResults = 
                vectorStoreService.semanticSearch(question, DEFAULT_RETRIEVE_COUNT);
            
            // 2. 尝试数据查询
            NL2SQLService.QueryResult queryResult = nl2sqlService.executeQuery(question);
            
            // 3. 综合生成回答
            String answer = generateGeneralAnswer(question, queryResult, searchResults);
            
            return new AnswerResult(
                true,
                answer,
                searchResults,
                QueryIntent.GENERAL,
                queryResult.isSuccess() ? queryResult.getData() : null,
                queryResult.getSql()
            );
            
        } catch (Exception e) {
            return new AnswerResult(
                false,
                "通用查询处理失败: " + e.getMessage(),
                null,
                QueryIntent.GENERAL,
                null,
                null
            );
        }
    }
    
    /**
     * 构建检索上下文
     */
    private String buildContext(List<VectorStoreService.SearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        int currentLength = 0;
        
        for (VectorStoreService.SearchResult result : searchResults) {
            String content = result.getContent();
            if (StringUtils.hasText(content)) {
                if (currentLength + content.length() > MAX_CONTEXT_LENGTH) {
                    break;
                }
                context.append(content).append("\n\n");
                currentLength += content.length();
            }
        }
        
        return context.toString().trim();
    }
    
    /**
     * 使用上下文生成回答
     */
    private String generateAnswerWithContext(String question, String context) {
        if (chatModel == null) {
            return generateFallbackAnswer(question, context);
        }

        // 初始化ChatClient（如果还没有初始化）
        if (chatClient == null) {
            chatClient = ChatClient.builder(chatModel).build();
        }

        try {
            String prompt = buildRAGPrompt(question, context);

            return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
            
        } catch (Exception e) {
            return generateFallbackAnswer(question, context);
        }
    }
    
    /**
     * 构建RAG提示词
     */
    private String buildRAGPrompt(String question, String context) {
        return String.format("""
            你是一个智慧交通领域的专家助手。请基于以下上下文信息回答用户问题。
            
            上下文信息：
            %s
            
            用户问题：%s
            
            请要求：
            1. 基于上下文信息进行回答
            2. 如果上下文信息不足，请说明并提供一般性建议
            3. 回答要专业、准确、有帮助
            4. 使用中文回答
            5. 如果涉及数据，请提供具体的数字和分析
            
            回答：
            """, context, question);
    }
    
    /**
     * 生成备用回答（AI不可用时）
     */
    private String generateFallbackAnswer(String question, String context) {
        if (StringUtils.hasText(context)) {
            return "根据相关信息，" + context.substring(0, Math.min(context.length(), 200)) + "...";
        } else {
            return "抱歉，暂时无法找到相关信息来回答您的问题。建议您尝试更具体的问题描述。";
        }
    }
    
    /**
     * 格式化数据查询回答
     */
    private String formatDataQueryAnswer(String question, NL2SQLService.QueryResult queryResult) {
        if (queryResult.getData() == null || queryResult.getData().isEmpty()) {
            return "查询完成，但没有找到相关数据。";
        }
        
        StringBuilder answer = new StringBuilder();
        answer.append("根据数据查询结果：\n\n");
        
        List<Map<String, Object>> data = queryResult.getData();
        int displayCount = Math.min(data.size(), 10); // 最多显示10条
        
        for (int i = 0; i < displayCount; i++) {
            Map<String, Object> row = data.get(i);
            answer.append(formatDataRow(row, i + 1));
        }
        
        if (data.size() > displayCount) {
            answer.append(String.format("\n... 还有 %d 条记录未显示", data.size() - displayCount));
        }
        
        answer.append(String.format("\n\n总计找到 %d 条记录。", data.size()));
        
        return answer.toString();
    }
    
    /**
     * 格式化数据行
     */
    private String formatDataRow(Map<String, Object> row, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(index).append(". ");
        
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                sb.append(entry.getKey()).append(": ").append(value).append(", ");
            }
        }
        
        // 移除最后的逗号和空格
        if (sb.length() > 3) {
            sb.setLength(sb.length() - 2);
        }
        
        sb.append("\n");
        return sb.toString();
    }
    
    /**
     * 生成分析回答
     */
    private String generateAnalysisAnswer(String question, NL2SQLService.QueryResult queryResult, 
                                        List<VectorStoreService.SearchResult> searchResults) {
        StringBuilder analysis = new StringBuilder();
        analysis.append("基于数据分析：\n\n");
        
        if (queryResult.isSuccess() && queryResult.getData() != null) {
            analysis.append(formatDataQueryAnswer(question, queryResult));
            analysis.append("\n\n分析建议：\n");
        }
        
        String context = buildContext(searchResults);
        if (StringUtils.hasText(context)) {
            analysis.append(context.substring(0, Math.min(context.length(), 500)));
        } else {
            analysis.append("建议结合具体业务场景进行深入分析。");
        }
        
        return analysis.toString();
    }
    
    /**
     * 生成推荐回答
     */
    private String generateRecommendationAnswer(String question, NL2SQLService.QueryResult queryResult,
                                              List<VectorStoreService.SearchResult> searchResults) {
        StringBuilder recommendation = new StringBuilder();
        recommendation.append("基于数据分析，为您提供以下建议：\n\n");
        
        String context = buildContext(searchResults);
        if (StringUtils.hasText(context)) {
            recommendation.append(context.substring(0, Math.min(context.length(), 400)));
        }
        
        if (queryResult.isSuccess() && queryResult.getData() != null) {
            recommendation.append("\n\n数据支撑：\n");
            recommendation.append(formatDataQueryAnswer(question, queryResult));
        }
        
        return recommendation.toString();
    }
    
    /**
     * 生成通用回答
     */
    private String generateGeneralAnswer(String question, NL2SQLService.QueryResult queryResult,
                                       List<VectorStoreService.SearchResult> searchResults) {
        String context = buildContext(searchResults);
        return generateAnswerWithContext(question, context);
    }
    
    // 关键词检测方法
    private boolean containsDataQueryKeywords(String question) {
        String[] keywords = {"多少", "数量", "统计", "查询", "显示", "列出", "有哪些", "什么时候", "哪里"};
        return Arrays.stream(keywords).anyMatch(question::contains);
    }
    
    private boolean containsKnowledgeKeywords(String question) {
        String[] keywords = {"什么是", "如何", "怎么", "为什么", "原因", "定义", "概念", "解释"};
        return Arrays.stream(keywords).anyMatch(question::contains);
    }
    
    private boolean containsAnalysisKeywords(String question) {
        String[] keywords = {"分析", "趋势", "对比", "比较", "影响", "关系", "原因分析", "深入"};
        return Arrays.stream(keywords).anyMatch(question::contains);
    }
    
    private boolean containsRecommendationKeywords(String question) {
        String[] keywords = {"建议", "推荐", "应该", "如何改进", "优化", "解决方案", "措施"};
        return Arrays.stream(keywords).anyMatch(question::contains);
    }
    
    // 缓存相关方法
    private AnswerResult getCachedAnswer(String question) {
        if (redisTemplate == null) {
            return null;
        }
        
        try {
            String cacheKey = CACHE_PREFIX + question.hashCode();
            return (AnswerResult) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            return null;
        }
    }
    
    private void cacheAnswer(String question, AnswerResult result) {
        if (redisTemplate == null || result == null) {
            return;
        }
        
        try {
            String cacheKey = CACHE_PREFIX + question.hashCode();
            redisTemplate.opsForValue().set(cacheKey, result, Duration.ofHours(CACHE_EXPIRE_HOURS));
        } catch (Exception e) {
            // 缓存失败不影响主流程
        }
    }
    /**
     * 构建增强的检索上下文，优先使用高相关性的SOP和专家知识
     */
    private String buildEnhancedContext(List<VectorStoreService.SearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        int currentLength = 0;

        // 优先处理SOP和专家知识
        for (VectorStoreService.SearchResult result : searchResults) {
            String content = result.getContent();
            String source = result.getSource();
            String metadata = result.getMetadata();

            // 优先处理来自SOP和专家知识的内容
            if (StringUtils.hasText(content) && isSOPorExpertKnowledge(source, metadata)) {
                if (currentLength + content.length() > MAX_CONTEXT_LENGTH) {
                    break;
                }
                context.append("[SOP/专家知识] ").append(content).append("\n\n");
                currentLength += content.length();
            }
        }

        // 如果上下文还不够长，添加其他相关内容
        if (currentLength < MAX_CONTEXT_LENGTH) {
            for (VectorStoreService.SearchResult result : searchResults) {
                String content = result.getContent();
                String source = result.getSource();
                String metadata = result.getMetadata();

                // 添加非SOP/专家知识的内容
                if (StringUtils.hasText(content) && !isSOPorExpertKnowledge(source, metadata)) {
                    if (currentLength + content.length() > MAX_CONTEXT_LENGTH) {
                        break;
                    }
                    context.append(content).append("\n\n");
                    currentLength += content.length();
                }
            }
        }

        return context.toString().trim();
    }

    /**
     * 判断是否为SOP或专家知识
     */
    private boolean isSOPorExpertKnowledge(String source, String metadata) {
        if (!StringUtils.hasText(source) && !StringUtils.hasText(metadata)) {
            return false;
        }

        // 检查来源是否包含SOP或专家知识标识
        String lowerSource = source != null ? source.toLowerCase() : "";
        String lowerMetadata = metadata != null ? metadata.toLowerCase() : "";

        return lowerSource.contains("sop") || lowerSource.contains("手册") ||
               lowerSource.contains("manual") || lowerSource.contains("handbook") ||
               lowerMetadata.contains("sop") || lowerMetadata.contains("专家") ||
               lowerMetadata.contains("expert") || lowerMetadata.contains("standard");
    }

    /**
     * 生成带有SOP引用的回答
     */
    private String generateAnswerWithSOPReference(String question, String context) {
        if (chatModel == null) {
            return generateFallbackAnswer(question, context);
        }

        // 初始化ChatClient（如果还没有初始化）
        if (chatClient == null) {
            chatClient = ChatClient.builder(chatModel).build();
        }

        try {
            String prompt = buildSOPReferencePrompt(question, context);

            return chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        } catch (Exception e) {
            return generateFallbackAnswer(question, context);
        }
    }

    /**
     * 构建带有SOP引用的提示词
     */
    private String buildSOPReferencePrompt(String question, String context) {
        return String.format("""
            你是一个智慧交通领域的专家助手。请基于以下上下文信息回答用户问题，特别注意其中的SOP（标准操作程序）和专家知识。
            
            上下文信息：
            %s
            
            用户问题：%s
            
            请要求：
            1. 基于上下文信息进行回答，特别是SOP和专家知识部分
            2. 如果引用了SOP或专家知识，请明确指出
            3. 如果上下文信息不足，请说明并提供一般性建议
            4. 回答要专业、准确、有帮助
            5. 使用中文回答
            6. 如果涉及数据，请提供具体的数字和分析
            
            回答：
            """, context, question);
    }

    /**
     * 查询意图枚举
     */
    public enum QueryIntent {
        DATA_QUERY,      // 数据查询
        KNOWLEDGE_QA,    // 知识问答
        ANALYSIS,        // 分析类
        RECOMMENDATION,  // 推荐类
        GENERAL,         // 通用
        UNKNOWN          // 未知
    }
    
    /**
     * 回答结果类
     */
    public static class AnswerResult {
        private boolean success;
        private String answer;
        private List<VectorStoreService.SearchResult> retrievedDocs;
        private QueryIntent intent;
        private List<Map<String, Object>> queryData;
        private String sql;
        private boolean fromCache = false;
        
        public AnswerResult(boolean success, String answer, List<VectorStoreService.SearchResult> retrievedDocs,
                           QueryIntent intent, List<Map<String, Object>> queryData, String sql) {
            this.success = success;
            this.answer = answer;
            this.retrievedDocs = retrievedDocs;
            this.intent = intent;
            this.queryData = queryData;
            this.sql = sql;
        }
        
        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
        public List<VectorStoreService.SearchResult> getRetrievedDocs() { return retrievedDocs; }
        public void setRetrievedDocs(List<VectorStoreService.SearchResult> retrievedDocs) { this.retrievedDocs = retrievedDocs; }
        public QueryIntent getIntent() { return intent; }
        public void setIntent(QueryIntent intent) { this.intent = intent; }
        public List<Map<String, Object>> getQueryData() { return queryData; }
        public void setQueryData(List<Map<String, Object>> queryData) { this.queryData = queryData; }
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        public boolean isFromCache() { return fromCache; }
        public void setFromCache(boolean fromCache) { this.fromCache = fromCache; }
    }
}
