package org.example.smarttransportation.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 天气回答结果
 */
public class WeatherAnswer {
    /**
     * 摘要描述
     */
    private String summary;

    /**
     * 是否来源于实时/接口数据
     */
    private boolean fromApi;

    /**
     * 数据时间范围描述
     */
    private String dateRange;

    /**
     * 图表数据
     */
    private List<ChartData> charts = new ArrayList<>();

    public WeatherAnswer() {}

    public WeatherAnswer(String summary, boolean fromApi, String dateRange, List<ChartData> charts) {
        this.summary = summary;
        this.fromApi = fromApi;
        this.dateRange = dateRange;
        if (charts != null) {
            this.charts = charts;
        }
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isFromApi() {
        return fromApi;
    }

    public void setFromApi(boolean fromApi) {
        this.fromApi = fromApi;
    }

    public String getDateRange() {
        return dateRange;
    }

    public void setDateRange(String dateRange) {
        this.dateRange = dateRange;
    }

    public List<ChartData> getCharts() {
        return charts;
    }

    public void setCharts(List<ChartData> charts) {
        this.charts = charts;
    }
}
