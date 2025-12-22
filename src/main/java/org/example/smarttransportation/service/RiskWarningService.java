package org.example.smarttransportation.service;

import org.example.smarttransportation.dto.RiskWarningReport;
import org.example.smarttransportation.entity.*;
import org.example.smarttransportation.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 风险预警服务
 * 实现场景一：事前·主动风险预警 (Proactive Risk Warning)
 *
 * @author pojin
 * @date 2025/11/23
 */
@Service
public class RiskWarningService {

    private static final Logger logger = LoggerFactory.getLogger(RiskWarningService.class);

    @Autowired
    private WeatherDataRepository weatherDataRepository;

    @Autowired
    private TrafficAccidentRepository trafficAccidentRepository;

    @Autowired
    private PermittedEventRepository permittedEventRepository;

    @Autowired
    private SubwayRidershipRepository subwayRidershipRepository;

    @Autowired
    private TavilySearchService tavilySearchService;

    /**
     * 生成风险预警通报（支持多数据源）
     * 核心算法：识别"暴雪+晚高峰+道路结冰隐患"的二级风险
     * @param targetDateTime 目标时间
     * @param useNetworkSearch 是否使用网络搜索获取数据
     */
    public RiskWarningReport generateRiskWarning(LocalDateTime targetDateTime, boolean useNetworkSearch) {
        if (useNetworkSearch) {
            return generateRiskWarningWithNetworkData(targetDateTime);
        } else {
            return generateRiskWarningWithDatabaseData(targetDateTime);
        }
    }

    /**
     * 生成风险预警通报（兼容性方法，默认使用数据库数据）
     * 核心算法：识别"暴雪+晚高峰+道路结冰隐患"的二级风险
     */
    public RiskWarningReport generateRiskWarning(LocalDateTime targetDateTime) {
        return generateRiskWarningWithDatabaseData(targetDateTime);
    }

    /**
     * 使用网络搜索数据生成风险预警报告
     */
    private RiskWarningReport generateRiskWarningWithNetworkData(LocalDateTime targetDateTime) {
        RiskWarningReport report = new RiskWarningReport();

        // 设置基本信息
        report.setTimeWindow(formatTimeWindow(targetDateTime));
        report.setAffectedArea("纽约市曼哈顿区");

        try {
            // 通过网络搜索获取最新风险数据
            RiskWarningReport.RiskAnalysis riskAnalysis = analyzeRisksFromNetworkData(targetDateTime);
            report.setRiskAnalysis(riskAnalysis);

            // 确定整体风险等级
            String riskLevel = determineRiskLevel(riskAnalysis);
            report.setRiskLevel(riskLevel);
            report.setRiskType(determineRiskType(riskAnalysis));

            // 基于网络数据识别高风险区域
            List<RiskWarningReport.HighRiskZone> highRiskZones = identifyHighRiskZonesFromNetworkData(targetDateTime, riskAnalysis);
            report.setHighRiskZones(highRiskZones);

            // 生成建议和SOP引用
            report.setRecommendations(generateRecommendations(riskLevel, riskAnalysis));
            report.setSopReference(getSopReference(riskLevel));

        } catch (Exception e) {
            logger.error("网络搜索获取风险数据失败，回退到数据库数据", e);
            // 回退到数据库数据
            return generateRiskWarningWithDatabaseData(targetDateTime);
        }

        return report;
    }

    /**
     * 使用数据库数据生成风险预警报告
     */
    private RiskWarningReport generateRiskWarningWithDatabaseData(LocalDateTime targetDateTime) {
        RiskWarningReport report = new RiskWarningReport();

        // 设置基本信息
        report.setTimeWindow(formatTimeWindow(targetDateTime));
        report.setAffectedArea("纽约市曼哈顿区");

        // 分析各类风险
        RiskWarningReport.RiskAnalysis riskAnalysis = analyzeRisks(targetDateTime);
        report.setRiskAnalysis(riskAnalysis);

        // 确定整体风险等级
        String riskLevel = determineRiskLevel(riskAnalysis);
        report.setRiskLevel(riskLevel);
        report.setRiskType(determineRiskType(riskAnalysis));

        // 识别高风险区域
        List<RiskWarningReport.HighRiskZone> highRiskZones = identifyHighRiskZones(targetDateTime, riskAnalysis);
        report.setHighRiskZones(highRiskZones);

        // 生成建议和SOP引用
        report.setRecommendations(generateRecommendations(riskLevel, riskAnalysis));
        report.setSopReference(getSopReference(riskLevel));

        return report;
    }

