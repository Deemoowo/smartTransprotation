# 智能交通治理系统 - 快速开始指南

本文档将指导您如何快速启动和运行智能交通治理系统。

## 📋 系统要求

- **Docker Desktop** 4.0+ (包含 Docker Engine 和 Docker Compose)
    - [Windows 下载](https://www.docker.com/products/docker-desktop)
    - [Mac 下载](https://www.docker.com/products/docker-desktop)
- **Python** 3.8+ (用于知识库初始化)
- **内存** 至少 8GB RAM (推荐 16GB)
- **磁盘空间** 至少 10GB 可用空间

## 🚀 快速启动

### 1. 克隆项目（如果尚未完成）
```bash
git clone <项目地址>
cd smartTransportation
```

### 2. 一键启动系统

#### Windows 用户
双击 `start.bat` 文件，或在命令行中运行：
```cmd
start.bat
```

#### Mac/Linux 用户
在终端中运行：
```bash
chmod +x start.sh
./start.sh
```

### 3. 启动过程说明

启动脚本将自动执行以下步骤：

1. **环境检查** - 验证 Docker 是否已安装并运行
2. **镜像构建** - 构建应用 Docker 镜像
3. **服务启动** - 启动所有必需的服务：
    - MySQL 数据库
    - Redis 缓存
    - Milvus 向量数据库
    - 应用服务
4. **等待就绪** - 等待所有服务完全启动（约30秒）
5. **知识库初始化** - 导入 SOP 和专家知识到向量数据库

## 🌐 访问系统

启动完成后，可通过以下地址访问系统：

- **主应用界面**: http://localhost:8080

## 🔧 系统配置

### 环境变量配置
系统通过环境变量进行配置，在 `docker-compose.yml` 中定义：

- **数据库**:
    - 用户名: `root`
    - 密码: `root`
    - 数据库: `smart_transportation`
- **Redis**: 默认配置
- **Milvus**: 默认配置

### 修改端口
如需修改默认端口，请编辑 `docker-compose.yml` 文件中的 `ports` 部分。
