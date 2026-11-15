@echo off
chcp 65001 >nul
title ADB Toolbox // Phone Manager
cd /d "%~dp0"
python.exe adb_toolbox.py
if %ERRORLEVEL% neq 0 py -3 adb_toolbox.py
pause