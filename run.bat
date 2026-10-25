@echo off
title VaxSlot Connect — Vaccination Slot Booking Server
setlocal

:: Ensure the working directory is always the script's folder
cd /d "%~dp0"

echo ====================================================================
echo   🏥 VAXSLOT CONNECT - VACCINATION SLOT BOOKING SYSTEM
echo ====================================================================
echo.

:: Ensure Java path is available
set "PATH=%PATH%;C:\Program Files\Common Files\Oracle\Java\javapath"

:: Create bin directory if not exists
if not exist "bin" mkdir bin

echo [1/3] Compiling Java backend...
javac -d bin src\model\*.java src\dsa\*.java src\util\*.java src\service\*.java src\Main.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Java compilation failed! Please verify that JDK is installed.
    echo.
    pause
    exit /b %ERRORLEVEL%
)

echo [2/3] Compilation successful!
echo.
echo [3/3] Starting server on http://localhost:8080 ...
echo       Patient Portal:   http://localhost:8080/index.html
echo       Staff Portal:     http://localhost:8080/admin.html
echo.
echo [INFO] Opening your browser in 2 seconds...
echo [INFO] Press Ctrl+C in this window anytime to stop the server.
echo ====================================================================
echo.

:: Launch browser in background after 2 seconds so server is fully bound
start "" cmd /c "timeout /t 2 /nobreak >nul & start http://localhost:8080/index.html"

:: Run the Java server in foreground
java -cp bin Main

echo.
echo Server has stopped.
pause
