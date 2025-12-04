package org.example.smarttransportation.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自然语言转SQL查询服务
 * 将用户的自然语言问题转换为SQL查询并执行
 * 
 * @author pojin
 * @date 2025/11/22
 */
@Service
public class NL2SQLService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired(required = false)
    private ChatModel chatModel;

    private ChatClient chatClient;
    
    // 数据库表结构信息
    private static final String SCHEMA_INFO = """
        数据库表结构信息：
        
        1. citibike_trips_202402 - 共享单车出行数据 (2024年2月数据)
        字段：started_at, start_station_name, ended_at, end_station_name, start_lat, start_lng, end_lat, end_lng
        
        2. complaints - 城市投诉数据
        字段：unique_key, closed_at, agency, complaint_type, descriptor, status, resolution_description, latitude, longitude, borough, created_at
        
        3. nyc_traffic_accidents - 机动车碰撞事故 (注意：数据为2024年2月)
        字段：collision_id, crash_date, crash_time, borough, latitude, longitude, on_street_name, cross_street_name, 
               off_street_name, number_of_persons_injured, number_of_persons_killed, number_of_pedestrians_injured, 
               number_of_pedestrians_killed, number_of_cyclist_injured, number_of_cyclist_killed, 
               number_of_motorist_injured, number_of_motorist_killed, contributing_factor_vehicle_1, 
               contributing_factor_vehicle_2, vehicle_type_code1, vehicle_type_code2
        
        4. nyc_permitted_events - 纽约许可活动数据 (注意：数据为2024年2月)
        字段：event_id, event_name, start_at, end_at, event_borough, event_location, event_street_side, 
               street_closure_type, latitude, longitude, geocode_query, geocode_status
        
        5. subway_ridership - 地铁客流数据 (注意：数据为2024年2月)
        字段：transit_timestamp, station_complex_id, station_complex, borough, ridership, latitude, longitude, stratum
        """;

    /**
     * 将自然语言问题转换为SQL查询
     */
    public String generateSQL(String naturalLanguageQuery) {
        if (!StringUtils.hasText(naturalLanguageQuery)) {
            throw new IllegalArgumentException("查询问题不能为空");
        }

        if (chatModel == null) {
            // 如果没有配置AI模型，使用规则匹配
            return generateSQLByRules(naturalLanguageQuery);
        }

        // 初始化ChatClient（如果还没有初始化）
        if (chatClient == null) {
            chatClient = ChatClient.builder(chatModel).build();
        }

        try {
            String prompt = buildNL2SQLPrompt(naturalLanguageQuery);

            String sqlResult = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

            // 提取SQL语句
            return extractSQL(sqlResult);

        } catch (Exception e) {
            // AI转换失败时，回退到规则匹配
            return generateSQLByRules(naturalLanguageQuery);
        }
    }

    /**
     * 执行SQL查询并返回结果
     */
    public QueryResult executeQuery(String naturalLanguageQuery) {
        try {
            String sql = generateSQL(naturalLanguageQuery);

            if (!StringUtils.hasText(sql)) {
                return new QueryResult(false, "无法生成有效的SQL查询", null, null);
            }

            // 验证SQL安全性
            if (!isSafeSQL(sql)) {
                return new QueryResult(false, "SQL查询包含不安全的操作", null, sql);
            }

            // 执行查询
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

            return new QueryResult(true, "查询成功", results, sql);

        } catch (Exception e) {
            return new QueryResult(false, "查询执行失败: " + e.getMessage(), null, null);
        }
    }

    /**
     * 构建NL2SQL的提示词
     */
    private String buildNL2SQLPrompt(String query) {
        return String.format("""
            你是一个专业的SQL查询生成器，专门处理智慧交通数据查询。
            
            %s
            
            用户问题：%s
            
            请根据用户问题生成对应的SQL查询语句。要求：
            1. 只返回SQL语句，不要其他解释
            2. 使用标准的MySQL语法
            3. 确保查询安全，只允许SELECT操作
            4. 所有时间相关的查询必须限定在2024年2月1日至2024年2月29日范围内
            5. 如果涉及地理位置，可以使用latitude和longitude字段
            6. 限制返回结果数量，添加LIMIT子句（建议100以内）
            7. 注意：数据库中存储的是2024年2月的历史数据，不要查询最近的数据
            
            SQL查询：
            """, SCHEMA_INFO, query);
    }

    /**
     * 基于规则的SQL生成（AI不可用时的备选方案）
     */
    private String generateSQLByRules(String query) {
        String lowerQuery = query.toLowerCase();

        // 共享单车相关查询
        if (lowerQuery.contains("单车") || lowerQuery.contains("citibike") || lowerQuery.contains("bike")) {
            if (lowerQuery.contains("站点") || lowerQuery.contains("station")) {
                return "SELECT start_station_name, COUNT(*) as trip_count FROM citibike_trips_202402 GROUP BY start_station_name ORDER BY trip_count DESC LIMIT 10";
            }
            if (lowerQuery.contains("时间") || lowerQuery.contains("duration")) {
                return "SELECT AVG(TIMESTAMPDIFF(MINUTE, started_at, ended_at)) as avg_duration FROM citibike_trips_202402 WHERE started_at IS NOT NULL AND ended_at IS NOT NULL LIMIT 100";
            }
            return "SELECT * FROM citibike_trips_202402 LIMIT 10";
        }

        // 投诉相关查询
        if (lowerQuery.contains("投诉") || lowerQuery.contains("complaint")) {
            if (lowerQuery.contains("类型") || lowerQuery.contains("type")) {
                return "SELECT complaint_type, COUNT(*) as count FROM complaints GROUP BY complaint_type ORDER BY count DESC LIMIT 10";
            }
            if (lowerQuery.contains("状态") || lowerQuery.contains("status")) {
                return "SELECT status, COUNT(*) as count FROM complaints GROUP BY status LIMIT 10";
            }
            return "SELECT * FROM complaints LIMIT 10";
        }

        // 事故相关查询
        if (lowerQuery.contains("事故") || lowerQuery.contains("collision") || lowerQuery.contains("accident")) {
            if (lowerQuery.contains("伤亡") || lowerQuery.contains("injured") || lowerQuery.contains("killed")) {
                return "SELECT SUM(number_of_persons_injured) as total_injured, SUM(number_of_persons_killed) as total_killed FROM nyc_traffic_accidents WHERE crash_date >= '2024-02-01' AND crash_date <= '2024-02-29'";
            }
            if (lowerQuery.contains("区域") || lowerQuery.contains("borough")) {
                return "SELECT borough, COUNT(*) as accident_count FROM nyc_traffic_accidents WHERE borough IS NOT NULL AND crash_date >= '2024-02-01' AND crash_date <= '2024-02-29' GROUP BY borough ORDER BY accident_count DESC LIMIT 10";
            }
            if (lowerQuery.contains("严重") || lowerQuery.contains("严重事故")) {
                return "SELECT * FROM nyc_traffic_accidents WHERE (number_of_persons_killed > 0 OR number_of_persons_injured >= 3) AND crash_date >= '2024-02-01' AND crash_date <= '2024-02-29' ORDER BY number_of_persons_killed DESC, number_of_persons_injured DESC LIMIT 100";
            }
            return "SELECT * FROM nyc_traffic_accidents WHERE crash_date >= '2024-02-01' AND crash_date <= '2024-02-29' LIMIT 10";
        }

        // 地铁相关查询
        if (lowerQuery.contains("地铁") || lowerQuery.contains("subway") || lowerQuery.contains("客流")) {
            if (lowerQuery.contains("站点") || lowerQuery.contains("station")) {
                return "SELECT station_complex, AVG(ridership) as avg_ridership FROM subway_ridership WHERE transit_timestamp >= '2024-02-01' AND transit_timestamp <= '2024-02-29' GROUP BY station_complex ORDER BY avg_ridership DESC LIMIT 10";
            }
            if (lowerQuery.contains("客流量") || lowerQuery.contains("ridership")) {
                return "SELECT DATE(transit_timestamp) as date, SUM(ridership) as total_ridership FROM subway_ridership WHERE transit_timestamp >= '2024-02-01' AND transit_timestamp <= '2024-02-29' GROUP BY DATE(transit_timestamp) ORDER BY date DESC LIMIT 10";
            }
            return "SELECT * FROM subway_ridership WHERE transit_timestamp >= '2024-02-01' AND transit_timestamp <= '2024-02-29' LIMIT 10";
        }

        // 活动相关查询
        if (lowerQuery.contains("活动") || lowerQuery.contains("event")) {
            if (lowerQuery.contains("类型") || lowerQuery.contains("type")) {
                return "SELECT event_name, COUNT(*) as count FROM nyc_permitted_events WHERE start_at >= '2024-02-01' AND start_at <= '2024-02-29' GROUP BY event_name ORDER BY count DESC LIMIT 10";
            }
            if (lowerQuery.contains("时间") || lowerQuery.contains("近期")) {
                return "SELECT * FROM nyc_permitted_events WHERE start_at >= '2024-02-01' AND start_at <= '2024-02-29' ORDER BY start_at DESC LIMIT 10";
            }
            return "SELECT * FROM nyc_permitted_events WHERE start_at >= '2024-02-01' AND start_at <= '2024-02-29' LIMIT 10";
        }

        // 默认查询
        return "SELECT 'citibike_trips_202402' as table_name, COUNT(*) as record_count FROM citibike_trips_202402 " +
               "UNION ALL SELECT 'complaints', COUNT(*) FROM complaints " +
               "UNION ALL SELECT 'nyc_traffic_accidents', COUNT(*) FROM nyc_traffic_accidents WHERE crash_date >= '2024-02-01' AND crash_date <= '2024-02-29' " +
               "UNION ALL SELECT 'nyc_permitted_events', COUNT(*) FROM nyc_permitted_events WHERE start_at >= '2024-02-01' AND start_at <= '2024-02-29' " +
               "UNION ALL SELECT 'subway_ridership', COUNT(*) FROM subway_ridership WHERE transit_timestamp >= '2024-02-01' AND transit_timestamp <= '2024-02-29'";
    }

    /**
     * 从AI响应中提取SQL语句
     */
    private String extractSQL(String response) {
        if (!StringUtils.hasText(response)) {
            return "";
        }

        // 尝试提取SQL代码块
        Pattern sqlPattern = Pattern.compile("```sql\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
        Matcher matcher = sqlPattern.matcher(response);

        if (matcher.find()) {
            String sql = matcher.group(1).trim();
            if (isValidSQL(sql)) {
                return sql;
            }
        }

        // 尝试提取普通代码块
        Pattern codePattern = Pattern.compile("```([\\s\\S]*?)```");
        matcher = codePattern.matcher(response);

        if (matcher.find()) {
            String sql = matcher.group(1).trim();
            if (isValidSQL(sql)) {
                return sql;
            }
        }

        // 查找完整的SELECT语句（从SELECT到分号或字符串结尾）
        Pattern selectPattern = Pattern.compile("(SELECT[\\s\\S]*?)(?:;|$)", Pattern.CASE_INSENSITIVE);
        matcher = selectPattern.matcher(response);

        if (matcher.find()) {
            String sql = matcher.group(1).trim();
            if (isValidSQL(sql)) {
                return sql;
            }
        }

        // 如果都没有找到有效的SQL，检查响应是否本身就是一个SQL语句
        String trimmedResponse = response.trim();
        if (isValidSQL(trimmedResponse)) {
            return trimmedResponse;
        }

        return "";
    }

    /**
     * 验证SQL语句是否有效（基本检查）
     */
    private boolean isValidSQL(String sql) {
        if (!StringUtils.hasText(sql)) {
            return false;
        }

        String upperSQL = sql.toUpperCase().trim();

        // 必须以SELECT开头
        if (!upperSQL.startsWith("SELECT")) {
            return false;
        }

        // 必须包含FROM关键字（除非是简单的SELECT常量）
        if (!upperSQL.contains("FROM") && !upperSQL.matches("SELECT\\s+[^\\s]+\\s*")) {
            return false;
        }

        // 不能只是"SELECT"
        if (upperSQL.equals("SELECT")) {
            return false;
        }

        return true;
    }

    /**
     * 验证SQL安全性
     */
    private boolean isSafeSQL(String sql) {
        if (!StringUtils.hasText(sql)) {
            return false;
        }

        String upperSQL = sql.toUpperCase().trim();

        // 只允许SELECT查询
        if (!upperSQL.startsWith("SELECT")) {
            return false;
        }

        // 禁止的关键词（但允许UNION ALL用于统计查询）
        String[] forbiddenKeywords = {
            "DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "CREATE", "TRUNCATE",
            "EXEC", "EXECUTE", "SCRIPT", "JAVASCRIPT", "VBSCRIPT"
        };

        for (String keyword : forbiddenKeywords) {
            if (upperSQL.contains(keyword)) {
                return false;
            }
        }

        // 特殊处理：允许UNION ALL但不允许单独的UNION
        if (upperSQL.contains("UNION") && !upperSQL.contains("UNION ALL")) {
            return false;
        }

        return true;
    }

    /**
     * 获取查询建议
     */
    public List<String> getQuerySuggestions() {
        return Arrays.asList(
            "最繁忙的共享单车站点有哪些？",
            "交通事故主要发生在哪些区域？",
            "最常见的投诉类型是什么？",
            "地铁客流量最高的站点？",
            "本月有哪些道路封闭活动？",
            "共享单车的平均使用时长？",
            "各区域的事故伤亡情况？",
            "投诉处理的平均时间？"
        );
    }

    /**
     * 检查NL2SQL服务是否可用
     */
    public boolean isNL2SQLServiceAvailable() {
        return jdbcTemplate != null;
    }

    /**
     * 查询结果类
     */
    public static class QueryResult {
        private boolean success;
        private String message;
        private List<Map<String, Object>> data;
        private String sql;

        public QueryResult(boolean success, String message, List<Map<String, Object>> data, String sql) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.sql = sql;
        }

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<Map<String, Object>> getData() { return data; }
        public void setData(List<Map<String, Object>> data) { this.data = data; }
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }

        public int getRowCount() {
            return data != null ? data.size() : 0;
        }
    }
}