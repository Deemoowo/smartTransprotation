# 🤖 T-Agent 智慧交通AI助手

> 您的专属智能交通数据分析与风险预警助手
<img width="3024" height="1646" alt="image" src="https://github.com/user-attachments/assets/86237309-7f6c-47ca-8c83-46c9498953a7" />

## 📋 项目简介

T-Agent是一个基于Spring Boot 3.3.3和阿里云千问大模型的智慧交通AI助手系统。专注于纽约市曼哈顿区的交通数据分析，提供智能对话、数据查询和风险预警功能。

### ✨ 核心功能

- 🤖 **智能对话** - 基于千问API的自然语言交互
- 📊 **交通数据分析** - 实时分析交通事故、天气、地铁客流等数据
- ⚠️ **风险预警** - 智能识别和预警交通风险
- 💬 **对话历史** - 完整的会话管理和上下文理解
- 🎨 **现代化界面** - 响应式AI对话界面

### 🏗️ 技术架构

- **后端框架**: Spring Boot 3.3.3
- **AI服务**: 阿里云DashScope千问API
- **数据库**: MySQL 8.0+
- **缓存**: Redis (可选)
- **向量数据库**: Milvus
- **前端**: 现代化响应式HTML/CSS/JavaScript

## 🚀 快速启动

### 环境要求

- Java 17+
- MySQL 8.0+
- Maven 3.6+
- 阿里云DashScope API密钥

### 1. 克隆项目

```bash
git clone https://github.com/Deemoowo/smartTransprotation
cd smartTransportation
```

### 2. 配置数据库

创建MySQL数据库：

```sql
CREATE DATABASE smart_transportation CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 修改配置文件

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: smartTransportation
  main:
    allow-bean-definition-overriding: true
  ai:
    dashscope:
      api-key: your_api_key

  datasource:
    url: jdbc:mysql://localhost:3306/smart_transportation?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: your_mysql_username
    password: your_mysql_password
```

### 4. 启动项目

```bash
# 使用Maven启动
./mvnw spring-boot:run
```

### 5. 访问应用

打开浏览器访问：http://localhost:8080

## 🔧 开发指南

### 项目结构

```
src/main/java/org/example/smarttransportation/
├── controller/          # REST API控制器
│   └── ChatController.java
├── service/            # 业务服务层
│   ├── AIAssistantService.java
│   └── TrafficDataAnalysisService.java
├── entity/             # 数据实体类
├── repository/         # 数据访问层
├── dto/               # 数据传输对象
└── config/            # 配置类

src/main/resources/
├── application.yml     # 应用配置
└── static/
    └── index.html     # 前端界面
```

### 核心组件

- **ChatController** - AI对话REST API控制器
- **AIAssistantService** - 核心AI助手服务，集成千问API
- **TrafficDataAnalysisService** - 交通数据分析服务


## 🤝 贡献

欢迎提交Issue和Pull Request来改进项目。

---

**T-Agent** - 让交通更智慧，让出行更安全！

