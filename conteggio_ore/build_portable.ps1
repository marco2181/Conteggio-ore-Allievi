# Build Portatile - Conteggio Ore Allievi
# Versione PowerShell di build_portable.bat: Smart App Control blocca i .bat,
# ma PowerShell e' firmato da Microsoft quindi questo script viene eseguito.
# Avvio: doppio click sul collegamento "Crea Portatile", oppure
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
Write-Host " Build Portatile - Conteggio Ore Allievi"
Write-Host "============================================"

if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    Errore "Python non trovato nel PATH. Reinstalla Python da https://www.python.org e spunta 'Add python.exe to PATH'."
}

Write-Host "[1/4] Installazione dipendenze..."
python -m pip install -r requirements.txt
if ($LASTEXITCODE -ne 0) { Errore "installazione dipendenze fallita." }

Write-Host "[2/4] Creazione cartella portatile..."
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

Write-Host "[3/4] Creazione archivio ZIP..."
if (Test-Path "dist\ConteggioOreAllievi_Portatile.zip") {
    Remove-Item -Force "dist\ConteggioOreAllievi_Portatile.zip"
}
Compress-Archive -Path "dist\ConteggioOreAllievi" -DestinationPath "dist\ConteggioOreAllievi_Portatile.zip"

Write-Host "[4/4] Completato!" -ForegroundColor Green
Write-Host ""
Write-Host "Cartella portatile:  dist\ConteggioOreAllievi\"
Write-Host "Archivio ZIP:        dist\ConteggioOreAllievi_Portatile.zip"
Write-Host ""
Write-Host "Come usare:"
Write-Host "  - Copia la cartella 'ConteggioOreAllievi' su USB o dove vuoi"
Write-Host "  - Avvia ConteggioOreAllievi.exe dalla cartella"
Write-Host "  - Il database viene salvato in 'data\' nella stessa cartella"
Write-Host "  - I dati restano anche dopo la chiusura dell'app"
if (-not $NoPausa) { Read-Host "Premi INVIO per chiudere" }
