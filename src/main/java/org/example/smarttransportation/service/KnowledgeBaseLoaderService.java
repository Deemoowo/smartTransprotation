package org.example.smarttransportation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识库加载服务
 * 负责解析Markdown格式的知识库文件并将内容加载到向量数据库
 * 
 * @author pojin
 * @date 2025/12/21
 */
@Service
public class KnowledgeBaseLoaderService {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseLoaderService.class);
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    private static final String KNOWLEDGE_FILE_PATH = "knowledge.md";
    
    /**
     * 加载知识库到向量数据库
     */
    public boolean loadKnowledgeBase() {
        try {
            logger.info("开始加载知识库文件: {}", KNOWLEDGE_FILE_PATH);
            
            // 读取知识库文件内容
            String content = readKnowledgeFile();
            if (!StringUtils.hasText(content)) {
                logger.warn("知识库文件为空或不存在");
                return false;
            }
            
            // 解析并分块处理内容
            List<KnowledgeChunk> chunks = parseKnowledgeContent(content);
            logger.info("解析出 {} 个知识块", chunks.size());
            
            // 转换为VectorStoreService所需的格式
            List<VectorStoreService.DocumentInfo> documents = new ArrayList<>();
            for (KnowledgeChunk chunk : chunks) {
                documents.add(new VectorStoreService.DocumentInfo(
                    chunk.getContent(),
                    chunk.getCategory(),
                    chunk.getTitle()
                ));
            }
            
            // 批量添加到向量数据库
            boolean success = vectorStoreService.addDocuments(documents);
            
            if (success) {
                logger.info("成功加载 {} 个知识块到向量数据库", documents.size());
            } else {
                logger.error("加载知识库到向量数据库失败");
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("加载知识库失败", e);
            return false;
        }
    }
    
    /**
     * 读取知识库文件内容
     */
    private String readKnowledgeFile() throws IOException {
        // 首先尝试从项目根目录读取
        Path filePath = Paths.get(KNOWLEDGE_FILE_PATH);
        if (Files.exists(filePath)) {
            logger.info("从项目根目录读取知识库文件: {}", filePath.toAbsolutePath());
            return Files.readString(filePath, StandardCharsets.UTF_8);
        }
        
        // 尝试从classpath读取
        try {
            Resource resource = new ClassPathResource(KNOWLEDGE_FILE_PATH);
            if (resource.exists()) {
                logger.info("从classpath读取知识库文件");
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    return content.toString();
                }
            }
        } catch (IOException e) {
            logger.debug("从classpath读取失败: {}", e.getMessage());
        }
        
        logger.error("无法找到知识库文件: {}", KNOWLEDGE_FILE_PATH);
        return "";
    }
    
    /**
     * 解析知识库内容，按章节和SOP条目进行分块
     */
    private List<KnowledgeChunk> parseKnowledgeContent(String content) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        
        // 按行分割内容
        String[] lines = content.split("\n");
        
        StringBuilder currentChunk = new StringBuilder();
        String currentTitle = "";
        String currentCategory = "general";
        
        // 正则表达式匹配不同的标题级别和SOP条目
        Pattern chapterPattern = Pattern.compile("^第([一二三四])章[：:](.+)");
        Pattern sopPattern = Pattern.compile("^\\[SOP-([A-Z]{2,3})-([A-Z0-9]+)\\](.+)");
        Pattern sectionPattern = Pattern.compile("^(\\d+\\.\\d+)\\s+(.+)");
        Pattern subsectionPattern = Pattern.compile("^●\\s*\\[(.+)\\]:");
        
        for (String line : lines) {
            line = line.trim();
            
            // 检查是否是新的章节
            Matcher chapterMatcher = chapterPattern.matcher(line);
            if (chapterMatcher.matches()) {
                // 保存前一个块
                if (currentChunk.length() > 0) {
                    chunks.add(new KnowledgeChunk(currentTitle, currentCategory, currentChunk.toString().trim()));
                }
                
                // 开始新的章节
                currentTitle = "第" + chapterMatcher.group(1) + "章: " + chapterMatcher.group(2);
                currentCategory = "chapter";
                currentChunk = new StringBuilder();
                currentChunk.append(line).append("\n");
                continue;
            }
            
            // 检查是否是SOP条目
            Matcher sopMatcher = sopPattern.matcher(line);
            if (sopMatcher.matches()) {
                // 保存前一个块
                if (currentChunk.length() > 0) {
                    chunks.add(new KnowledgeChunk(currentTitle, currentCategory, currentChunk.toString().trim()));
                }
                
                // 开始新的SOP条目
                currentTitle = "SOP-" + sopMatcher.group(1) + "-" + sopMatcher.group(2) + ": " + sopMatcher.group(3);
                currentCategory = "sop";
                currentChunk = new StringBuilder();
                currentChunk.append(line).append("\n");
                continue;
            }
            
            // 检查是否是小节标题
            Matcher sectionMatcher = sectionPattern.matcher(line);
            if (sectionMatcher.matches()) {
                // 保存前一个块
                if (currentChunk.length() > 0) {
                    chunks.add(new KnowledgeChunk(currentTitle, currentCategory, currentChunk.toString().trim()));
                }
                
                // 开始新的小节
                currentTitle = sectionMatcher.group(1) + " " + sectionMatcher.group(2);
                currentCategory = "section";
                currentChunk = new StringBuilder();
                currentChunk.append(line).append("\n");
                continue;
            }
            
            // 检查是否是子条目
            Matcher subsectionMatcher = subsectionPattern.matcher(line);
            if (subsectionMatcher.matches()) {
                // 保存前一个块
                if (currentChunk.length() > 0) {
                    chunks.add(new KnowledgeChunk(currentTitle, currentCategory, currentChunk.toString().trim()));
                }
                
                // 开始新的子条目
                currentTitle = subsectionMatcher.group(1);
                currentCategory = "subsection";
                currentChunk = new StringBuilder();
                currentChunk.append(line).append("\n");
                continue;
            }
            
            // 普通内容行，添加到当前块
            if (StringUtils.hasText(line)) {
                currentChunk.append(line).append("\n");
            }
            
            // 如果当前块太长，进行分割
            if (currentChunk.length() > 1500) {
                chunks.add(new KnowledgeChunk(currentTitle, currentCategory, currentChunk.toString().trim()));
                currentChunk = new StringBuilder();
                // 保持标题信息，但添加序号
                currentTitle = currentTitle + " (续)";
            }
        }
        
        // 添加最后一个块
        if (currentChunk.length() > 0) {
            chunks.add(new KnowledgeChunk(currentTitle, currentCategory, currentChunk.toString().trim()));
        }
        
        return chunks;
    }
    
    /**
     * 知识块数据类
     */
    public static class KnowledgeChunk {
        private String title;
        private String category;
        private String content;
        
        public KnowledgeChunk(String title, String category, String content) {
            this.title = title;
            this.category = category;
            this.content = content;
        }
        
        // Getters
        public String getTitle() { return title; }
        public String getCategory() { return category; }
        public String getContent() { return content; }
    }
}