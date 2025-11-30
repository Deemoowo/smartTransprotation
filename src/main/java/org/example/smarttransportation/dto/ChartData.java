package org.example.smarttransportation.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 图表数据 DTO，用于前端可视化渲染
 */
public class ChartData {

    /**
     * 图表标题
     */
    private String title;

    /**
     * 图表类型：line/bar/pie 等
     */
    private String type;

    /**
     * 横轴标签
     */
    private List<String> labels = new ArrayList<>();

    /**
     * 数据序列
     */
    private List<Series> series = new ArrayList<>();

    public ChartData() {}

    public ChartData(String title, String type) {
        this.title = title;
        this.type = type;
    }

    /**
     * 构建单序列的简单图表
     */
    public static ChartData singleSeries(String title, String type, List<String> labels,
                                         List<Double> values, String seriesName) {
        ChartData chart = new ChartData(title, type);
        chart.setLabels(labels);
        chart.getSeries().add(new Series(seriesName, values));
        return chart;
    }

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public List<Series> getSeries() {
        return series;
    }

    public void setSeries(List<Series> series) {
        this.series = series;
    }

    /**
     * 数据序列
     */
    public static class Series {
        private String name;
        private List<Double> data = new ArrayList<>();

        public Series() {}

        public Series(String name, List<Double> data) {
            this.name = name;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Double> getData() {
            return data;
        }

        public void setData(List<Double> data) {
            this.data = data;
        }
    }
}
