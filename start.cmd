@echo off
chcp 65001 >nul
cd /d %~dp0

if not exist "target\hse-iot-demo-0.0.1-SNAPSHOT.jar" (
    echo 首次运行, 正在构建项目...
    call mvn -B -DskipTests package
    if errorlevel 1 (
        echo.
        echo 构建失败, 请检查上面的输出。
        pause
        exit /b 1
    )
)

echo.
echo 服务地址: http://localhost:8080
echo 按 Ctrl+C 停止服务。
echo.
java -Dfile.encoding=UTF-8 -jar "target\hse-iot-demo-0.0.1-SNAPSHOT.jar"
pause
