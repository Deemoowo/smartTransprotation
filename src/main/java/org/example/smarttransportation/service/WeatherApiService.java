package org.example.smarttransportation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.smarttransportation.dto.ChartData;
import org.example.smarttransportation.dto.WeatherAnswer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 曼哈顿天气 API 服务（默认拉取 2024 年 2 月数据）
 */
@Service
public class WeatherApiService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherApiService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${weather.api.enabled:true}")
    private boolean apiEnabled;

    @Value("${weather.api.base-url:https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline}")
    private String baseUrl;

    @Value("${weather.api.key:}")
    private String apiKey;

    @Value("${weather.api.location:Manhattan,NY}")
    private String location;

    @Value("${weather.api.start-date:2024-02-01}")
    private String startDate;

    @Value("${weather.api.end-date:2024-02-29}")
    private String endDate;

    /**
     * 根据用户消息判定是否需要返回曼哈顿 2024 年 2 月天气
     */
    public WeatherAnswer findWeatherAnswerForMessage(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return null;
        }
        String message = userMessage.toLowerCase(Locale.ROOT);

        boolean hasWeatherKeyword = message.contains("天气") || message.contains("weather");
        boolean hasLocation = message.contains("曼哈顿") || message.contains("manhattan");
        boolean hasFeb2024 = message.contains("2024年2") || message.contains("2024-02")
                || message.contains("2024/02") || message.contains("february 2024")
                || message.contains("feb 2024");

        // 降低触发门槛：只要提到天气就返回曼哈顿 2024 年 2 月默认数据
        if (hasWeatherKeyword) {
            return fetchManhattanFeb2024Weather();
        }
        return null;
    }

    /**
     * 获取曼哈顿 2024 年 2 月天气数据（优先 API，失败回退本地样例）
     */
    public WeatherAnswer fetchManhattanFeb2024Weather() {
        List<DailyWeather> dailyWeather = fetchFromApi();
        boolean fromApi = true;

        if (dailyWeather == null || dailyWeather.isEmpty()) {
            dailyWeather = fallbackWeather();
            fromApi = false;
        }

        return buildAnswer(dailyWeather, fromApi);
    }

    private List<DailyWeather> fetchFromApi() {
        if (!apiEnabled || !StringUtils.hasText(apiKey)) {
            logger.warn("天气接口未启用或未配置 API Key，使用本地样例数据");
            return null;
        }

        try {
            String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8);
            String url = String.format("%s/%s/%s/%s?unitGroup=metric&include=days&key=%s&contentType=json",
                    baseUrl, encodedLocation, startDate, endDate, apiKey);

            logger.info("调用天气接口: location={}, range={}~{}", location, startDate, endDate);
            String body = restTemplate.getForObject(url, String.class);
            if (!StringUtils.hasText(body)) {
                logger.warn("天气接口返回空响应");
                return null;
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode days = root.path("days");
            if (days.isMissingNode() || !days.isArray()) {
                logger.warn("天气接口响应缺少 days 字段");
                return null;
            }

            List<DailyWeather> daily = new ArrayList<>();
            for (JsonNode day : days) {
                LocalDate date = LocalDate.parse(day.path("datetime").asText(), DATE_FORMATTER);
                Double tempMax = day.hasNonNull("tempmax") ? day.get("tempmax").asDouble() : null;
                Double tempMin = day.hasNonNull("tempmin") ? day.get("tempmin").asDouble() : null;
                Double precip = day.hasNonNull("precip") ? day.get("precip").asDouble() : 0.0;
                Double windSpeed = day.hasNonNull("windspeed") ? day.get("windspeed").asDouble() : 0.0;
                Double snow = day.hasNonNull("snow") ? day.get("snow").asDouble() : 0.0;
                String conditions = day.path("conditions").asText("");

                daily.add(new DailyWeather(date, tempMax, tempMin, precip, windSpeed, snow, conditions));
            }

            daily.sort(Comparator.comparing(DailyWeather::getDate));
            logger.info("天气接口解析成功，记录数={}", daily.size());
            return daily;
        } catch (Exception e) {
            logger.warn("调用天气接口失败，将回退本地样例: {}", e.getMessage());
            return null;
        }
    }

    private WeatherAnswer buildAnswer(List<DailyWeather> daily, boolean fromApi) {
        if (daily == null || daily.isEmpty()) {
            return new WeatherAnswer("未获取到天气数据。", fromApi, formatDateRange(), new ArrayList<>());
        }

        double avgHigh = daily.stream()
                .filter(d -> d.getTempMax() != null)
                .mapToDouble(DailyWeather::getTempMax)
                .average().orElse(0.0);
        double avgLow = daily.stream()
                .filter(d -> d.getTempMin() != null)
                .mapToDouble(DailyWeather::getTempMin)
                .average().orElse(0.0);
        double totalPrecip = daily.stream()
                .mapToDouble(DailyWeather::getPrecip)
                .sum();
        long snowDays = daily.stream()
                .filter(d -> (d.getSnow() != null && d.getSnow() > 0) ||
                        d.getConditions().toLowerCase(Locale.ROOT).contains("snow"))
                .count();
        double maxWind = daily.stream()
                .mapToDouble(DailyWeather::getWindSpeed)
                .max().orElse(0.0);
        Optional<DailyWeather> wettest = daily.stream()
                .max(Comparator.comparing(DailyWeather::getPrecip));

        StringBuilder summary = new StringBuilder();
        summary.append("曼哈顿 2024 年 2 月天气概览：\n");
        summary.append(String.format("· 平均最高气温：%.1f°C，平均最低气温：%.1f°C\n", avgHigh, avgLow));
        summary.append(String.format("· 全月累计降水：%.1f mm，降雪天数：%d 天\n", totalPrecip, snowDays));
        summary.append(String.format("· 最大风速：%.1f km/h", maxWind));
        wettest.ifPresent(d -> summary.append(String.format("；最湿一天：%s，降水 %.1f mm",
                d.getDate().format(DateTimeFormatter.ofPattern("MM-dd")), d.getPrecip())));
        summary.append(fromApi ? "（数据来源：天气接口）" : "（数据来源：本地样例）");

        List<ChartData> charts = buildCharts(daily);
        return new WeatherAnswer(summary.toString(), fromApi, formatDateRange(), charts);
    }

    private List<ChartData> buildCharts(List<DailyWeather> daily) {
        List<ChartData> charts = new ArrayList<>();
        List<String> labels = daily.stream()
                .map(d -> d.getDate().format(DateTimeFormatter.ofPattern("MM-dd")))
                .collect(Collectors.toList());

        List<Double> highs = daily.stream()
                .map(d -> d.getTempMax() != null ? d.getTempMax() : 0.0)
                .collect(Collectors.toList());
        List<Double> lows = daily.stream()
                .map(d -> d.getTempMin() != null ? d.getTempMin() : 0.0)
                .collect(Collectors.toList());
        List<Double> precip = daily.stream()
                .map(DailyWeather::getPrecip)
                .collect(Collectors.toList());
        List<Double> winds = daily.stream()
                .map(DailyWeather::getWindSpeed)
                .collect(Collectors.toList());

        ChartData tempChart = new ChartData("2024 年 2 月曼哈顿每日温度 (°C)", "line");
        tempChart.setLabels(labels);
        tempChart.getSeries().add(new ChartData.Series("最高气温", highs));
        tempChart.getSeries().add(new ChartData.Series("最低气温", lows));
        charts.add(tempChart);

        charts.add(ChartData.singleSeries("每日降水量 (mm)", "bar", labels, precip, "降水"));
        charts.add(ChartData.singleSeries("每日风速 (km/h)", "line", labels, winds, "风速"));

        return charts;
    }

    private List<DailyWeather> fallbackWeather() {
        List<DailyWeather> sample = new ArrayList<>();
        sample.add(new DailyWeather(LocalDate.of(2024, 2, 1), 6.0, -1.0, 2.5, 18.0, 1.2, "Snow"));
        sample.add(new DailyWeather(LocalDate.of(2024, 2, 5), 4.5, -3.0, 5.8, 22.0, 3.4, "Snow, Wind"));
        sample.add(new DailyWeather(LocalDate.of(2024, 2, 10), 7.5, 1.0, 1.0, 15.0, 0.0, "Cloudy"));
        sample.add(new DailyWeather(LocalDate.of(2024, 2, 15), 9.0, 2.0, 0.0, 12.0, 0.0, "Clear"));
        sample.add(new DailyWeather(LocalDate.of(2024, 2, 20), 8.5, 1.5, 6.2, 28.0, 0.5, "Rain/Snow"));
        sample.add(new DailyWeather(LocalDate.of(2024, 2, 25), 5.0, -2.0, 3.0, 20.0, 2.0, "Snow"));
        sample.add(new DailyWeather(LocalDate.of(2024, 2, 29), 10.0, 3.5, 0.0, 10.0, 0.0, "Clear"));
        return sample;
    }

    private String formatDateRange() {
        return startDate + " 至 " + endDate;
    }

    /**
     * 简单的每日天气数据结构
     */
    private static class DailyWeather {
        private final LocalDate date;
        private final Double tempMax;
        private final Double tempMin;
        private final Double precip;
        private final Double windSpeed;
        private final Double snow;
        private final String conditions;

        DailyWeather(LocalDate date, Double tempMax, Double tempMin, Double precip,
                     Double windSpeed, Double snow, String conditions) {
            this.date = date;
            this.tempMax = tempMax;
            this.tempMin = tempMin;
            this.precip = precip != null ? precip : 0.0;
            this.windSpeed = windSpeed != null ? windSpeed : 0.0;
            this.snow = snow != null ? snow : 0.0;
            this.conditions = conditions != null ? conditions : "";
        }

        public LocalDate getDate() {
            return date;
        }

        public Double getTempMax() {
            return tempMax;
        }

        public Double getTempMin() {
            return tempMin;
        }

        public Double getPrecip() {
            return precip;
        }

        public Double getWindSpeed() {
            return windSpeed;
        }

        public Double getSnow() {
            return snow;
        }

        public String getConditions() {
            return conditions;
        }
    }
}
