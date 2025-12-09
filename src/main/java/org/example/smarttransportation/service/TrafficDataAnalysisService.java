package org.example.smarttransportation.service;

import org.example.smarttransportation.entity.TrafficAccident;
import org.example.smarttransportation.entity.WeatherData;
import org.example.smarttransportation.entity.PermittedEvent;
import org.example.smarttransportation.entity.SubwayRidership;
import org.example.smarttransportation.repository.TrafficAccidentRepository;
import org.example.smarttransportation.repository.WeatherDataRepository;
import org.example.smarttransportation.repository.PermittedEventRepository;
import org.example.smarttransportation.repository.SubwayRidershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.example.smarttransportation.dto.ChartData;
import java.util.ArrayList;

/**
 * 交通数据分析服务
 * 为AI助手提供数据查询和分析能力
 *
 * @author pojin
 * @date 2025/11/23
 */
@Service
public class TrafficDataAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(TrafficDataAnalysisService.class);

    @Autowired
    private TrafficAccidentRepository trafficAccidentRepository;

    @Autowired
    private WeatherDataRepository weatherDataRepository;

    @Autowired
    private PermittedEventRepository permittedEventRepository;

    @Autowired
    private SubwayRidershipRepository subwayRidershipRepository;

    @Autowired
    private NL2SQLService nl2SQLService;
    
    @Autowired
    private MetadataCacheService metadataCacheService;

    /**
     * 分析用户查询并返回相关数据摘要 (兼容旧接口)
     */
    public String analyzeUserQuery(String userQuery) {
        return analyzeUserQuery(userQuery, null);
    }

    /**
     * 分析用户查询并返回相关数据摘要 (支持元数据缓存)
     */
    public String analyzeUserQuery(String userQuery, String sessionId) {
        try {
            if (sessionId != null) {
                metadataCacheService.addThought(sessionId, "收到交通数据查询请求: " + userQuery);
                metadataCacheService.addThought(sessionId, "正在将自然语言转换为 SQL 查询...");
            }

            // 首先尝试使用NL2SQL服务处理查询
            NL2SQLService.QueryResult queryResult = nl2SQLService.executeQuery(userQuery);

            if (queryResult.isSuccess() && queryResult.getData() != null && !queryResult.getData().isEmpty()) {
                
                // 如果有sessionId，生成图表并缓存
                if (sessionId != null) {
                    metadataCacheService.addThought(sessionId, "SQL生成成功: " + queryResult.getSql());
                    metadataCacheService.addThought(sessionId, "数据库查询成功，获取到 " + queryResult.getData().size() + " 条记录");
                    
                    List<ChartData> charts = buildChartsFromQueryData(queryResult.getData());
                    if (!charts.isEmpty()) {
                        metadataCacheService.addCharts(sessionId, charts);
                        metadataCacheService.setSummary(sessionId, "已查询交通相关数据并整合到回答中");
                        metadataCacheService.addThought(sessionId, "已根据查询结果生成 " + charts.size() + " 张可视化图表");
                    }
                    // 简单推断查询的表名（这里简化处理，实际可以从SQL解析）
                    List<String> tables = new ArrayList<>();
                    if (queryResult.getSql().toLowerCase().contains("accident")) {
                        tables.add("nyc_traffic_accidents");
                    }
                    if (queryResult.getSql().toLowerCase().contains("subway")) {
                        tables.add("subway_ridership");
                    }
                    metadataCacheService.addQueriedTables(sessionId, tables);
                }

                StringBuilder analysis = new StringBuilder();
                analysis.append("【数据查询结果】\n");

                // 显示执行的SQL（用于调试）
                if (queryResult.getSql() != null) {
                    logger.info("执行的SQL: {}", queryResult.getSql());
                }

                // 格式化查询结果
                List<Map<String, Object>> data = queryResult.getData();
                analysis.append(String.format("查询到 %d 条记录。\n", data.size()));

                // 智能摘要：按 data_type 分组统计（如果存在）
                Map<String, List<Map<String, Object>>> groupedData = data.stream()
                    .collect(Collectors.groupingBy(row -> {
                        Object type = row.get("data_type");
                        return type != null ? type.toString() : "default";
                    }));

                if (groupedData.size() > 1) {
                    analysis.append("数据分布：\n");
                    groupedData.forEach((type, list) -> 
                        analysis.append(String.format("- %s: %d 条\n", type, list.size()))
                    );
                }

                analysis.append("\n【详细数据采样】\n");
                
                // 对每种类型的数据展示前 5 条
                for (Map.Entry<String, List<Map<String, Object>>> entry : groupedData.entrySet()) {
                    String type = entry.getKey();
                    List<Map<String, Object>> rows = entry.getValue();
                    
                    if (!"default".equals(type)) {
                        analysis.append(String.format("\n--- %s (前5条) ---\n", type));
                    }

                    int displayCount = Math.min(rows.size(), 5);
                    for (int i = 0; i < displayCount; i++) {
                        Map<String, Object> row = rows.get(i);
                        analysis.append(String.format("%d. ", i + 1));

                        // 格式化每行数据
                        for (Map.Entry<String, Object> field : row.entrySet()) {
                            String key = field.getKey();
                            Object value = field.getValue();
                            // 跳过 data_type 字段，因为已经在标题中显示了
                            if ("data_type".equals(key)) {
                                continue;
                            }
                            // 跳过空值
                            if (value == null) {
                                continue;
                            }

                            String displayKey = simplifyFieldName(key);
                            analysis.append(String.format("%s: %s, ", displayKey, value));
                        }

                        // 移除最后的逗号和空格
                        if (analysis.length() > 2) {
                            int lastComma = analysis.lastIndexOf(", ");
                            if (lastComma > 0 && lastComma == analysis.length() - 2) {
                                analysis.setLength(lastComma);
                            }
                        }
                        analysis.append("\n");
                    }
                }

                return analysis.toString();
            } else {
                // NL2SQL查询失败，回退到传统分析方法
                logger.warn("NL2SQL查询失败: {}", queryResult.getMessage());
                return analyzeUserQueryTraditional(userQuery);
            }

        } catch (Exception e) {
            logger.error("NL2SQL查询异常: {}", e.getMessage());
            // 出现异常时回退到传统分析方法
            return analyzeUserQueryTraditional(userQuery);
        }
    }

    /**
     * 传统的数据分析方法（作为NL2SQL的备选方案）
     */
    private String analyzeUserQueryTraditional(String userQuery) {
        StringBuilder analysis = new StringBuilder();
        String query = userQuery.toLowerCase();

        try {
            // 分析事故相关查询
            if (containsKeywords(query, "事故", "accident", "碰撞", "crash")) {
                String accidentAnalysis = analyzeAccidents(query);
                if (!accidentAnalysis.isEmpty()) {
                    analysis.append("【交通事故分析】\n").append(accidentAnalysis).append("\n\n");
                }
            }

            // 分析天气相关查询
            if (containsKeywords(query, "天气", "weather", "雨", "雪", "风")) {
                String weatherAnalysis = analyzeWeather(query);
                if (!weatherAnalysis.isEmpty()) {
                    analysis.append("【天气数据分析】\n").append(weatherAnalysis).append("\n\n");
                }
            }

            // 分析地铁客流查询
            if (containsKeywords(query, "地铁", "subway", "客流", "ridership")) {
                String ridershipAnalysis = analyzeRidership(query);
                if (!ridershipAnalysis.isEmpty()) {
                    analysis.append("【地铁客流分析】\n").append(ridershipAnalysis).append("\n\n");
                }
            }

            // 分析许可事件查询
            if (containsKeywords(query, "事件", "event", "许可", "permit", "活动")) {
                String eventAnalysis = analyzeEvents(query);
                if (!eventAnalysis.isEmpty()) {
                    analysis.append("【许可事件分析】\n").append(eventAnalysis).append("\n\n");
                }
            }

            // 如果没有具体数据查询，提供总体概况
            if (analysis.length() == 0) {
                analysis.append(getGeneralOverview());
            }

        } catch (Exception e) {
            logger.error("传统数据分析失败: {}", e.getMessage());
            return "数据查询暂时不可用，请稍后再试。";
        }

        return analysis.toString().trim();
    }

    private List<ChartData> buildChartsFromQueryData(List<Map<String, Object>> queryData) {
        List<ChartData> charts = new ArrayList<>();
        if (queryData == null || queryData.isEmpty()) {
            return charts;
        }

        // 检查是否存在 data_type 字段
        boolean hasDataType = queryData.get(0).containsKey("data_type");

        if (hasDataType) {
            // 按 data_type 分组生成图表
            Map<String, List<Map<String, Object>>> grouped = queryData.stream()
                .collect(Collectors.groupingBy(row -> {
                    Object val = row.get("data_type");
                    return val != null ? val.toString() : "default";
                }));
            
            for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
                String type = entry.getKey();
                List<Map<String, Object>> groupData = entry.getValue();
                charts.addAll(generateChartsForData(groupData, type));
            }
        } else {
            charts.addAll(generateChartsForData(queryData, null));
        }
        
        return charts;
    }

    private List<ChartData> generateChartsForData(List<Map<String, Object>> queryData, String groupName) {
        List<ChartData> charts = new ArrayList<>();
        if (queryData == null || queryData.isEmpty()) {
            return charts;
        }

        Map<String, Object> firstRow = queryData.get(0);
        String labelKey = null;
        String valueKey = null;

        // 自动推断图表类型和数据列
        for (Map.Entry<String, Object> entry : firstRow.entrySet()) {
            String key = entry.getKey();
            // 如果已经按 data_type 分组，则不再将其作为 label，除非没有其他选择
            if ("data_type".equals(key) && groupName != null && firstRow.size() > 2) {
                continue;
            }

            if (labelKey == null && !(entry.getValue() instanceof Number)) {
                labelKey = key;
            }
            if (valueKey == null && entry.getValue() instanceof Number) {
                valueKey = key;
            }
        }

        // 如果没找到 labelKey，尝试使用 data_type 或者任意 key
        if (labelKey == null && !firstRow.isEmpty()) {
             // 优先找非数字
             for (String key : firstRow.keySet()) {
                 if (!"data_type".equals(key) || groupName == null) {
                     labelKey = key;
                     break;
                 }
             }
             // 实在不行就用第一个
             if (labelKey == null) {
                 labelKey = firstRow.keySet().iterator().next();
             }
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

        String displayValueKey = simplifyFieldName(valueKey);
        String displayLabelKey = simplifyFieldName(labelKey);
        String title = displayValueKey + "统计 (按" + displayLabelKey + ")";
        
        if (groupName != null) {
            title = groupName + " - " + title;
        }

        ChartData chart = ChartData.singleSeries(
            title,
            chartType,
            labels,
            values,
            valueKey
        );
        charts.add(chart);
        return charts;
    }

    /**
     * 简化字段名显示
     */
    private String simplifyFieldName(String fieldName) {
        if (fieldName == null) {
            return "未知";
        }

        // 处理常见的字段名
        switch (fieldName.toLowerCase()) {
            case "crash date":
            case "`crash date`":
                return "事故日期";
            case "number of persons injured":
            case "`number of persons injured`":
                return "受伤人数";
            case "number of persons killed":
            case "`number of persons killed`":
                return "死亡人数";
            case "on street name":
            case "`on street name`":
                return "街道名称";
            case "borough":
                return "行政区";
            case "start_station_name":
                return "起始站点";
            case "trip_count":
                return "出行次数";
            case "event name":
            case "`event name`":
                return "事件名称";
            case "start date/time":
            case "`start date/time`":
                return "开始时间";
            case "source_table":
                return "数据来源";
            default:
                return fieldName;
        }
    }

    /**
     * 分析交通事故数据
     */
    private String analyzeAccidents(String query) {
        try {
            // 获取2024年2月的事故数据
            LocalDate startTime = LocalDate.of(2024, 2, 1);
            LocalDate endTime = LocalDate.of(2024, 2, 28);

            List<TrafficAccident> recentAccidents = trafficAccidentRepository
                .findByDateRange(startTime, endTime);

            if (recentAccidents.isEmpty()) {
                return "2024年2月暂无交通事故记录。";
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("2024年2月共发生 %d 起交通事故\n", recentAccidents.size()));

            // 统计严重程度
            long severeAccidents = recentAccidents.stream()
                .filter(TrafficAccident::isSevere)
                .count();

            if (severeAccidents > 0) {
                result.append(String.format("其中严重事故 %d 起\n", severeAccidents));
            }

            // 统计伤亡情况
            int totalInjured = recentAccidents.stream()
                .mapToInt(TrafficAccident::getPersonsInjured)
                .sum();
            int totalKilled = recentAccidents.stream()
                .mapToInt(TrafficAccident::getPersonsKilled)
                .sum();

            if (totalInjured > 0 || totalKilled > 0) {
                result.append(String.format("造成伤亡：受伤 %d 人，死亡 %d 人\n", totalInjured, totalKilled));
            }

            // 分析高发区域
            String topLocation = recentAccidents.stream()
                .collect(Collectors.groupingBy(TrafficAccident::getBorough, Collectors.counting()))
                .entrySet().stream()
                .max((e1, e2) -> e1.getValue().compareTo(e2.getValue()))
                .map(e -> e.getKey())
                .orElse("未知");

            result.append(String.format("事故高发区域：%s", topLocation));

            return result.toString();

        } catch (Exception e) {
            logger.error("事故数据分析失败", e);
            return "事故数据分析暂时不可用。";
        }
    }

    /**
     * 分析天气数据
     */
    private String analyzeWeather(String query) {
        try {
            // 获取2024年2月的天气数据
            LocalDate startTime = LocalDate.of(2024, 2, 1);
            LocalDate endTime = LocalDate.of(2024, 2, 28);

            List<WeatherData> recentWeather = weatherDataRepository
                .findByDateRange(startTime, endTime);

            if (recentWeather.isEmpty()) {
                return "2024年2月暂无天气数据记录。";
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("2024年2月天气数据（共 %d 条记录）\n", recentWeather.size()));

            // 统计恶劣天气
            long severeWeatherDays = recentWeather.stream()
                .filter(WeatherData::isSevereWeather)
                .count();

            if (severeWeatherDays > 0) {
                result.append(String.format("恶劣天气天数：%d 天\n", severeWeatherDays));
            }

            // 统计结冰风险
            long icingRiskDays = recentWeather.stream()
                .filter(WeatherData::hasIcingRisk)
                .count();

            if (icingRiskDays > 0) {
                result.append(String.format("道路结冰风险天数：%d 天\n", icingRiskDays));
            }

            // 平均温度
            double avgTemp = recentWeather.stream()
                .mapToDouble(WeatherData::getTemperature)
                .average()
                .orElse(0.0);

            result.append(String.format("平均温度：%.1f°F", avgTemp));

            return result.toString();

        } catch (Exception e) {
            logger.error("天气数据分析失败", e);
            return "天气数据分析暂时不可用。";
        }
    }

    /**
     * 分析地铁客流数据
     */
    private String analyzeRidership(String query) {
        try {
            // 获取2024年2月的地铁客流数据
            LocalDate startTime = LocalDate.of(2024, 2, 1);
            LocalDate endTime = LocalDate.of(2024, 2, 28);

            List<SubwayRidership> recentRidership = subwayRidershipRepository
                .findByDateRange(startTime, endTime);

            if (recentRidership.isEmpty()) {
                return "2024年2月暂无地铁客流数据。";
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("2024年2月地铁客流数据（共 %d 条记录）\n", recentRidership.size()));

            // 计算总客流量
            long totalRidership = recentRidership.stream()
                .mapToLong(SubwayRidership::getRidership)
                .sum();

            result.append(String.format("总客流量：%,d 人次\n", totalRidership));

            // 平均日客流量
            double avgDaily = (double) totalRidership / recentRidership.size();
            result.append(String.format("日均客流量：%,.0f 人次", avgDaily));

            return result.toString();

        } catch (Exception e) {
            logger.error("客流数据分析失败", e);
            return "客流数据分析暂时不可用。";
        }
    }

    /**
     * 分析许可事件数据
     */
    private String analyzeEvents(String query) {
        try {
            // 获取2024年2月的许可事件数据
            LocalDate startTime = LocalDate.of(2024, 2, 1);
            LocalDate endTime = LocalDate.of(2024, 2, 28);

            List<PermittedEvent> recentEvents = permittedEventRepository
                .findByDateRange(startTime, endTime);

            if (recentEvents.isEmpty()) {
                return "2024年2月暂无许可事件记录。";
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("2024年2月许可事件（共 %d 个）\n", recentEvents.size()));

            // 统计高影响事件
            long highImpactEvents = recentEvents.stream()
                .filter(PermittedEvent::isHighImpact)
                .count();

            if (highImpactEvents > 0) {
                result.append(String.format("高影响事件：%d 个\n", highImpactEvents));
            }

            // 统计高峰时段事件
            long rushHourEvents = recentEvents.stream()
                .filter(PermittedEvent::isDuringRushHour)
                .count();

            if (rushHourEvents > 0) {
                result.append(String.format("高峰时段事件：%d 个", rushHourEvents));
            }

            return result.toString();

        } catch (Exception e) {
            logger.error("事件数据分析失败", e);
            return "事件数据分析暂时不可用。";
        }
    }

    /**
     * 获取总体概况
     */
    private String getGeneralOverview() {
        StringBuilder overview = new StringBuilder();
        overview.append("【纽约曼哈顿交通数据概况】\n");
        overview.append("我可以为您分析以下数据：\n");
        overview.append("• 交通事故统计和趋势分析\n");
        overview.append("• 天气条件对交通的影响\n");
        overview.append("• 地铁客流量变化\n");
        overview.append("• 许可事件对交通的影响\n");
        overview.append("请告诉我您想了解哪方面的具体信息。");

        return overview.toString();
    }

    /**
     * 检查查询是否包含关键词
     */
    private boolean containsKeywords(String query, String... keywords) {
        for (String keyword : keywords) {
            if (query.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}