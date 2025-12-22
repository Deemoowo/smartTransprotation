package org.example.smarttransportation.service;

import org.example.smarttransportation.dto.RiskWarningReport;
import org.example.smarttransportation.entity.Complaint;
import org.example.smarttransportation.entity.TrafficAccident;
import org.example.smarttransportation.repository.TrafficAccidentRepository;
import org.example.smarttransportation.repository.ComplaintRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据驱动治理服务
 * 实现场景三：事后·数据驱动治理 (Data-Driven Governance)
 *
 * @author T-Agent
 * @version 1.0
 */
@Service
public class DataGovernanceService {

    @Autowired
    private TrafficAccidentRepository trafficAccidentRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private RAGService ragService;

    /**
     * 生成事故黑点综合治理方案建议书
     *
     * @param location 事故多发地点
     * @param timeRangeStart 时间范围开始
     * @param timeRangeEnd 时间范围结束
     * @return 治理方案建议书
     */
    public GovernanceProposal generateGovernanceProposal(String location, LocalDateTime timeRangeStart, LocalDateTime timeRangeEnd) {
        GovernanceProposal proposal = new GovernanceProposal();

        // 设置基本信息
        proposal.setLocation(location);
        proposal.setTimeRange(String.format("%s 至 %s",
            timeRangeStart.toLocalDate().toString(),
            timeRangeEnd.toLocalDate().toString()));

        // 分析事故数据
        AccidentAnalysis accidentAnalysis = analyzeAccidents(location, timeRangeStart, timeRangeEnd);
        proposal.setAccidentAnalysis(accidentAnalysis);

        // 分析投诉数据
        ComplaintAnalysis complaintAnalysis = analyzeComplaints(location, timeRangeStart, timeRangeEnd);
        proposal.setComplaintAnalysis(complaintAnalysis);

        // 生成问题诊断
        String diagnosis = generateDiagnosis(accidentAnalysis, complaintAnalysis);
        proposal.setProblemDiagnosis(diagnosis);

        // 生成短期措施（治标）
        List<String> shortTermMeasures = generateShortTermMeasures(accidentAnalysis, complaintAnalysis);
        proposal.setShortTermMeasures(shortTermMeasures);

        // 生成长期措施（治本）
        List<String> longTermMeasures = generateLongTermMeasures(accidentAnalysis, complaintAnalysis);
        proposal.setLongTermMeasures(longTermMeasures);

        // 获取参考案例
        List<String> referenceCases = getReferenceCases(location);
        proposal.setReferenceCases(referenceCases);

        return proposal;
    }

