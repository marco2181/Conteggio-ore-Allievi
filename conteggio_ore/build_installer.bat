@echo off
echo ============================================
echo  Build Installer - Conteggio Ore Allievi
echo ============================================

cd /d "%~dp0"

where python >nul 2>&1
if errorlevel 1 (
    echo ERRORE: Python non trovato nel PATH.
    echo Reinstalla Python da https://www.python.org e spunta "Add python.exe to PATH".
    pause
    exit /b 1
)

rem Trova il compilatore Inno Setup (installazione utente o di sistema)
set "ISCC=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=%ProgramFiles%\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" (
    echo ERRORE: Inno Setup 6 non trovato.
    echo Installalo con:  winget install JRSoftware.InnoSetup
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

echo [2/3] Build applicazione (PyInstaller --onedir)...
python -m PyInstaller --onedir ^
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

echo [3/3] Compilazione installer (Inno Setup)...
"%ISCC%" setup.iss
if errorlevel 1 (
    echo ERRORE: compilazione installer fallita.
    pause
    exit /b 1
)

echo.
echo Completato! Installer: dist\ConteggioOreAllievi_Setup.exe
echo  - Installa in %%LOCALAPPDATA%%\Programs\Conteggio Ore Allievi (senza UAC)
echo  - Crea voce nel menu Start e collegamento sul Desktop
echo  - I dati restano in data\ e sopravvivono alla disinstallazione
echo.
pause
