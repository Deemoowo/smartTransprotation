#!/bin/bash

echo "================================"
echo "智能交通治理系统 - 一键启动"
echo "================================"
echo ""

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "❌ [错误] 未检测到 Docker，请先安装 Docker"
    echo "📥 下载地址: https://www.docker.com/products/docker-desktop"
    exit 1
fi

# 检查 Docker 是否运行
if ! docker info &> /dev/null; then
    echo "❌ [错误] Docker 未运行，请先启动 Docker Desktop"
    exit 1
fi

echo "✅ Docker 检查通过"
echo ""

# 清理旧的容器和镜像（可选）
read -p "是否清理旧的容器和镜像？(y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🧹 [1/5] 清理旧容器..."
    docker-compose down -v
    docker rmi smarttransportation-app 2>/dev/null || true
    echo "✅ 清理完成"
    echo ""
fi

echo "🔨 [2/5] 构建应用镜像..."
docker-compose build app
if [ $? -ne 0 ]; then
    echo "❌ [错误] 构建失败"
    exit 1
fi
echo "✅ 构建完成"
echo ""

echo "🚀 [3/5] 启动所有服务..."
docker-compose up -d
if [ $? -ne 0 ]; then
    echo "❌ [错误] 启动失败"
    exit 1
fi
echo "✅ 服务启动成功"
echo ""

echo "⏳ [4/5] 等待服务就绪（约30秒）..."
sleep 30
echo "✅ 服务就绪"
echo ""

echo "📚 [5/5] 初始化知识库..."
if [ -f "init_knowledge_base.py" ]; then
    python3 init_knowledge_base.py
    if [ $? -eq 0 ]; then
        echo "✅ 知识库初始化完成"
    else
        echo "⚠️  知识库初始化失败，但系统仍可使用"
    fi
else
    echo "⚠️  未找到知识库初始化脚本，跳过此步骤"
fi
echo ""

echo "================================"
echo "✅ 启动完成！"
echo "================================"
echo ""
echo "📍 访问地址: http://localhost:8080"
echo ""
echo "📊 服务状态查看: docker-compose ps"
echo "📝 查看日志: docker-compose logs -f app"
echo "🛑 停止服务: docker-compose down"
echo "🔄 重启服务: docker-compose restart app"
echo ""
echo "💡 提示: 如需查看实时日志，请运行: docker-compose logs -f"
echo ""