    /**
     * 分析各类风险
     */
    private RiskWarningReport.RiskAnalysis analyzeRisks(LocalDateTime targetDateTime) {
        RiskWarningReport.RiskAnalysis analysis = new RiskWarningReport.RiskAnalysis();

        // 分析天气风险
        RiskWarningReport.WeatherRisk weatherRisk = analyzeWeatherRisk(targetDateTime);
        analysis.setWeatherRisk(weatherRisk);

        // 分析交通风险
        RiskWarningReport.TrafficRisk trafficRisk = analyzeTrafficRisk(targetDateTime);
        analysis.setTrafficRisk(trafficRisk);

        // 分析事件风险
        RiskWarningReport.EventRisk eventRisk = analyzeEventRisk(targetDateTime);
        analysis.setEventRisk(eventRisk);

        // 计算综合风险评分
        int overallScore = weatherRisk.getRiskScore() + trafficRisk.getRiskScore() + eventRisk.getRiskScore();
        analysis.setOverallRiskScore(overallScore);

        // 生成风险因子描述
        analysis.setRiskFactors(generateRiskFactors(weatherRisk, trafficRisk, eventRisk));

        return analysis;
    }

    /**
     * 分析天气风险
     */
    private RiskWarningReport.WeatherRisk analyzeWeatherRisk(LocalDateTime targetDateTime) {
        RiskWarningReport.WeatherRisk weatherRisk = new RiskWarningReport.WeatherRisk();

        // 查询目标时间的天气数据 (修复：将LocalDateTime转换为LocalDate)
        Optional<WeatherData> weatherOpt = weatherDataRepository.findByDate(targetDateTime.toLocalDate());

        if (weatherOpt.isPresent()) {
            WeatherData weather = weatherOpt.get();

            // 检查是否有降雪
            boolean hasSnow = weather.getSnow() != null && weather.getSnow().doubleValue() > 0;
            weatherRisk.setHasSnow(hasSnow);

            // 检查结冰风险
            boolean hasIcingRisk = weather.hasIcingRisk();
            weatherRisk.setHasIcingRisk(hasIcingRisk);

            // 检查恶劣天气
            boolean isSevereWeather = weather.isSevereWeather();
            weatherRisk.setSevereWeather(isSevereWeather);

            // 设置天气描述
            weatherRisk.setWeatherDescription(weather.getWeatherDescription());

            // 计算天气风险评分
            int score = 0;
            if (hasSnow) {
                score += 30;
            }
            if (hasIcingRisk) {
                score += 25;
            }
            if (isSevereWeather) {
                score += 20;
            }

            weatherRisk.setRiskScore(score);
        } else {
            weatherRisk.setWeatherDescription("天气数据不可用");
            weatherRisk.setRiskScore(0);
        }

        return weatherRisk;
    }

    /**
     * 分析交通风险
     */
    private RiskWarningReport.TrafficRisk analyzeTrafficRisk(LocalDateTime targetDateTime) {
        RiskWarningReport.TrafficRisk trafficRisk = new RiskWarningReport.TrafficRisk();

        // 判断是否为高峰时段
        int hour = targetDateTime.getHour();
        boolean isRushHour = (hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19);
        trafficRisk.setRushHour(isRushHour);

        // 查询历史事故数据（同一时段）(修复：将LocalDateTime转换为LocalDate)
        LocalDate startDate = targetDateTime.toLocalDate().minusDays(30);
        LocalDate endDate = targetDateTime.toLocalDate().plusDays(1);

        List<TrafficAccident> accidents = trafficAccidentRepository.findByDateRange(startDate, endDate);
        int accidentCount = accidents.size();
        trafficRisk.setAccidentCount(accidentCount);

        // 查询地铁高密度站点
        List<SubwayRidership> highDensityStations = subwayRidershipRepository
            .findHighDensityStations(startDate, endDate, 500);
        trafficRisk.setHighDensityStations(highDensityStations.size());

        // 设置交通模式描述
        if (isRushHour) {
            trafficRisk.setTrafficPattern("高峰时段 - 交通密度极高");
        } else {
            trafficRisk.setTrafficPattern("平峰时段 - 交通密度正常");
        }

        // 计算交通风险评分
        int score = 0;
        if (isRushHour) {
            score += 25;
        }
        if (accidentCount > 10) {
            score += 20;
        }
        if (highDensityStations.size() > 5) {
            score += 15;
        }

        trafficRisk.setRiskScore(score);

        return trafficRisk;
    }

    /**
     * 分析事件风险
     */
    private RiskWarningReport.EventRisk analyzeEventRisk(LocalDateTime targetDateTime) {
        RiskWarningReport.EventRisk eventRisk = new RiskWarningReport.EventRisk();

        // 查询活跃事件 (修复：将LocalDateTime转换为LocalDate)
        LocalDate startTime = targetDateTime.toLocalDate().minusDays(1);
        LocalDate endTime = targetDateTime.toLocalDate().plusDays(1);

        List<PermittedEvent> activeEvents = permittedEventRepository
            .findByBoroughAndDateRange("Manhattan", startTime, endTime);
        eventRisk.setActiveEvents(activeEvents.size());

        // 统计高影响事件
        long highImpactCount = activeEvents.stream()
            .filter(event -> "高影响".equals(event.getImpactLevel()))
            .count();
        eventRisk.setHighImpactEvents((int) highImpactCount);

        // 统计事件类型
        Map<String, Long> eventTypeCount = activeEvents.stream()
            .collect(Collectors.groupingBy(
                PermittedEvent::getEventType,
                Collectors.counting()
            ));

        String eventTypes = eventTypeCount.entrySet().stream()
            .map(entry -> entry.getKey() + "(" + entry.getValue() + ")")
            .collect(Collectors.joining(", "));
        eventRisk.setEventTypes(eventTypes.isEmpty() ? "无活跃事件" : eventTypes);

        // 计算事件风险评分
        int score = 0;
        if (activeEvents.size() > 3) {
            score += 15;
        }
        if (highImpactCount > 0) {
            score += 20;
        }

        eventRisk.setRiskScore(score);

        return eventRisk;
    }

