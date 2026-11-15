@echo off
chcp 65001 >nul
title CMF Buds Pro 2 // PC Audio Relay Bridge
color 0A
echo ========================================================
echo   :: CMF BUDS PRO 2 // PC - PHONE - EARBUDS RELAY ::
echo ========================================================
echo.
echo [1] Проверка подключения телефона по USB...

:: Проверка ADB и проброс порта
adb.exe forward tcp:4242 tcp:4242 >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [OK] ADB обнаружен: порт 4242 проброшен через USB!
) else (
    echo [i] ADB не найден в PATH или USB-отладка выключена.
    echo     Используется прямое подключение через USB-модем (Tethering) или локальную сеть.
)

echo.
echo [2] Запуск трансляции звука Windows в CMF Buds Pro 2...
python.exe cmf_pc_relay.py
if %ERRORLEVEL% neq 0 (
    echo.
    echo Попытка запуска через встроенный Python...
    py -3 cmf_pc_relay.py
)

pause
