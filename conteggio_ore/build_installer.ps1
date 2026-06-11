# Build Installer - Conteggio Ore Allievi
# Versione PowerShell di build_installer.bat: Smart App Control blocca i .bat,
# ma PowerShell e' firmato da Microsoft quindi questo script viene eseguito.
# Avvio: doppio click sul collegamento "Crea Installer", oppure
#        click destro su questo file -> Esegui con PowerShell
param([switch]$NoPausa)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Errore($msg) {
    Write-Host "ERRORE: $msg" -ForegroundColor Red
    if (-not $NoPausa) { Read-Host "Premi INVIO per chiudere" }
    exit 1
}

Write-Host "============================================"
Write-Host " Build Installer - Conteggio Ore Allievi"
Write-Host "============================================"

if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    Errore "Python non trovato nel PATH. Reinstalla Python da https://www.python.org e spunta 'Add python.exe to PATH'."
}

# Trova il compilatore Inno Setup (installazione utente o di sistema)
$iscc = "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe"
if (-not (Test-Path $iscc)) { $iscc = "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe" }
if (-not (Test-Path $iscc)) { $iscc = "$env:ProgramFiles\Inno Setup 6\ISCC.exe" }
if (-not (Test-Path $iscc)) {
    Errore "Inno Setup 6 non trovato. Installalo con:  winget install JRSoftware.InnoSetup"
}

Write-Host "[1/3] Installazione dipendenze..."
python -m pip install -r requirements.txt
if ($LASTEXITCODE -ne 0) { Errore "installazione dipendenze fallita." }

Write-Host "[2/3] Build applicazione (PyInstaller --onedir)..."
python -m PyInstaller --onedir `
    --windowed `
    --name "ConteggioOreAllievi" `
    --hidden-import "babel.numbers" `
    --hidden-import "babel.dates" `
    --hidden-import "reportlab.graphics" `
    --hidden-import "reportlab.platypus" `
    --hidden-import "tkcalendar" `
    --clean `
    -y `
    main.py
if (-not (Test-Path "dist\ConteggioOreAllievi\ConteggioOreAllievi.exe")) {
    Errore "la build ha fallito."
}

Write-Host "[3/3] Compilazione installer (Inno Setup)..."
& $iscc setup.iss
if ($LASTEXITCODE -ne 0) { Errore "compilazione installer fallita." }

Write-Host ""
Write-Host "Completato! Installer: dist\ConteggioOreAllievi_Setup.exe" -ForegroundColor Green
Write-Host " - Installa in %LOCALAPPDATA%\Programs\Conteggio Ore Allievi (senza UAC)"
Write-Host " - Crea voce nel menu Start e collegamento sul Desktop"
Write-Host " - I dati restano in data\ e sopravvivono alla disinstallazione"
Write-Host ""
Write-Host "NOTA: se Smart App Control blocca il Setup.exe al primo avvio,"
Write-Host "      attendi 1-2 minuti e riprova (il cloud Microsoft lo valuta)."
if (-not $NoPausa) { Read-Host "Premi INVIO per chiudere" }
