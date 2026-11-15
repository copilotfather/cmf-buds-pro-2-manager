@echo off
chcp 65001 >nul
title CMF Buds Pro 2 PC Manager
cd /d "%~dp0"
python.exe cmf_pc_manager.py
if %ERRORLEVEL% neq 0 py -3 cmf_pc_manager.py
pause