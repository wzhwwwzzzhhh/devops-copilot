@echo off
echo ========================================
echo Elasticsearch 连接测试
echo ========================================
echo.

echo [1] 测试本地 ES 连接...
curl.exe http://127.0.0.1:9200
if %errorlevel% neq 0 (
    echo.
    echo ❌ 连接失败！请检查：
    echo   1. ES 容器是否运行: docker ps
    echo   2. 端口映射是否正确
    echo   3. 隧道连接是否正常
    echo   4. 防火墙设置
) else (
    echo.
    echo ✅ ES 连接成功！
)

echo.
echo [2] 测试索引是否存在...
curl.exe http://127.0.0.1:9200/devops-knowledge
if %errorlevel% neq 0 (
    echo.
    echo ℹ️  索引不存在（这是正常的，应用会自动创建）
) else (
    echo.
    echo ✅ 索引已存在
)

echo.
echo ========================================
echo 测试完成
echo ========================================
pause
