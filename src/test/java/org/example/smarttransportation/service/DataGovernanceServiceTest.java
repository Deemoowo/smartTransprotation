package org.example.smarttransportation.service;

import org.example.smarttransportation.entity.Complaint;
import org.example.smarttransportation.entity.TrafficAccident;
import org.example.smarttransportation.repository.ComplaintRepository;
import org.example.smarttransportation.repository.TrafficAccidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataGovernanceServiceTest {

    @Mock
    private TrafficAccidentRepository trafficAccidentRepository;

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private RAGService ragService;

    @InjectMocks
    private DataGovernanceService dataGovernanceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGenerateGovernanceProposal() {
        // 准备测试数据
        String location = "曼哈顿";
        LocalDateTime start = LocalDateTime.of(2024, 2, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 29, 23, 59);

        // 模拟事故数据
        TrafficAccident accident1 = new TrafficAccident();
        accident1.setOnStreetName("曼哈顿大道");
        accident1.setNumberOfPersonsInjured(2);
        accident1.setNumberOfPersonsKilled(0);
        accident1.setCrashDate(LocalDateTime.of(2024, 2, 15, 8, 30).toLocalDate());

        TrafficAccident accident2 = new TrafficAccident();
        accident2.setCrossStreetName("曼哈顿街");
        accident2.setNumberOfPersonsInjured(1);
        accident2.setNumberOfPersonsKilled(1);
        accident2.setCrashDate(LocalDateTime.of(2024, 2, 20, 18, 45).toLocalDate());

        List<TrafficAccident> accidents = Arrays.asList(accident1, accident2);

        // 模拟投诉数据
        Complaint complaint1 = new Complaint();
        complaint1.setComplaintType("交通拥堵");
        complaint1.setCreatedAt(LocalDateTime.of(2024, 2, 10, 9, 0));

        Complaint complaint2 = new Complaint();
        complaint2.setComplaintType("噪音污染");
        complaint2.setCreatedAt(LocalDateTime.of(2024, 2, 25, 15, 30));

        List<Complaint> complaints = Arrays.asList(complaint1, complaint2);

        // 设置模拟行为
        when(trafficAccidentRepository.findByDateRange(start.toLocalDate(), end.toLocalDate())).thenReturn(accidents);
        when(complaintRepository.findByLocationAndDateRange(location, start, end)).thenReturn(complaints);

        // 执行测试
        DataGovernanceService.GovernanceProposal proposal = dataGovernanceService.generateGovernanceProposal(location, start, end);

        // 验证结果
        assertNotNull(proposal);
        assertEquals(location, proposal.getLocation());
        assertEquals("2024-02-01 至 2024-02-29", proposal.getTimeRange());
        
        // 验证事故分析
        DataGovernanceService.AccidentAnalysis accidentAnalysis = proposal.getAccidentAnalysis();
        assertEquals(2, accidentAnalysis.getTotalAccidents());
        assertEquals(3, accidentAnalysis.getTotalInjuries());
        assertEquals(1, accidentAnalysis.getTotalFatalities());
        
        // 验证投诉分析
        DataGovernanceService.ComplaintAnalysis complaintAnalysis = proposal.getComplaintAnalysis();
        assertEquals(2, complaintAnalysis.getTotalComplaints());
        
        // 验证措施
        assertNotNull(proposal.getShortTermMeasures());
        assertNotNull(proposal.getLongTermMeasures());
        assertNotNull(proposal.getReferenceCases());
        
        // 验证诊断
        assertNotNull(proposal.getProblemDiagnosis());
        assertFalse(proposal.getProblemDiagnosis().isEmpty());
    }
}