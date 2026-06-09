@echo off
echo ======================================
echo  Build - Conteggio Ore Allievi
echo ======================================

cd /d "%~dp0"

echo [1/3] Installazione dipendenze...
pip install -r requirements.txt

echo [2/3] Creazione eseguibile...
pyinstaller --onefile ^
    --windowed ^
    --name "ConteggioOreAllievi" ^
    --hidden-import "babel.numbers" ^
    --hidden-import "babel.dates" ^
    --hidden-import "reportlab.graphics" ^
    --hidden-import "reportlab.platypus" ^
    --hidden-import "tkcalendar" ^
    main.py

echo [3/3] Completato!
echo.
echo L'eseguibile si trova in: dist\ConteggioOreAllievi.exe
echo Il database verra' creato in:  dist\data\conteggio_ore.db
echo.
echo Per creare un installer per Windows, apri setup.iss con Inno Setup.
pause