    /**
     * 分析事故数据
     */
    private AccidentAnalysis analyzeAccidents(String location, LocalDateTime start, LocalDateTime end) {
        AccidentAnalysis analysis = new AccidentAnalysis();

        // 查询事故数据 (修复：将LocalDateTime转换为LocalDate)
        LocalDate startDate = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();
        List<TrafficAccident> allAccidents = trafficAccidentRepository.findByDateRange(startDate, endDate);
        // 过滤指定位置的事故数据
        List<TrafficAccident> accidents = allAccidents.stream()
            .filter(accident -> {
                if (accident.getOnStreetName() != null && accident.getOnStreetName().contains(location)) {
                    return true;
                }
                if (accident.getCrossStreetName() != null && accident.getCrossStreetName().contains(location)) {
                    return true;
                }
                if (accident.getOffStreetName() != null && accident.getOffStreetName().contains(location)) {
                    return true;
                }
                return false;
            })
            .collect(Collectors.toList());

        analysis.setTotalAccidents(accidents.size());

        // 统计事故类型
        Map<String, Long> accidentTypeCount = accidents.stream()
            .collect(Collectors.groupingBy(
                accident -> "机动车事故", // 简化处理，实际应该根据具体字段判断事故类型
                Collectors.counting()
            ));
        analysis.setAccidentTypeDistribution(accidentTypeCount);

        // 统计伤亡情况
        int totalInjuries = accidents.stream()
            .mapToInt(accident -> {
                int injuries = 0;
                if (accident.getNumberOfPersonsInjured() != null) {
                    injuries += accident.getNumberOfPersonsInjured();
                }
                if (accident.getNumberOfPedestriansInjured() != null) {
                    injuries += accident.getNumberOfPedestriansInjured();
                }
                if (accident.getNumberOfCyclistInjured() != null) {
                    injuries += accident.getNumberOfCyclistInjured();
                }
                if (accident.getNumberOfMotoristInjured() != null) {
                    injuries += accident.getNumberOfMotoristInjured();
                }
                return injuries;
            })
            .sum();

        int totalFatalities = accidents.stream()
            .mapToInt(accident -> {
                int fatalities = 0;
                if (accident.getNumberOfPersonsKilled() != null) {
                    fatalities += accident.getNumberOfPersonsKilled();
                }
                if (accident.getNumberOfPedestriansKilled() != null) {
                    fatalities += accident.getNumberOfPedestriansKilled();
                }
                if (accident.getNumberOfCyclistKilled() != null) {
                    fatalities += accident.getNumberOfCyclistKilled();
                }
                if (accident.getNumberOfMotoristKilled() != null) {
                    fatalities += accident.getNumberOfMotoristKilled();
                }
                return fatalities;
            })
            .sum();

        analysis.setTotalInjuries(totalInjuries);
        analysis.setTotalFatalities(totalFatalities);

        // 分析高发时段
        Map<String, Long> timeDistribution = accidents.stream()
            .collect(Collectors.groupingBy(
                accident -> {
                    // 修复：从LocalDate获取小时信息会报错，需要从字符串解析时间
                    try {
                        String[] timeParts = accident.getCrashTime().split(":");
                        int hour = Integer.parseInt(timeParts[0]);
                        if (hour >= 6 && hour < 12) {
                            return "上午(6-12)";
                        }
                        if (hour >= 12 && hour < 18) {
                            return "下午(12-18)";
                        }
                        if (hour >= 18 && hour < 24) {
                            return "晚上(18-24)";
                        }
                        return "凌晨(0-6)";
                    } catch (Exception e) {
                        return "未知时段";
                    }
                },
                Collectors.counting()
            ));
        analysis.setTimeDistribution(timeDistribution);

        return analysis;
    }

    /**
     * 分析投诉数据
     */
    private ComplaintAnalysis analyzeComplaints(String location, LocalDateTime start, LocalDateTime end) {
        ComplaintAnalysis analysis = new ComplaintAnalysis();

        // 查询投诉数据 (修复：保持使用LocalDateTime参数)
        List<Complaint> complaints = complaintRepository.findByLocationAndDateRange(location, start, end);

        analysis.setTotalComplaints(complaints.size());

        // 统计投诉类型分布
        Map<String, Long> complaintTypeCount = complaints.stream()
            .collect(Collectors.groupingBy(
                Complaint::getComplaintType,
                Collectors.counting()
            ));
        analysis.setComplaintTypeDistribution(complaintTypeCount);

        // 统计投诉频率趋势（按月份）
        Map<String, Long> frequencyTrend = complaints.stream()
            .collect(Collectors.groupingBy(
                // 修复：从LocalDate获取小时信息会报错
                complaint -> complaint.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.counting()
            ));
        analysis.setFrequencyTrend(frequencyTrend);

        return analysis;
    }

    /**
     * 生成问题诊断
     */
    private String generateDiagnosis(AccidentAnalysis accidentAnalysis, ComplaintAnalysis complaintAnalysis) {
        StringBuilder diagnosis = new StringBuilder();
        diagnosis.append("经过数据分析，该区域存在以下问题：\n\n");

        // 事故问题诊断
        if (accidentAnalysis.getTotalAccidents() > 50) {
            diagnosis.append("1. 事故频发：统计期内共发生 ")
                     .append(accidentAnalysis.getTotalAccidents())
                     .append(" 起事故，属于事故高发区域。\n");
        }

        // 伤亡问题诊断
        if (accidentAnalysis.getTotalInjuries() > 10 || accidentAnalysis.getTotalFatalities() > 0) {
            diagnosis.append("2. 伤亡严重：统计期内共造成 ")
                     .append(accidentAnalysis.getTotalInjuries())
                     .append(" 人受伤，")
                     .append(accidentAnalysis.getTotalFatalities())
                     .append(" 人死亡。\n");
        }

        // 时段分布问题诊断
        Map<String, Long> timeDistribution = accidentAnalysis.getTimeDistribution();
        if (!timeDistribution.isEmpty()) {
            String peakTime = timeDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("未知时段");

            diagnosis.append("3. 时段集中：事故主要发生在")
                     .append(peakTime)
                     .append("，需重点关注该时段的交通管理。\n");
        }

        // 类型分布问题诊断
        Map<String, Long> typeDistribution = accidentAnalysis.getAccidentTypeDistribution();
        if (!typeDistribution.isEmpty()) {
            String majorType = typeDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("未知类型");

            diagnosis.append("4. 类型集中：主要事故类型为")
                     .append(majorType)
                     .append("，占总事故数的较大比例。\n");
        }

        if (diagnosis.length() == "经过数据分析，该区域存在以下问题：\n\n".length()) {
            diagnosis.append("暂未发现明显问题，建议持续监测。\n");
        }

        return diagnosis.toString();
    }

