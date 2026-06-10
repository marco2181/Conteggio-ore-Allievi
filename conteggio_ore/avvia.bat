@echo off
cd /d "%~dp0"

where python >nul 2>&1
if errorlevel 1 (
    echo ERRORE: Python non trovato nel PATH.
    echo Reinstalla Python da https://www.python.org e spunta "Add python.exe to PATH".
    pause
    exit /b 1
)

python main.py
pause
