package org.example.smarttransportation.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class TavilySearchService {

    @Value("${tavily.api-key}")
    private String apiKey;

    private final RestClient restClient;

    public TavilySearchService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://api.tavily.com").build();
    }

    public TavilyResponse search(String query) {
        TavilyRequest request = new TavilyRequest(apiKey, query, "advanced", true, 5);
        
        return restClient.post()
                .uri("/search")
                .body(request)
                .retrieve()
                .body(TavilyResponse.class);
    }

    public record TavilyRequest(
            @JsonProperty("api_key") String apiKey,
            String query,
            @JsonProperty("search_depth") String searchDepth,
            @JsonProperty("include_answer") boolean includeAnswer,
            @JsonProperty("max_results") int maxResults
    ) {}

    public record TavilyResponse(
            String answer,
            String query,
            List<TavilyResult> results
    ) {}

    public record TavilyResult(
            String title,
            String url,
            String content,
            double score
    ) {}
}
