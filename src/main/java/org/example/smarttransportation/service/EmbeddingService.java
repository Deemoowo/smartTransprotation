package org.example.smarttransportation.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.ArrayList;

/**
 * 文本向量化服务
 * 负责将文本转换为向量表示，用于语义搜索
 * 
 * @author pojin
 * @date 2025/11/22
 */
@Service
public class EmbeddingService {
    
    @Autowired(required = false)
    @Qualifier("openAiEmbeddingModel")
    private EmbeddingModel embeddingModel;
    
    /**
     * 将单个文本转换为向量
     */
    public List<Float> embedText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("文本内容不能为空");
        }
        
        if (embeddingModel == null) {
            throw new IllegalStateException("嵌入模型未配置，请检查Spring AI配置");
        }
        
        try {
            // 使用 Spring AI 的简化 API
            float[] embedding = embeddingModel.embed(text);

            // 转换为 List<Float>
            List<Float> result = new ArrayList<>();
            for (float value : embedding) {
                result.add(value);
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException("文本向量化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 批量文本向量化
     */
    public List<List<Float>> embedTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("文本列表不能为空");
        }
        
        if (embeddingModel == null) {
            throw new IllegalStateException("嵌入模型未配置，请检查Spring AI配置");
        }

        try {
            // 使用 Spring AI 的批量 API
            List<String> validTexts = new ArrayList<>();
            for (String text : texts) {
                validTexts.add(StringUtils.hasText(text) ? text : "");
            }

            List<float[]> embeddings = embeddingModel.embed(validTexts);

            // 转换为 List<List<Float>>
            List<List<Float>> result = new ArrayList<>();
            for (float[] embedding : embeddings) {
                List<Float> floatList = new ArrayList<>();
                for (float value : embedding) {
                    floatList.add(value);
                }
                result.add(floatList);
            }

            return result;

        } catch (Exception e) {
            throw new RuntimeException("批量文本向量化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 计算两个向量的余弦相似度
     */
    public double calculateCosineSimilarity(List<Float> vector1, List<Float> vector2) {
        if (vector1 == null || vector2 == null || vector1.size() != vector2.size()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.size(); i++) {
            float v1 = vector1.get(i);
            float v2 = vector2.get(i);
            
            dotProduct += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    

    

    
    /**
     * 获取向量维度
     */
    public int getEmbeddingDimension() {
        return 1536; // 修改为与Python脚本一致的1536维向量
    }
}