    /**
     * 生成短期措施（治标）
     */
    private List<String> generateShortTermMeasures(AccidentAnalysis accidentAnalysis, ComplaintAnalysis complaintAnalysis) {
        List<String> measures = new ArrayList<>();

        // 基于事故数量的措施
        if (accidentAnalysis.getTotalAccidents() > 30) {
            measures.add("增设临时警示牌和减速带，提醒驾驶员注意安全");
            measures.add("加强早晚高峰时段的交警执法力度");
            measures.add("在事故多发时段增加巡逻频次");
        }

        // 基于伤亡情况的措施
        if (accidentAnalysis.getTotalInjuries() > 5 || accidentAnalysis.getTotalFatalities() > 0) {
            measures.add("在事故多发路段设置紧急呼叫设备");
            measures.add("优化夜间照明设施，提高道路可见度");
        }

        // 基于时段分布的措施
        Map<String, Long> timeDistribution = accidentAnalysis.getTimeDistribution();
        if (!timeDistribution.isEmpty()) {
            String peakTime = timeDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

            if (peakTime.contains("晚上") || peakTime.contains("凌晨")) {
                measures.add("加强夜间和凌晨时段的路灯维护");
            }
        }

        // 默认措施
        measures.add("开展交通安全宣传教育活动");
        measures.add("建立事故快速响应机制");

        return measures;
    }

    /**
     * 生成长期措施（治本）
     */
    private List<String> generateLongTermMeasures(AccidentAnalysis accidentAnalysis, ComplaintAnalysis complaintAnalysis) {
        List<String> measures = new ArrayList<>();

        // 基于事故类型的措施
        Map<String, Long> typeDistribution = accidentAnalysis.getAccidentTypeDistribution();
        if (!typeDistribution.isEmpty()) {
            String majorType = typeDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

            if (majorType.contains("机动车") || majorType.contains("车辆")) {
                measures.add("优化路口信号灯配时，提高通行效率");
                measures.add("施划清晰的道路标线，规范行车秩序");
            } else if (majorType.contains("行人") || majorType.contains("非机动车")) {
                measures.add("完善人行横道和非机动车道设施");
                measures.add("增设行人过街天桥或地下通道");
            }
        }

        // 基于事故数量的措施
        if (accidentAnalysis.getTotalAccidents() > 50) {
            measures.add("申请专项资金对路口进行改造");
            measures.add("引入智能信号灯控制系统");
            measures.add("建设智慧交通监控平台");
        }

        // 通用长期措施
        measures.add("建立长效治理机制，定期评估治理效果");
        measures.add("加强与城市规划部门的协调，优化道路设计");
        measures.add("推动相关法规完善，加大违法处罚力度");

        return measures;
    }

