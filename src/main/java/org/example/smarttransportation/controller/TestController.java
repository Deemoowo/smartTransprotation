package org.example.smarttransportation.controller;

import org.example.smarttransportation.dto.RiskWarningReport;
import org.example.smarttransportation.service.DataGovernanceService;
import org.example.smarttransportation.service.RiskWarningService;
import org.example.smarttransportation.service.TrafficDataAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 测试控制器 - 直接测试业务逻辑
 * 
 * @author pojin
 * @date 2025/12/22
 */
@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*")
public class TestController {

    private static final Logger logger = LoggerFactory.getLogger(TestController.class);

    @Autowired
    private DataGovernanceService dataGovernanceService;

    @Autowired
    private RiskWarningService riskWarningService;

    @Autowired
    private TrafficDataAnalysisService trafficDataAnalysisService;

    /**
     * 测试1：复盘2024年2月的事故数据，找出事故最高发的前3条街道，并分析主要致因
     */
    @GetMapping("/accident-analysis")
    public ResponseEntity<?> testAccidentAnalysis() {
        try {
            logger.info("开始测试事故数据复盘功能");
            
            LocalDate startDate = LocalDate.of(2024, 2, 1);
            LocalDate endDate = LocalDate.of(2024, 2, 29);
            
            DataGovernanceService.StreetAccidentAnalysis analysis = dataGovernanceService.analyzeTop3AccidentStreets(startDate, endDate);
            
            logger.info("事故数据复盘测试完成，分析了{}条街道", 
                analysis.getTop3Streets() != null ? analysis.getTop3Streets().size() : 0);
            
            return ResponseEntity.ok(analysis);
            
        } catch (Exception e) {
            logger.error("事故数据复盘测试失败", e);
            return ResponseEntity.internalServerError()
                .body("测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试2：第五大道发生多车追尾，请根据标准操作程序（SOP）生成应急处置方案
     */
    @GetMapping("/emergency-response")
    public ResponseEntity<?> testEmergencyResponse() {
        try {
            logger.info("开始测试应急处置方案生成功能");
            
            RiskWarningService.EmergencyResponse response = riskWarningService.generateEmergencyResponse(
                "第五大道", "多车追尾", "严重"
            );
            
            logger.info("应急处置方案生成测试完成，生成了{}个立即行动步骤", 
                response.getImmediateActions() != null ? response.getImmediateActions().size() : 0);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("应急处置方案生成测试失败", e);
            return ResponseEntity.internalServerError()
                .body("测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试3：查询2月14日当天的拥堵指数和事故总量
     */
    @GetMapping("/congestion-analysis")
    public ResponseEntity<?> testCongestionAnalysis() {
        try {
            logger.info("开始测试拥堵指数和事故总量查询功能");
            
            LocalDate targetDate = LocalDate.of(2024, 2, 14);
            
            TrafficDataAnalysisService.CongestionAnalysis analysis = trafficDataAnalysisService.analyzeCongestionAndAccidents(targetDate);
            
            logger.info("拥堵指数和事故总量查询测试完成，拥堵指数: {}, 事故总量: {}", 
                analysis.getCongestionIndex(), analysis.getAccidentCount());
            
            return ResponseEntity.ok(analysis);
            
        } catch (Exception e) {
            logger.error("拥堵指数和事故总量查询测试失败", e);
            return ResponseEntity.internalServerError()
                .body("测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试4：分析2024年2月中旬曼哈顿的交通风险
     */
    @GetMapping("/risk-analysis")
    public ResponseEntity<?> testRiskAnalysis() {
        try {
            logger.info("开始测试交通风险分析功能");
            
            // 2024年2月中旬 - 选择2月15日作为代表
            LocalDateTime targetDateTime = LocalDateTime.of(2024, 2, 15, 18, 0);
            
            RiskWarningReport report = riskWarningService.generateRiskWarning(targetDateTime);
            
            logger.info("交通风险分析测试完成，风险等级: {}, 综合评分: {}", 
                report.getRiskLevel(), 
                report.getRiskAnalysis() != null ? report.getRiskAnalysis().getOverallRiskScore() : "N/A");
            
            return ResponseEntity.ok(report);
            
        } catch (Exception e) {
            logger.error("交通风险分析测试失败", e);
            return ResponseEntity.internalServerError()
                .body("测试失败: " + e.getMessage());
        }
    }

    /**
     * 系统状态检查
     */
    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        return ResponseEntity.ok("测试控制器运行正常");
    }
}
