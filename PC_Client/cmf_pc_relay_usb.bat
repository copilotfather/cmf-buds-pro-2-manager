@echo off
chcp 65001 >nul
title CMF Buds Pro 2 PC Audio Relay Bridge USB
color 0A
setlocal enabledelayedexpansion

cd /d "%~dp0"

echo ========================================================
echo   CMF Buds Pro 2 PC - PHONE - EARBUDS RELAY
echo ========================================================
echo.

set "ADB=adb.exe"
where adb.exe >nul 2>&1
if %ERRORLEVEL% neq 0 (
    if exist "%~dp0platform-tools\adb.exe" (
        set "ADB=%~dp0platform-tools\adb.exe"
    ) else (
        echo [i] ADB not found. Downloading Android Platform Tools...
        powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile '%~dp0platform-tools.zip'"
        if not exist "%~dp0platform-tools.zip" (
            echo [!] Download failed. Check internet.
            pause
            exit /b 1
        )
        echo [i] Extracting...
        powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%~dp0platform-tools.zip' -DestinationPath '%~dp0' -Force"
        del "%~dp0platform-tools.zip"
        set "ADB=%~dp0platform-tools\adb.exe"
    )
)

echo [1] Checking phone over USB...
"%ADB%" devices
echo.
echo If device is not listed, enable USB debugging on phone and allow access.
pause

echo [2] Forwarding port 4242 from PC to phone...
"%ADB%" forward tcp:4242 tcp:4242
if %ERRORLEVEL% neq 0 (
    echo [!] Port forward failed. Check USB and debugging.
    pause
    exit /b 1
)
echo [OK] Port forwarded.

echo.
echo [3] Starting Windows audio stream to CMF Buds Pro 2...
python.exe cmf_pc_relay.py
if %ERRORLEVEL% neq 0 (
    py -3 cmf_pc_relay.py
)

echo.
pause