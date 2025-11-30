package org.example.smarttransportation.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天响应DTO
 * 
 * @author pojin
 * @date 2025/11/23
 */
public class ChatResponse {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * AI回复消息
     */
    private String message;

    /**
     * 响应时间
     */
    private LocalDateTime timestamp;

    /**
     * 是否涉及数据查询
     */
    private Boolean involvesDataQuery = false;

    /**
     * 查询的数据表
     */
    private List<String> queriedTables;

    /**
     * 数据查询结果摘要
     */
    private String dataQuerySummary;

    /**
     * 处理耗时（毫秒）
     */
    private Long processingTimeMs;

    /**
     * 错误信息（如果有）
     */
    private String error;

    /**
     * 图表数据（用于前端展示）
     */
    private List<ChartData> charts;

    /**
     * 是否成功
     */
    private Boolean success = true;

    public ChatResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ChatResponse(String sessionId, String message) {
        this();
        this.sessionId = sessionId;
        this.message = message;
    }

    // 静态工厂方法
    public static ChatResponse success(String sessionId, String message) {
        return new ChatResponse(sessionId, message);
    }

    public static ChatResponse error(String sessionId, String error) {
        ChatResponse response = new ChatResponse(sessionId, "抱歉，处理您的请求时遇到了问题。");
        response.setError(error);
        response.setSuccess(false);
        return response;
    }

    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getInvolvesDataQuery() {
        return involvesDataQuery;
    }

    public void setInvolvesDataQuery(Boolean involvesDataQuery) {
        this.involvesDataQuery = involvesDataQuery;
    }

    public List<String> getQueriedTables() {
        return queriedTables;
    }

    public void setQueriedTables(List<String> queriedTables) {
        this.queriedTables = queriedTables;
    }

    public String getDataQuerySummary() {
        return dataQuerySummary;
    }

    public void setDataQuerySummary(String dataQuerySummary) {
        this.dataQuerySummary = dataQuerySummary;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<ChartData> getCharts() {
        return charts;
    }

    public void setCharts(List<ChartData> charts) {
        this.charts = charts;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
}