    /**
     * 确定风险等级
     */
    private String determineRiskLevel(RiskWarningReport.RiskAnalysis analysis) {
        int totalScore = analysis.getOverallRiskScore();

        if (totalScore >= 70) {
            return "一级风险"; // 高风险
        } else if (totalScore >= 50) {
            return "二级风险"; // 中高风险
        } else if (totalScore >= 30) {
            return "三级风险"; // 中等风险
        } else {
            return "四级风险"; // 低风险
        }
    }

    /**
     * 确定风险类型
     */
    private String determineRiskType(RiskWarningReport.RiskAnalysis analysis) {
        List<String> riskTypes = new ArrayList<>();

        RiskWarningReport.WeatherRisk weather = analysis.getWeatherRisk();
        if (weather.isHasSnow()) {
            riskTypes.add("暴雪");
        }
        if (weather.isHasIcingRisk()) {
            riskTypes.add("道路结冰");
        }
        if (weather.isSevereWeather()) {
            riskTypes.add("恶劣天气");
        }

        if (analysis.getTrafficRisk().isRushHour()) {
            riskTypes.add("高峰拥堵");
        }

        if (analysis.getEventRisk().getHighImpactEvents() > 0) {
            riskTypes.add("重大事件");
        }

        return riskTypes.isEmpty() ? "综合风险" : String.join("+", riskTypes);
    }

    /**
     * 识别高风险区域
     */
    private List<RiskWarningReport.HighRiskZone> identifyHighRiskZones(
            LocalDateTime targetDateTime, RiskWarningReport.RiskAnalysis analysis) {

        List<RiskWarningReport.HighRiskZone> zones = new ArrayList<>();

        // 基于历史事故数据识别事故多发区域 (修复：将LocalDateTime转换为LocalDate)
        LocalDate startDate = targetDateTime.toLocalDate().minusDays(30);
        LocalDate endDate = targetDateTime.toLocalDate().plusDays(1);

        List<Object[]> accidentsByStreet = trafficAccidentRepository
            .countAccidentsByStreet(startDate, endDate);

        // 取前5个事故多发街道
        for (int i = 0; i < Math.min(5, accidentsByStreet.size()); i++) {
            Object[] result = accidentsByStreet.get(i);
            String streetName = (String) result[0];
            Long accidentCount = (Long) result[1];

            if (accidentCount > 3) { // 只考虑事故数量较多的街道
                RiskWarningReport.HighRiskZone zone = new RiskWarningReport.HighRiskZone();
                zone.setZoneName("事故多发区域");
                zone.setLocation(streetName);
                zone.setRiskLevel(accidentCount > 10 ? "极高风险" : "高风险");
                zone.setRiskFactors("历史事故频发，天气条件恶化");

                List<String> suggestions = Arrays.asList(
                    "增派交警巡逻",
                    "设置临时警示标志",
                    "加强路面除雪除冰",
                    "限制车辆通行速度"
                );
                zone.setDeploymentSuggestions(suggestions);

                zones.add(zone);
            }
        }

        // 基于地铁高密度站点识别人流密集区域
        List<SubwayRidership> highDensityStations = subwayRidershipRepository
            .findHighDensityStations(startDate, endDate, 800);

        for (SubwayRidership station : highDensityStations.subList(0, Math.min(3, highDensityStations.size()))) {
            RiskWarningReport.HighRiskZone zone = new RiskWarningReport.HighRiskZone();
            zone.setZoneName("人流密集区域");
            zone.setLocation(station.getStationComplex() + "地铁站周边");
            zone.setRiskLevel("中高风险");
            zone.setRiskFactors("人流密集，恶劣天气下疏散困难");
            zone.setLatitude(station.getLatitude() != null ? station.getLatitude().doubleValue() : null);
            zone.setLongitude(station.getLongitude() != null ? station.getLongitude().doubleValue() : null);

            List<String> suggestions = Arrays.asList(
                "增加地面引导人员",
                "开放临时避难场所",
                "加强地铁站周边除雪",
                "准备应急疏散预案"
            );
            zone.setDeploymentSuggestions(suggestions);

            zones.add(zone);
        }

        return zones;
    }

