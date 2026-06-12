@echo off
title DevOps Copilot - CLI
echo ========================================
echo  DevOps Copilot - 命令行模式
echo  如果报错请在 IDEA 终端中运行
echo ========================================
echo.
echo 正在编译并启动应用...
echo.
mvn spring-boot:run -Dspring-boot.run.arguments=--cli
echo.
echo 程序已退出。
pause
