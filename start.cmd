@echo off
chcp 65001 >nul
cd /d %~dp0

set "PORT=8080"
for /f "tokens=2" %%p in ('findstr /b /c:"  port:" src\main\resources\application.yml') do set "PORT=%%p"

echo Building project, please wait...
call mvn -B -q -DskipTests package
if errorlevel 1 (
    echo.
    echo Build failed, please check the output above.
    pause
    exit /b 1
)

echo.
echo URL: http://localhost:%PORT%
echo Press Ctrl+C to stop the service.
echo.
java -Dfile.encoding=UTF-8 -jar "target\hse-iot-demo-0.0.1-SNAPSHOT.jar"
pause
