@echo off
echo ======================================
echo  Build - Conteggio Ore Allievi
echo ======================================

cd /d "%~dp0"

where python >nul 2>&1
if errorlevel 1 (
    echo ERRORE: Python non trovato nel PATH.
    echo Reinstalla Python da https://www.python.org e spunta "Add python.exe to PATH".
    pause
    exit /b 1
)

echo [1/3] Installazione dipendenze...
python -m pip install -r requirements.txt
if errorlevel 1 (
    echo ERRORE: installazione dipendenze fallita.
    pause
    exit /b 1
)

echo [2/3] Creazione eseguibile...
python -m PyInstaller --onefile ^
    --windowed ^
    --name "ConteggioOreAllievi" ^
    --hidden-import "babel.numbers" ^
    --hidden-import "babel.dates" ^
    --hidden-import "reportlab.graphics" ^
    --hidden-import "reportlab.platypus" ^
    --hidden-import "tkcalendar" ^
    main.py

if not exist "dist\ConteggioOreAllievi.exe" (
    echo ERRORE: la build ha fallito.
    pause
    exit /b 1
)

echo [3/3] Completato!
echo.
echo L'eseguibile si trova in: dist\ConteggioOreAllievi.exe
echo Il database verra' creato in:  dist\data\conteggio_ore.db
echo.
echo Per creare un installer per Windows, apri setup.iss con Inno Setup.
pause