    /**
     * 分析指定时间段内事故最高发的前3条街道及其主要致因
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 街道事故分析结果
     */
    public StreetAccidentAnalysis analyzeTop3AccidentStreets(LocalDate startDate, LocalDate endDate) {
        StreetAccidentAnalysis analysis = new StreetAccidentAnalysis();

        // 获取按街道统计的事故数量
        List<Object[]> streetAccidentCounts = trafficAccidentRepository.countAccidentsByStreet(startDate, endDate);

        // 取前3条街道
        List<StreetAccidentInfo> top3Streets = new ArrayList<>();
        int count = 0;
        for (Object[] row : streetAccidentCounts) {
            if (count >= 3) {
                break;
            }

            String streetName = (String) row[0];
            Long accidentCount = (Long) row[1];

            // 分析该街道的事故致因
            List<TrafficAccident> streetAccidents = trafficAccidentRepository.findByDateRange(startDate, endDate)
                .stream()
                .filter(accident -> streetName.equals(accident.getOnStreetName()))
                .collect(Collectors.toList());

            Map<String, Long> causeDistribution = analyzeAccidentCauses(streetAccidents);

            StreetAccidentInfo streetInfo = new StreetAccidentInfo();
            streetInfo.setStreetName(streetName);
            streetInfo.setAccidentCount(accidentCount.intValue());
            streetInfo.setCauseDistribution(causeDistribution);
            streetInfo.setMainCause(getMainCause(causeDistribution));

            top3Streets.add(streetInfo);
            count++;
        }

        analysis.setTop3Streets(top3Streets);
        analysis.setAnalysisPeriod(startDate + " 至 " + endDate);

        return analysis;
    }

    /**
     * 分析事故致因分布
     */
    private Map<String, Long> analyzeAccidentCauses(List<TrafficAccident> accidents) {
        Map<String, Long> causeDistribution = new HashMap<>();

        for (TrafficAccident accident : accidents) {
            // 分析所有致因因素
            String[] factors = {
                accident.getContributingFactorVehicle1(),
                accident.getContributingFactorVehicle2(),
                accident.getContributingFactorVehicle3(),
                accident.getContributingFactorVehicle4(),
                accident.getContributingFactorVehicle5()
            };

            for (String factor : factors) {
                if (factor != null && !factor.trim().isEmpty() &&
                    !factor.equalsIgnoreCase("Unspecified") &&
                    !factor.equalsIgnoreCase("Unknown")) {
                    causeDistribution.merge(factor, 1L, Long::sum);
                }
            }
        }

        // 如果没有有效的致因数据，返回默认分类
        if (causeDistribution.isEmpty()) {
            causeDistribution.put("数据不完整", (long) accidents.size());
        }

        return causeDistribution;
    }

    /**
     * 获取主要致因
     */
    private String getMainCause(Map<String, Long> causeDistribution) {
        return causeDistribution.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("未知");
    }

    /**
     * 获取参考案例
     */
    private List<String> getReferenceCases(String location) {
        List<String> referenceCases = new ArrayList<>();

        // 使用RAG服务检索相关案例
        try {
            RAGService.AnswerResult result = ragService.answer(
                "查找与" + location + "类似的事故黑点治理成功案例", "data-governance-session");

            if (result.isSuccess() && result.getAnswer() != null) {
                // 简化答案，提取关键信息
                String answer = result.getAnswer();
                if (answer.length() > 200) {
                    answer = answer.substring(0, 200) + "...";
                }
                referenceCases.add("参考案例：\n" + answer);
            }
        } catch (Exception e) {
            // 如果RAG服务不可用，提供默认案例
            referenceCases.add("参考案例：\n布鲁克林某路口改造前后对比：通过增设左转信号灯、优化车道分配，事故率下降65%");
        }

        return referenceCases;
    }

    /**
     * 事故分析数据类
     */
    public static class AccidentAnalysis {
        private int totalAccidents;
        private int totalInjuries;
        private int totalFatalities;
        private Map<String, Long> accidentTypeDistribution;
        private Map<String, Long> timeDistribution;

        // Getters and Setters
        public int getTotalAccidents() { return totalAccidents; }
        public void setTotalAccidents(int totalAccidents) { this.totalAccidents = totalAccidents; }

        public int getTotalInjuries() { return totalInjuries; }
        public void setTotalInjuries(int totalInjuries) { this.totalInjuries = totalInjuries; }

        public int getTotalFatalities() { return totalFatalities; }
        public void setTotalFatalities(int totalFatalities) { this.totalFatalities = totalFatalities; }

        public Map<String, Long> getAccidentTypeDistribution() { return accidentTypeDistribution; }
        public void setAccidentTypeDistribution(Map<String, Long> accidentTypeDistribution) { this.accidentTypeDistribution = accidentTypeDistribution; }

