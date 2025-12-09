package org.example.smarttransportation.service;

import org.example.smarttransportation.dto.ChartData;
import org.example.smarttransportation.dto.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MetadataCacheService {

    private final Map<String, ChatResponse> cache = new ConcurrentHashMap<>();

    public void addCharts(String sessionId, List<ChartData> charts) {
        cache.compute(sessionId, (k, v) -> {
            if (v == null) {
                v = new ChatResponse();
                v.setCharts(new ArrayList<>());
                v.setQueriedTables(new ArrayList<>());
                v.setThoughts(new ArrayList<>());
                v.setInvolvesDataQuery(false);
            }
            if (charts != null) {
                if (v.getCharts() == null) {
                    v.setCharts(new ArrayList<>());
                }
                v.getCharts().addAll(charts);
                v.setInvolvesDataQuery(true);
            }
            return v;
        });
    }

    public void addQueriedTables(String sessionId, List<String> tables) {
        cache.compute(sessionId, (k, v) -> {
            if (v == null) {
                v = new ChatResponse();
                v.setCharts(new ArrayList<>());
                v.setQueriedTables(new ArrayList<>());
                v.setThoughts(new ArrayList<>());
                v.setInvolvesDataQuery(false);
            }
            if (tables != null) {
                if (v.getQueriedTables() == null) {
                    v.setQueriedTables(new ArrayList<>());
                }
                v.getQueriedTables().addAll(tables);
                v.setInvolvesDataQuery(true);
            }
            return v;
        });
    }

    public void addThought(String sessionId, String thought) {
        cache.compute(sessionId, (k, v) -> {
            if (v == null) {
                v = new ChatResponse();
                v.setCharts(new ArrayList<>());
                v.setQueriedTables(new ArrayList<>());
                v.setThoughts(new ArrayList<>());
                v.setInvolvesDataQuery(false);
            }
            if (thought != null) {
                if (v.getThoughts() == null) v.setThoughts(new ArrayList<>());
                v.getThoughts().add(thought);
            }
            return v;
        });
    }

    public void setSummary(String sessionId, String summary) {
        cache.compute(sessionId, (k, v) -> {
            if (v == null) {
                v = new ChatResponse();
                v.setCharts(new ArrayList<>());
                v.setQueriedTables(new ArrayList<>());
                v.setInvolvesDataQuery(false);
            }
            v.setDataQuerySummary(summary);
            return v;
        });
    }

    public ChatResponse getAndClear(String sessionId) {
        return cache.remove(sessionId);
    }
}