    /**
     * 生成建议措施
     */
    private List<String> generateRecommendations(String riskLevel, RiskWarningReport.RiskAnalysis analysis) {
        List<String> recommendations = new ArrayList<>();

        // 基于风险等级的通用建议
        switch (riskLevel) {
            case "一级风险":
                recommendations.add("立即启动应急预案，全面部署应急资源");
                recommendations.add("发布交通管制通告，限制非必要车辆出行");
                recommendations.add("开放所有应急避难场所");
                break;
            case "二级风险":
                recommendations.add("启动二级应急响应，重点区域部署警力");
                recommendations.add("发布交通安全提醒，建议市民谨慎出行");
                recommendations.add("加强重点路段巡逻和监控");
                break;
            case "三级风险":
                recommendations.add("加强交通监控，做好应急准备");
                recommendations.add("向市民发布出行提醒");
                break;
            default:
                recommendations.add("保持常规监控，关注天气变化");
        }

        // 基于具体风险因子的专项建议
        RiskWarningReport.WeatherRisk weather = analysis.getWeatherRisk();
        if (weather.isHasSnow()) {
            recommendations.add("启动除雪作业，优先保障主干道通行");
            recommendations.add("在坡道和桥梁设置防滑设施");
        }

        if (weather.isHasIcingRisk()) {
            recommendations.add("重点关注桥梁、高架路段结冰情况");
            recommendations.add("准备融雪剂和防滑材料");
        }

        if (analysis.getTrafficRisk().isRushHour()) {
            recommendations.add("在高峰时段增派交通疏导人员");
            recommendations.add("优化信号灯配时，提高通行效率");
        }

        if (analysis.getEventRisk().getHighImpactEvents() > 0) {
            recommendations.add("协调活动主办方，做好人流疏导");
            recommendations.add("制定活动期间应急疏散方案");
        }

        return recommendations;
    }

    /**
     * 获取SOP引用
     */
    private String getSopReference(String riskLevel) {
        switch (riskLevel) {
            case "一级风险":
                return "SOP-PW-L1: 一级风险应急处置标准作业程序";
            case "二级风险":
                return "SOP-PW-L2: 二级风险预警处置标准作业程序";
            case "三级风险":
                return "SOP-PW-L3: 三级风险监控标准作业程序";
            default:
                return "SOP-PW-L4: 常规监控标准作业程序";
        }
    }

    /**
     * 生成风险因子描述
     */
    private String generateRiskFactors(RiskWarningReport.WeatherRisk weather,
                                     RiskWarningReport.TrafficRisk traffic,
                                     RiskWarningReport.EventRisk event) {
        List<String> factors = new ArrayList<>();

        if (weather.isHasSnow()) {
            factors.add("降雪天气");
        }
        if (weather.isHasIcingRisk()) {
            factors.add("道路结冰风险");
        }
        if (weather.isSevereWeather()) {
            factors.add("恶劣天气条件");
        }

        if (traffic.isRushHour()) {
            factors.add("交通高峰时段");
        }
        if (traffic.getAccidentCount() > 10) {
            factors.add("历史事故频发");
        }
        if (traffic.getHighDensityStations() > 5) {
            factors.add("人流密集");
        }

        if (event.getActiveEvents() > 3) {
            factors.add("多个活动同时进行");
        }
        if (event.getHighImpactEvents() > 0) {
            factors.add("高影响事件");
        }

        return factors.isEmpty() ? "暂无明显风险因子" : String.join("、", factors);
    }

