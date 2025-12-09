@echo off
chcp 65001 >nul
echo ================================
echo 智能交通治理系统 - 一键启动
echo ================================
echo.

REM 检查 Docker
where docker >nul 2>&1
if errorlevel 1 (
    echo ❌ [错误] 未检测到 Docker，请先安装 Docker Desktop
    echo 📥 下载地址: https://www.docker.com/products/docker-desktop
    pause
    exit /b 1
)

REM 检查 Docker 是否运行
docker info >nul 2>&1
if errorlevel 1 (
    echo ❌ [错误] Docker 未运行，请先启动 Docker Desktop
    pause
    exit /b 1
)

echo ✅ Docker 检查通过
echo.

REM 询问是否清理
set /p CLEAN="是否清理旧的容器和镜像？(y/N): "
if /i "%CLEAN%"=="y" (
    echo 🧹 [1/5] 清理旧容器...
    docker-compose down -v
    docker rmi smarttransportation-app 2>nul
    echo ✅ 清理完成
    echo.
)

echo 🔨 [2/5] 构建应用镜像...
docker-compose build app
if errorlevel 1 (
    echo ❌ [错误] 构建失败
    pause
    exit /b 1
)
echo ✅ 构建完成
echo.

echo 🚀 [3/5] 启动所有服务...
docker-compose up -d
if errorlevel 1 (
    echo ❌ [错误] 启动失败
    pause
    exit /b 1
)
echo ✅ 服务启动成功
echo.

echo ⏳ [4/5] 等待服务就绪（约30秒）...
timeout /t 30 /nobreak >nul
echo ✅ 服务就绪
echo.

echo 📚 [5/5] 初始化知识库...
if exist "init_knowledge_base.py" (
    python init_knowledge_base.py
    if errorlevel 1 (
        echo ⚠️  知识库初始化失败，但系统仍可使用
    ) else (
        echo ✅ 知识库初始化完成
    )
) else (
    echo ⚠️  未找到知识库初始化脚本，跳过此步骤
)
echo.

echo ================================
echo ✅ 启动完成！
echo ================================
echo.
echo 📍 访问地址: http://localhost:8080
echo.
echo 📊 服务状态查看: docker-compose ps
echo 📝 查看日志: docker-compose logs -f app
echo 🛑 停止服务: docker-compose down
echo 🔄 重启服务: docker-compose restart app
echo.
echo 💡 提示: 如需查看实时日志，请运行: docker-compose logs -f
echo.
pause
