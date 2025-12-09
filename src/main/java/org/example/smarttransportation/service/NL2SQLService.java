package org.example.smarttransportation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    
    private static final Logger logger = LoggerFactory.getLogger(NL2SQLService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired(required = false)
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    private ChatClient chatClient;
    
    // 数据库表结构信息 - 根据实际数据库表结构修正
    private static final String SCHEMA_INFO = """
        数据库表结构信息：
        
        1. citibike_trips_202402 - 共享单车出行数据 (2024年2月数据)
        字段：start_station_name, started_at, end_lat, end_lng, end_station_name, ended_at, start_lat, start_lng
        
        2. complaints - 城市投诉数据
        字段：unique_key, borough, created_at, latitude, longitude, agency, closed_at, complaint_type, descriptor, resolution_description, status
        
        3. nyc_traffic_accidents - 机动车碰撞事故 (注意：数据为2024年2月，包含重复字段名)
        主要字段：`CRASH DATE`, `CRASH TIME`, borough, `ZIP CODE`, latitude, longitude, `LOCATION`, `ON STREET NAME`, `CROSS STREET NAME`, 
               `OFF STREET NAME`, `NUMBER OF PERSONS INJURED`, `NUMBER OF PERSONS KILLED`, `NUMBER OF PEDESTRIANS INJURED`, 
               `NUMBER OF PEDESTRIANS KILLED`, `NUMBER OF CYCLIST INJURED`, `NUMBER OF CYCLIST KILLED`, 
               `NUMBER OF MOTORIST INJURED`, `NUMBER OF MOTORIST KILLED`, `CONTRIBUTING FACTOR VEHICLE 1`, 
               `CONTRIBUTING FACTOR VEHICLE 2`, collision_id, `VEHICLE TYPE CODE 1`, `VEHICLE TYPE CODE 2`, `CRASH_DATETIME`, created_at
        备用字段：crash_date, crash_time, cross_street_name, number_of_cyclist_injured, number_of_cyclist_killed,
               number_of_motorist_injured, number_of_motorist_killed, number_of_pedestrians_injured, 
               number_of_pedestrians_killed, number_of_persons_injured, number_of_persons_killed, off_street_name, on_street_name, unique_key
        
        4. nyc_permitted_events - 纽约许可活动数据 (注意：数据为2024年2月，包含重复字段名)
        主要字段：`Event ID`, `Event Name`, `Start Date/Time`, `End Date/Time`, `Event Borough`, `Event Location`, `Event Street Side`, 
               `Street Closure Type`, `Processed_Location`, `Location_Type`, latitude, longitude, geocode_query
        备用字段：event_id, borough, created_at, end_at, event_borough, event_location, event_name, event_street_side, 
               geocode_status, start_at, street_closure_type
        
        5. subway_ridership - 地铁客流数据 (注意：数据为2024年2月)
        字段：station_complex_id, transit_timestamp, borough, created_at, latitude, longitude, ridership, station_complex, stratum
        
        重要提示：
        - nyc_traffic_accidents和nyc_permitted_events表存在重复字段名（带空格的原始字段和下划线的标准化字段）
        - 优先使用带反引号的原始字段名（如`CRASH DATE`），它们包含完整的原始数据
        - 时间范围限制：所有查询必须限定在2024年2月1日至2024年2月29日
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
            String extractedSQL = extractSQL(sqlResult);

            // 如果AI生成的SQL为空或无效，回退到规则匹配
            if (!StringUtils.hasText(extractedSQL)) {
                logger.warn("AI生成的SQL为空，回退到规则匹配。原始响应: {}", sqlResult);
                return generateSQLByRules(naturalLanguageQuery);
            }

            return extractedSQL;

        } catch (Exception e) {
            // AI转换失败时，回退到规则匹配
            logger.warn("AI转换失败，回退到规则匹配: {}", e.getMessage());
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
            4. 数据仅包含2024年2月。如果用户指定具体日期，请生成针对该日期的精确查询（例如 WHERE `CRASH DATE` = '2024-02-01'）。否则查询整个2月。
            5. 如果涉及地理位置，可以使用latitude和longitude字段
            6. 不要添加LIMIT子句，返回所有符合条件的数据
            7. 注意：数据库中存储的是2024年2月的历史数据，不要查询最近的数据
            8. 如果查询结果可能很多，请按时间正序排序
            
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
                return "SELECT start_station_name, COUNT(*) as trip_count FROM citibike_trips_202402 GROUP BY start_station_name ORDER BY trip_count DESC";
            }
            if (lowerQuery.contains("时间") || lowerQuery.contains("duration")) {
                return "SELECT AVG(TIMESTAMPDIFF(MINUTE, started_at, ended_at)) as avg_duration FROM citibike_trips_202402 WHERE started_at IS NOT NULL AND ended_at IS NOT NULL";
            }
            return "SELECT * FROM citibike_trips_202402";
        }

        // 投诉相关查询
        if (lowerQuery.contains("投诉") || lowerQuery.contains("complaint")) {
            if (lowerQuery.contains("类型") || lowerQuery.contains("type")) {
                return "SELECT complaint_type, COUNT(*) as count FROM complaints GROUP BY complaint_type ORDER BY count DESC";
            }
            if (lowerQuery.contains("状态") || lowerQuery.contains("status")) {
                return "SELECT status, COUNT(*) as count FROM complaints GROUP BY status";
            }
            return "SELECT * FROM complaints";
        }

        // 事故相关查询
        if (lowerQuery.contains("事故") || lowerQuery.contains("collision") || lowerQuery.contains("accident")) {
            if (lowerQuery.contains("多少") || lowerQuery.contains("数量") || lowerQuery.contains("统计") || lowerQuery.contains("count") || lowerQuery.contains("how many")) {
                return "SELECT COUNT(*) as total_accidents FROM nyc_traffic_accidents WHERE `CRASH DATE` >= '2024-02-01' AND `CRASH DATE` <= '2024-02-29'";
            }
            if (lowerQuery.contains("伤亡") || lowerQuery.contains("injured") || lowerQuery.contains("killed")) {
                return "SELECT SUM(`NUMBER OF PERSONS INJURED`) as total_injured, SUM(`NUMBER OF PERSONS KILLED`) as total_killed FROM nyc_traffic_accidents WHERE `CRASH DATE` >= '2024-02-01' AND `CRASH DATE` <= '2024-02-29'";
            }
            if (lowerQuery.contains("区域") || lowerQuery.contains("borough")) {
                return "SELECT `BOROUGH`, COUNT(*) as accident_count FROM nyc_traffic_accidents WHERE `BOROUGH` IS NOT NULL AND `CRASH DATE` >= '2024-02-01' AND `CRASH DATE` <= '2024-02-29' GROUP BY `BOROUGH` ORDER BY accident_count DESC";
            }
            if (lowerQuery.contains("严重") || lowerQuery.contains("严重事故")) {
                return "SELECT * FROM nyc_traffic_accidents WHERE (`NUMBER OF PERSONS KILLED` > 0 OR `NUMBER OF PERSONS INJURED` >= 3) AND `CRASH DATE` >= '2024-02-01' AND `CRASH DATE` <= '2024-02-29' ORDER BY `NUMBER OF PERSONS KILLED` DESC, `NUMBER OF PERSONS INJURED` DESC";
            }
            return "SELECT * FROM nyc_traffic_accidents WHERE `CRASH DATE` >= '2024-02-01' AND `CRASH DATE` <= '2024-02-29'";
        }

        // 地铁相关查询
        if (lowerQuery.contains("地铁") || lowerQuery.contains("subway") || lowerQuery.contains("客流")) {
            if (lowerQuery.contains("站点") || lowerQuery.contains("station")) {
                return "SELECT station_complex, AVG(ridership) as avg_ridership FROM subway_ridership WHERE transit_timestamp >= '2024-02-01' AND transit_timestamp <= '2024-02-29' GROUP BY station_complex ORDER BY avg_ridership DESC";
            }
            if (lowerQuery.contains("客流量") || lowerQuery.contains("ridership")) {
                return "SELECT DATE(transit_timestamp) as date, SUM(ridership) as total_ridership FROM subway_ridership WHERE transit_timestamp >= '2024-02-01' AND transit_timestamp <= '2024-02-29' GROUP BY DATE(transit_timestamp) ORDER BY date DESC";
            }
            return "SELECT * FROM subway_ridership WHERE transit_timestamp >= '2024-02-01' AND transit_timestamp <= '2024-02-29'";
        }

        // 活动相关查询
        if (lowerQuery.contains("活动") || lowerQuery.contains("event")) {
            if (lowerQuery.contains("类型") || lowerQuery.contains("type")) {
                return "SELECT `Event Name`, COUNT(*) as count FROM nyc_permitted_events WHERE `Start Date/Time` >= '2024-02-01' AND `Start Date/Time` <= '2024-02-29' GROUP BY `Event Name` ORDER BY count DESC";
            }
            if (lowerQuery.contains("时间") || lowerQuery.contains("近期")) {
                return "SELECT * FROM nyc_permitted_events WHERE `Start Date/Time` >= '2024-02-01' AND `Start Date/Time` <= '2024-02-29' ORDER BY `Start Date/Time` DESC";
            }
            return "SELECT * FROM nyc_permitted_events WHERE `Start Date/Time` >= '2024-02-01' AND `Start Date/Time` <= '2024-02-29'";
        }

        // 默认查询
        return "SELECT 'citibike_trips_202402' as table_name, COUNT(*) as record_count FROM citibike_trips_202402 " +
               "UNION ALL SELECT 'complaints', COUNT(*) FROM complaints " +
               "UNION ALL SELECT 'nyc_traffic_accidents', COUNT(*) FROM nyc_traffic_accidents WHERE `CRASH DATE` >= '2024-02-01' AND `CRASH DATE` <= '2024-02-29' " +
               "UNION ALL SELECT 'nyc_permitted_events', COUNT(*) FROM nyc_permitted_events WHERE `Start Date/Time` >= '2024-02-01' AND `Start Date/Time` <= '2024-02-29' " +
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
     * 验证SQL语句是否有效（基本检查和字段验证）
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

        // 验证字段名是否存在于实际表中
        return validateFieldNames(sql);
    }

    /**
     * 验证SQL中的字段名是否存在于实际表结构中
     */
    private boolean validateFieldNames(String sql) {
        try {
            // 基本的字段名验证 - 检查常见的错误字段名
            String lowerSQL = sql.toLowerCase();

            // 检查nyc_traffic_accidents表的字段
            if (lowerSQL.contains("nyc_traffic_accidents")) {
                // 检查是否使用了不推荐的字段名格式（仅警告，不阻止）
                if (lowerSQL.contains("crash_date") && !lowerSQL.contains("`crash date`") && !lowerSQL.contains("`crash_datetime`")) {
                    logger.warn("建议使用 `CRASH DATE` 或 `CRASH_DATETIME` 字段");
                }
                if (lowerSQL.contains("number_of_persons_injured") && !lowerSQL.contains("`number of persons injured`")) {
                    logger.warn("建议使用 `NUMBER OF PERSONS INJURED` 而不是 number_of_persons_injured");
                }

                // 检查是否使用了真正不存在的字段名
                // 注意：crash_datetime、crash_date等字段实际存在（有大写版本），不应阻止
                String[] invalidFields = {"borough_name", "accident_id", "crash_location_id"};
                for (String field : invalidFields) {
                    if (lowerSQL.contains(field)) {
                        logger.error("使用了不存在的字段: {}", field);
                        return false;
                    }
                }
            }

            // 检查nyc_permitted_events表的字段
            if (lowerSQL.contains("nyc_permitted_events")) {
                // 检查是否使用了不推荐的字段名格式
                if (lowerSQL.contains("start_at") && !lowerSQL.contains("`start date/time`")) {
                    logger.warn("建议使用 `Start Date/Time` 而不是 start_at");
                }
                if (lowerSQL.contains("event_name") && !lowerSQL.contains("`event name`")) {
                    logger.warn("建议使用 `Event Name` 而不是 event_name");
                }

                // 检查是否使用了不存在的字段名
                String[] invalidFields = {"event_type", "permit_id", "location_id"};
                for (String field : invalidFields) {
                    if (lowerSQL.contains(field)) {
                        logger.error("使用了不存在的字段: {}", field);
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            logger.error("字段验证过程出错: {}", e.getMessage());
            // 如果验证过程出错，返回true以避免阻塞正常查询
            return true;
        }
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