    /**
     * 格式化时间窗口
     */
    private String formatTimeWindow(LocalDateTime targetDateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
        LocalDateTime endTime = targetDateTime.plusHours(2);
        return targetDateTime.format(formatter) + " - " + endTime.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /**
     * 基于网络搜索数据分析风险
     */
    private RiskWarningReport.RiskAnalysis analyzeRisksFromNetworkData(LocalDateTime targetDateTime) {
        RiskWarningReport.RiskAnalysis analysis = new RiskWarningReport.RiskAnalysis();

        try {
            // 分析天气风险（通过网络搜索）
            RiskWarningReport.WeatherRisk weatherRisk = analyzeWeatherRiskFromNetwork(targetDateTime);
            analysis.setWeatherRisk(weatherRisk);

            // 分析交通风险（通过网络搜索）
            RiskWarningReport.TrafficRisk trafficRisk = analyzeTrafficRiskFromNetwork(targetDateTime);
            analysis.setTrafficRisk(trafficRisk);

            // 分析事件风险（通过网络搜索）
            RiskWarningReport.EventRisk eventRisk = analyzeEventRiskFromNetwork(targetDateTime);
            analysis.setEventRisk(eventRisk);

            // 计算综合风险评分
            int overallScore = weatherRisk.getRiskScore() + trafficRisk.getRiskScore() + eventRisk.getRiskScore();
            analysis.setOverallRiskScore(overallScore);

            // 生成风险因子描述
            analysis.setRiskFactors(generateRiskFactors(weatherRisk, trafficRisk, eventRisk));

        } catch (Exception e) {
            logger.error("网络数据分析失败", e);
            // 创建默认的风险分析结果
            analysis = createDefaultRiskAnalysis();
        }

        return analysis;
    }

    /**
     * 通过网络搜索分析天气风险
     */
    private RiskWarningReport.WeatherRisk analyzeWeatherRiskFromNetwork(LocalDateTime targetDateTime) {
        RiskWarningReport.WeatherRisk weatherRisk = new RiskWarningReport.WeatherRisk();

        try {
            // 构建天气查询
            String weatherQuery = String.format("New York Manhattan weather forecast %s snow ice storm severe weather conditions",
                targetDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            TavilySearchService.TavilyResponse weatherResponse = tavilySearchService.search(weatherQuery);

            if (weatherResponse != null && weatherResponse.results() != null && !weatherResponse.results().isEmpty()) {
                String weatherContent = weatherResponse.results().get(0).content().toLowerCase();

                // 分析天气内容
                boolean hasSnow = weatherContent.contains("snow") || weatherContent.contains("snowfall") ||
                                weatherContent.contains("blizzard") || weatherContent.contains("暴雪");
                weatherRisk.setHasSnow(hasSnow);

                boolean hasIcingRisk = weatherContent.contains("ice") || weatherContent.contains("icing") ||
                                     weatherContent.contains("freezing") || weatherContent.contains("结冰");
                weatherRisk.setHasIcingRisk(hasIcingRisk);

                boolean isSevereWeather = weatherContent.contains("severe") || weatherContent.contains("storm") ||
                                        weatherContent.contains("warning") || weatherContent.contains("恶劣");
                weatherRisk.setSevereWeather(isSevereWeather);

                // 设置天气描述
                weatherRisk.setWeatherDescription(weatherResponse.answer() != null ?
                    weatherResponse.answer() : "基于网络搜索的天气预报信息");

                // 计算风险评分
                int score = 0;
                if (hasSnow) {
                    score += 30;
                }
                if (hasIcingRisk) {
                    score += 25;
                }
                if (isSevereWeather) {
                    score += 20;
                }
                weatherRisk.setRiskScore(score);

            } else {
                // 无法获取天气数据时的默认处理
                weatherRisk.setWeatherDescription("无法获取最新天气数据");
                weatherRisk.setRiskScore(10); // 给予基础风险分
            }

        } catch (Exception e) {
            logger.error("网络天气数据获取失败", e);
            weatherRisk.setWeatherDescription("天气数据获取失败");
            weatherRisk.setRiskScore(15); // 不确定情况下给予中等风险分
        }

        return weatherRisk;
    }

    /**
     * 通过网络搜索分析交通风险
     */
    private RiskWarningReport.TrafficRisk analyzeTrafficRiskFromNetwork(LocalDateTime targetDateTime) {
        RiskWarningReport.TrafficRisk trafficRisk = new RiskWarningReport.TrafficRisk();

        try {
            // 判断是否为高峰时段
            int hour = targetDateTime.getHour();
            boolean isRushHour = (hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19);
            trafficRisk.setRushHour(isRushHour);

            // 构建交通查询
            String trafficQuery = String.format("New York Manhattan traffic accidents congestion %s rush hour subway delays",
                targetDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            TavilySearchService.TavilyResponse trafficResponse = tavilySearchService.search(trafficQuery);

            if (trafficResponse != null && trafficResponse.results() != null && !trafficResponse.results().isEmpty()) {
                String trafficContent = trafficResponse.results().get(0).content().toLowerCase();

                // 分析交通内容
                int accidentCount = countOccurrences(trafficContent, new String[]{"accident", "crash", "collision"});
                trafficRisk.setAccidentCount(accidentCount);

                int densityStations = countOccurrences(trafficContent, new String[]{"crowded", "busy", "congestion", "delay"});
                trafficRisk.setHighDensityStations(densityStations);

                // 设置交通模式描述
                if (isRushHour) {
                    trafficRisk.setTrafficPattern("高峰时段 - 基于网络数据显示交通密度较高");
                } else {
                    trafficRisk.setTrafficPattern("平峰时段 - 基于网络数据显示交通密度正常");
                }

            } else {
                trafficRisk.setAccidentCount(0);
                trafficRisk.setHighDensityStations(0);
                trafficRisk.setTrafficPattern(isRushHour ? "高峰时段 - 数据不可用" : "平峰时段 - 数据不可用");
            }

            // 计算交通风险评分
            int score = 0;
            if (isRushHour) {
                score += 25;
            }
            if (trafficRisk.getAccidentCount() > 5) {
                score += 20;
            }
            if (trafficRisk.getHighDensityStations() > 3) {
                score += 15;
            }
            trafficRisk.setRiskScore(score);

        } catch (Exception e) {
            logger.error("网络交通数据获取失败", e);
            // 基于时间段给予基础评分
            int hour = targetDateTime.getHour();
            boolean isRushHour = (hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19);
            trafficRisk.setRushHour(isRushHour);
            trafficRisk.setAccidentCount(0);
            trafficRisk.setHighDensityStations(0);
            trafficRisk.setTrafficPattern("交通数据获取失败");
            trafficRisk.setRiskScore(isRushHour ? 25 : 10);
        }

        return trafficRisk;
    }

    /**
     * 通过网络搜索分析事件风险
     */
    private RiskWarningReport.EventRisk analyzeEventRiskFromNetwork(LocalDateTime targetDateTime) {
        RiskWarningReport.EventRisk eventRisk = new RiskWarningReport.EventRisk();

        try {
            // 构建事件查询
            String eventQuery = String.format("New York Manhattan events activities %s large gatherings concerts protests",
                targetDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            TavilySearchService.TavilyResponse eventResponse = tavilySearchService.search(eventQuery);

            if (eventResponse != null && eventResponse.results() != null && !eventResponse.results().isEmpty()) {
                String eventContent = eventResponse.results().get(0).content().toLowerCase();

                // 分析事件内容
                int activeEvents = countOccurrences(eventContent, new String[]{"event", "concert", "festival", "gathering", "parade"});
                eventRisk.setActiveEvents(activeEvents);

                int highImpactEvents = countOccurrences(eventContent, new String[]{"large", "major", "massive", "thousands", "crowd"});
                eventRisk.setHighImpactEvents(highImpactEvents);

                // 统计事件类型
                List<String> eventTypes = new ArrayList<>();
                if (eventContent.contains("concert")) {
                    eventTypes.add("音乐会");
                }
                if (eventContent.contains("festival")) {
                    eventTypes.add("节庆活动");
                }
                if (eventContent.contains("protest")) {
                    eventTypes.add("抗议活动");
                }
                if (eventContent.contains("parade")) {
                    eventTypes.add("游行");
                }
                if (eventContent.contains("sports")) {
                    eventTypes.add("体育赛事");
                }

                eventRisk.setEventTypes(eventTypes.isEmpty() ? "无明确事件类型" : String.join(", ", eventTypes));

            } else {
                eventRisk.setActiveEvents(0);
                eventRisk.setHighImpactEvents(0);
                eventRisk.setEventTypes("无活跃事件");
            }

            // 计算事件风险评分
            int score = 0;
            if (eventRisk.getActiveEvents() > 2) {
                score += 15;
            }
            if (eventRisk.getHighImpactEvents() > 0) {
                score += 20;
            }
            eventRisk.setRiskScore(score);

        } catch (Exception e) {
            logger.error("网络事件数据获取失败", e);
            eventRisk.setActiveEvents(0);
            eventRisk.setHighImpactEvents(0);
            eventRisk.setEventTypes("事件数据获取失败");
            eventRisk.setRiskScore(5); // 给予最低风险分
        }

        return eventRisk;
    }

    /**
     * 基于网络数据识别高风险区域
     */
    private List<RiskWarningReport.HighRiskZone> identifyHighRiskZonesFromNetworkData(
            LocalDateTime targetDateTime, RiskWarningReport.RiskAnalysis analysis) {

        List<RiskWarningReport.HighRiskZone> zones = new ArrayList<>();

        try {
            // 构建高风险区域查询
            String riskZoneQuery = String.format("New York Manhattan high risk areas dangerous zones %s traffic accidents crime",
                targetDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            TavilySearchService.TavilyResponse riskResponse = tavilySearchService.search(riskZoneQuery);

            if (riskResponse != null && riskResponse.results() != null && !riskResponse.results().isEmpty()) {
                String riskContent = riskResponse.results().get(0).content().toLowerCase();

                // 基于网络搜索结果识别高风险区域
                List<String> commonRiskAreas = Arrays.asList(
                    "Times Square", "Herald Square", "Union Square", "Washington Square Park",
                    "Central Park", "Brooklyn Bridge", "Manhattan Bridge", "Williamsburg Bridge"
                );

                for (String area : commonRiskAreas) {
                    if (riskContent.contains(area.toLowerCase()) ||
                        riskContent.contains(area.replace(" ", "").toLowerCase())) {

                        RiskWarningReport.HighRiskZone zone = new RiskWarningReport.HighRiskZone();
                        zone.setZoneName("网络数据识别的高风险区域");
                        zone.setLocation(area + "周边区域");
                        zone.setRiskLevel("中高风险");
                        zone.setRiskFactors("基于网络搜索的风险信息");

                        List<String> suggestions = Arrays.asList(
                            "加强该区域监控",
                            "增派安全人员",
                            "设置临时警示标志",
                            "准备应急疏散预案"
                        );
                        zone.setDeploymentSuggestions(suggestions);

                        zones.add(zone);

                        if (zones.size() >= 3) {
                            break; // 限制最多3个区域
                        }
                    }
                }
            }

            // 如果没有找到具体区域，添加默认的高风险区域
            if (zones.isEmpty()) {
                RiskWarningReport.HighRiskZone defaultZone = new RiskWarningReport.HighRiskZone();
                defaultZone.setZoneName("一般风险区域");
                defaultZone.setLocation("曼哈顿主要交通枢纽");
                defaultZone.setRiskLevel("中等风险");
                defaultZone.setRiskFactors("基于历史数据和当前条件的综合评估");

                List<String> suggestions = Arrays.asList(
                    "保持常规监控",
                    "关注天气变化",
                    "准备应急资源"
                );
                defaultZone.setDeploymentSuggestions(suggestions);
                zones.add(defaultZone);
            }

        } catch (Exception e) {
            logger.error("网络风险区域数据获取失败", e);
            // 添加默认风险区域
            RiskWarningReport.HighRiskZone defaultZone = new RiskWarningReport.HighRiskZone();
            defaultZone.setZoneName("默认监控区域");
            defaultZone.setLocation("曼哈顿核心区域");
            defaultZone.setRiskLevel("待评估");
            defaultZone.setRiskFactors("数据获取失败，建议人工评估");
            defaultZone.setDeploymentSuggestions(Arrays.asList("加强人工巡查", "收集现场信息"));
            zones.add(defaultZone);
        }

        return zones;
    }

    /**
     * 创建默认的风险分析结果
     */
    private RiskWarningReport.RiskAnalysis createDefaultRiskAnalysis() {
        RiskWarningReport.RiskAnalysis analysis = new RiskWarningReport.RiskAnalysis();

        // 创建默认天气风险
        RiskWarningReport.WeatherRisk weatherRisk = new RiskWarningReport.WeatherRisk();
        weatherRisk.setWeatherDescription("数据不可用");
        weatherRisk.setRiskScore(15);
        analysis.setWeatherRisk(weatherRisk);

        // 创建默认交通风险
        RiskWarningReport.TrafficRisk trafficRisk = new RiskWarningReport.TrafficRisk();
        trafficRisk.setTrafficPattern("数据不可用");
        trafficRisk.setRiskScore(15);
        analysis.setTrafficRisk(trafficRisk);

        // 创建默认事件风险
        RiskWarningReport.EventRisk eventRisk = new RiskWarningReport.EventRisk();
        eventRisk.setEventTypes("数据不可用");
        eventRisk.setRiskScore(10);
        analysis.setEventRisk(eventRisk);

        analysis.setOverallRiskScore(40);
        analysis.setRiskFactors("数据获取失败，建议人工核实");

        return analysis;
    }

    /**
     * 生成应急处置方案
     *
     * @param location 事故地点
     * @param accidentType 事故类型
     * @param severity 严重程度
     * @return 应急处置方案
     */
    public EmergencyResponse generateEmergencyResponse(String location, String accidentType, String severity) {
        EmergencyResponse response = new EmergencyResponse();
        response.setLocation(location);
        response.setAccidentType(accidentType);
        response.setSeverity(severity);
        response.setResponseTime(LocalDateTime.now());

        // 根据事故类型确定SOP引用
        String sopReference = getSopReferenceForAccident(accidentType, severity);
        response.setSopReference(sopReference);

        // 生成立即行动步骤
        List<String> immediateActions = generateImmediateActions(accidentType, severity);
        response.setImmediateActions(immediateActions);

        // 生成资源部署建议
        List<String> resourceDeployment = generateResourceDeployment(location, accidentType, severity);
        response.setResourceDeployment(resourceDeployment);

        // 生成交通管制措施
        List<String> trafficControl = generateTrafficControlMeasures(location, accidentType);
        response.setTrafficControlMeasures(trafficControl);

        // 生成后续处置步骤
        List<String> followUpActions = generateFollowUpActions(accidentType, severity);
        response.setFollowUpActions(followUpActions);

        return response;
    }

    /**
     * 根据事故类型获取SOP引用
     */
    private String getSopReferenceForAccident(String accidentType, String severity) {
        if (accidentType.contains("追尾") || accidentType.contains("collision")) {
            if ("严重".equals(severity) || "重大".equals(severity)) {
                return "SOP-ER-COLLISION-SEVERE: 严重追尾事故应急处置标准作业程序";
            } else {
                return "SOP-ER-COLLISION-NORMAL: 一般追尾事故应急处置标准作业程序";
            }
        } else if (accidentType.contains("翻车") || accidentType.contains("rollover")) {
            return "SOP-ER-ROLLOVER: 车辆翻覆事故应急处置标准作业程序";
        } else if (accidentType.contains("火灾") || accidentType.contains("fire")) {
            return "SOP-ER-FIRE: 车辆火灾事故应急处置标准作业程序";
        } else {
            return "SOP-ER-GENERAL: 一般交通事故应急处置标准作业程序";
        }
    }

    /**
     * 生成立即行动步骤
     */
    private List<String> generateImmediateActions(String accidentType, String severity) {
        List<String> actions = new ArrayList<>();

        // 通用立即行动
        actions.add("1. 立即派遣最近的交警和急救车辆到达现场");
        actions.add("2. 确保现场安全，设置警戒区域");
        actions.add("3. 评估伤亡情况，优先救治伤员");

        // 根据事故类型添加特定行动
        if (accidentType.contains("追尾") || accidentType.contains("collision")) {
            actions.add("4. 检查车辆是否有燃油泄漏或火灾风险");
            actions.add("5. 疏散围观人员，保持救援通道畅通");
            if ("多车".equals(severity) || accidentType.contains("多车")) {
                actions.add("6. 启动大型事故应急预案，请求额外救援力量");
                actions.add("7. 通知医院准备接收多名伤员");
            }
        }

        actions.add("8. 通知相关部门（消防、医疗、拖车服务）");
        actions.add("9. 开始现场勘查和证据收集");

        return actions;
    }

    /**
     * 生成资源部署建议
     */
    private List<String> generateResourceDeployment(String location, String accidentType, String severity) {
        List<String> deployment = new ArrayList<>();

        // 基础资源部署
        deployment.add("交警：2-3名交警到场处理");
        deployment.add("急救车：1-2辆救护车待命");
        deployment.add("拖车：联系拖车服务清理现场");

        // 根据严重程度调整资源
        if ("严重".equals(severity) || "重大".equals(severity) || accidentType.contains("多车")) {
            deployment.add("消防车：1辆消防车到场待命");
            deployment.add("交警：增派至4-6名交警");
            deployment.add("急救车：增派至3-4辆救护车");
            deployment.add("指挥车：现场指挥车辆到场");
        }

        // 根据地点调整资源
        if (location.contains("大道") || location.contains("Avenue")) {
            deployment.add("交通疏导：在主要路口设置交通疏导点");
            deployment.add("信息发布：启动交通信息发布系统");
        }

        if (location.contains("桥") || location.contains("Bridge")) {
            deployment.add("特殊设备：准备桥梁救援设备");
            deployment.add("专业人员：桥梁安全评估专家");
        }

        return deployment;
    }

    /**
     * 生成交通管制措施
     */
    private List<String> generateTrafficControlMeasures(String location, String accidentType) {
        List<String> measures = new ArrayList<>();

        measures.add("立即封闭事故车道，设置安全锥和警示标志");
        measures.add("在事故点前方200-500米设置分流指示");

        if (location.contains("大道") || location.contains("Avenue")) {
            measures.add("启动主干道应急交通预案");
            measures.add("协调周边路口信号灯，优化分流路线");
            measures.add("通过交通广播和导航系统发布绕行信息");
        }

        if (accidentType.contains("多车") || accidentType.contains("严重")) {
            measures.add("考虑临时封闭整个路段");
            measures.add("启动区域交通疏导预案");
            measures.add("协调公共交通增加班次");
        }

        measures.add("设置临时停车区域供相关车辆使用");
        measures.add("安排交警在关键路口进行人工疏导");

        return measures;
    }

    /**
     * 生成后续处置步骤
     */
    private List<String> generateFollowUpActions(String accidentType, String severity) {
        List<String> actions = new ArrayList<>();

        actions.add("完成现场勘查和事故调查");
        actions.add("清理现场，恢复正常交通");
        actions.add("统计伤亡和财产损失情况");
        actions.add("向上级部门报告事故处置情况");

        if ("严重".equals(severity) || "重大".equals(severity)) {
            actions.add("启动事故深度调查程序");
            actions.add("评估是否需要采取预防措施");
            actions.add("组织事故分析会议");
        }

        actions.add("更新事故数据库记录");
        actions.add("总结经验教训，完善应急预案");

        return actions;
    }

    /**
     * 统计关键词出现次数的辅助方法
     */
    private int countOccurrences(String content, String[] keywords) {
        int count = 0;
        for (String keyword : keywords) {
            int index = 0;
            while ((index = content.indexOf(keyword, index)) != -1) {
                count++;
                index += keyword.length();
            }
        }
        return Math.min(count, 10); // 限制最大计数
    }

    /**
     * 应急响应方案
     */
    public static class EmergencyResponse {
        private String location;
        private String accidentType;
        private String severity;
        private LocalDateTime responseTime;
        private String sopReference;
        private List<String> immediateActions;
        private List<String> resourceDeployment;
        private List<String> trafficControlMeasures;
        private List<String> followUpActions;

        // Getters and Setters
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public String getAccidentType() { return accidentType; }
        public void setAccidentType(String accidentType) { this.accidentType = accidentType; }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public LocalDateTime getResponseTime() { return responseTime; }
        public void setResponseTime(LocalDateTime responseTime) { this.responseTime = responseTime; }

        public String getSopReference() { return sopReference; }
        public void setSopReference(String sopReference) { this.sopReference = sopReference; }

        public List<String> getImmediateActions() { return immediateActions; }
        public void setImmediateActions(List<String> immediateActions) { this.immediateActions = immediateActions; }

        public List<String> getResourceDeployment() { return resourceDeployment; }
        public void setResourceDeployment(List<String> resourceDeployment) { this.resourceDeployment = resourceDeployment; }

        public List<String> getTrafficControlMeasures() { return trafficControlMeasures; }
        public void setTrafficControlMeasures(List<String> trafficControlMeasures) { this.trafficControlMeasures = trafficControlMeasures; }

        public List<String> getFollowUpActions() { return followUpActions; }
        public void setFollowUpActions(List<String> followUpActions) { this.followUpActions = followUpActions; }
    }

}