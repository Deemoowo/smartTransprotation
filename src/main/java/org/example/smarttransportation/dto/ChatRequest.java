package org.example.smarttransportation.dto;

/**
 * 聊天请求DTO
 * 
 * @author pojin
 * @date 2025/11/23
 */
public class ChatRequest {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户消息
     */
    private String message;

    /**
     * 是否需要上下文
     */
    private Boolean includeContext = true;

    /**
     * 最大上下文轮数
     */
    private Integer maxContextRounds = 5;

    /**
     * 是否启用深度搜索
     */
    private Boolean enableSearch = false;

    public ChatRequest() {}

    public ChatRequest(String sessionId, String message) {
        this.sessionId = sessionId;
        this.message = message;
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

    public Boolean getIncludeContext() {
        return includeContext;
    }

    public void setIncludeContext(Boolean includeContext) {
        this.includeContext = includeContext;
    }

    public Integer getMaxContextRounds() {
        return maxContextRounds;
    }

    public void setMaxContextRounds(Integer maxContextRounds) {
        this.maxContextRounds = maxContextRounds;
    }

    public Boolean getEnableSearch() {
        return enableSearch;
    }

    public void setEnableSearch(Boolean enableSearch) {
        this.enableSearch = enableSearch;
    }
}
