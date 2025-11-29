package org.example.smarttransportation.config;

import org.example.smarttransportation.service.TavilySearchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class ToolsConfig {

    public record WebSearchRequest(String query) {}

    @Bean
    @Description("Search the web for information using Tavily API. Use this tool when you need up-to-date information or facts not in your knowledge base.")
    public Function<WebSearchRequest, TavilySearchService.TavilyResponse> webSearch(TavilySearchService tavilySearchService) {
        return request -> tavilySearchService.search(request.query());
    }
}
