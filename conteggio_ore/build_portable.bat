@echo off
echo ============================================
echo  Build Portatile - Conteggio Ore Allievi
echo ============================================

cd /d "%~dp0"

echo [1/4] Installazione dipendenze...
pip install -r requirements.txt

echo [2/4] Creazione cartella portatile...
pyinstaller --onedir ^
    --windowed ^
    --name "ConteggioOreAllievi" ^
    --hidden-import "babel.numbers" ^
    --hidden-import "babel.dates" ^
    --hidden-import "reportlab.graphics" ^
    --hidden-import "reportlab.platypus" ^
    --hidden-import "tkcalendar" ^
    --clean ^
    -y ^
    main.py

if not exist "dist\ConteggioOreAllievi\ConteggioOreAllievi.exe" (
    echo ERRORE: la build ha fallito.
    pause
    exit /b 1
)

echo [3/4] Creazione archivio ZIP...
if exist "dist\ConteggioOreAllievi_Portatile.zip" del /f "dist\ConteggioOreAllievi_Portatile.zip"
powershell -Command "Compress-Archive -Path 'dist\ConteggioOreAllievi' -DestinationPath 'dist\ConteggioOreAllievi_Portatile.zip'"

echo [4/4] Completato!
echo.
echo Cartella portatile:  dist\ConteggioOreAllievi\
echo Archivio ZIP:        dist\ConteggioOreAllievi_Portatile.zip
echo.
echo Come usare:
echo   - Copia la cartella "ConteggioOreAllievi" su USB o dove vuoi
echo   - Avvia ConteggioOreAllievi.exe dalla cartella
echo   - Il database viene salvato in "data\" nella stessa cartella
echo   - I dati restano anche dopo la chiusura dell'app
echo.
pause
