package org.example.smarttransportation.config;

import org.example.smarttransportation.service.KnowledgeBaseLoaderService;
import org.example.smarttransportation.service.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 知识库初始化器
 * 在应用启动时自动初始化向量数据库并加载知识库
 * 
 * @author pojin
 * @date 2025/12/21
 */
@Component
public class KnowledgeBaseInitializer implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseInitializer.class);
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Autowired
    private KnowledgeBaseLoaderService knowledgeBaseLoaderService;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("🚀 开始初始化知识库...");
        
        try {
            // 1. 初始化向量数据库集合
            logger.info("📊 初始化向量数据库集合...");
            boolean collectionInitialized = vectorStoreService.initializeCollection();
            
            if (!collectionInitialized) {
                logger.error("❌ 向量数据库集合初始化失败，跳过知识库加载");
                return;
            }
            
            logger.info("✅ 向量数据库集合初始化成功");
            
            // 2. 加载知识库文件到向量数据库
            logger.info("📚 加载知识库文件到向量数据库...");
            boolean knowledgeLoaded = knowledgeBaseLoaderService.loadKnowledgeBase();
            
            if (knowledgeLoaded) {
                logger.info("✅ 知识库加载成功");
            } else {
                logger.warn("⚠️ 知识库加载失败，但不影响系统启动");
            }
            
            logger.info("🎉 知识库初始化完成!");
            
        } catch (Exception e) {
            logger.error("❌ 知识库初始化过程中发生错误", e);
            // 不抛出异常，避免影响应用启动
        }
    }
}