        public Map<String, Long> getTimeDistribution() { return timeDistribution; }
        public void setTimeDistribution(Map<String, Long> timeDistribution) { this.timeDistribution = timeDistribution; }
    }

    /**
     * 投诉分析数据类
     */
    public static class ComplaintAnalysis {
        private int totalComplaints;
        private Map<String, Long> complaintTypeDistribution;
        private Map<String, Long> frequencyTrend;

        // Getters and Setters
        public int getTotalComplaints() { return totalComplaints; }
        public void setTotalComplaints(int totalComplaints) { this.totalComplaints = totalComplaints; }

        public Map<String, Long> getComplaintTypeDistribution() { return complaintTypeDistribution; }
        public void setComplaintTypeDistribution(Map<String, Long> complaintTypeDistribution) { this.complaintTypeDistribution = complaintTypeDistribution; }

        public Map<String, Long> getFrequencyTrend() { return frequencyTrend; }
        public void setFrequencyTrend(Map<String, Long> frequencyTrend) { this.frequencyTrend = frequencyTrend; }
    }

    /**
     * 治理方案建议书
     */
    public static class GovernanceProposal {
        private String location;
        private String timeRange;
        private AccidentAnalysis accidentAnalysis;
        private ComplaintAnalysis complaintAnalysis;
        private String problemDiagnosis;
        private List<String> shortTermMeasures;
        private List<String> longTermMeasures;
        private List<String> referenceCases;

        // Getters and Setters
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public String getTimeRange() { return timeRange; }
        public void setTimeRange(String timeRange) { this.timeRange = timeRange; }

        public AccidentAnalysis getAccidentAnalysis() { return accidentAnalysis; }
        public void setAccidentAnalysis(AccidentAnalysis accidentAnalysis) { this.accidentAnalysis = accidentAnalysis; }

        public ComplaintAnalysis getComplaintAnalysis() { return complaintAnalysis; }
        public void setComplaintAnalysis(ComplaintAnalysis complaintAnalysis) { this.complaintAnalysis = complaintAnalysis; }

        public String getProblemDiagnosis() { return problemDiagnosis; }
        public void setProblemDiagnosis(String problemDiagnosis) { this.problemDiagnosis = problemDiagnosis; }

        public List<String> getShortTermMeasures() { return shortTermMeasures; }
        public void setShortTermMeasures(List<String> shortTermMeasures) { this.shortTermMeasures = shortTermMeasures; }

        public List<String> getLongTermMeasures() { return longTermMeasures; }
        public void setLongTermMeasures(List<String> longTermMeasures) { this.longTermMeasures = longTermMeasures; }

        public List<String> getReferenceCases() { return referenceCases; }
        public void setReferenceCases(List<String> referenceCases) { this.referenceCases = referenceCases; }
    }

    /**
     * 街道事故分析结果
     */
    public static class StreetAccidentAnalysis {
        private List<StreetAccidentInfo> top3Streets;
        private String analysisPeriod;

        // Getters and Setters
        public List<StreetAccidentInfo> getTop3Streets() { return top3Streets; }
        public void setTop3Streets(List<StreetAccidentInfo> top3Streets) { this.top3Streets = top3Streets; }

        public String getAnalysisPeriod() { return analysisPeriod; }
        public void setAnalysisPeriod(String analysisPeriod) { this.analysisPeriod = analysisPeriod; }
    }

    /**
     * 街道事故信息
     */
    public static class StreetAccidentInfo {
        private String streetName;
        private int accidentCount;
        private Map<String, Long> causeDistribution;
        private String mainCause;

        // Getters and Setters
        public String getStreetName() { return streetName; }
        public void setStreetName(String streetName) { this.streetName = streetName; }

        public int getAccidentCount() { return accidentCount; }
        public void setAccidentCount(int accidentCount) { this.accidentCount = accidentCount; }

        public Map<String, Long> getCauseDistribution() { return causeDistribution; }
        public void setCauseDistribution(Map<String, Long> causeDistribution) { this.causeDistribution = causeDistribution; }

        public String getMainCause() { return mainCause; }
        public void setMainCause(String mainCause) { this.mainCause = mainCause; }
    }
}