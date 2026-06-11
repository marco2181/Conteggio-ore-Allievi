# Avvia l'app da sorgente (richiede Python installato)
# Versione PowerShell di avvia.bat: Smart App Control blocca i .bat,
# ma PowerShell e' firmato da Microsoft quindi questo script viene eseguito.
Set-Location $PSScriptRoot

if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Host "ERRORE: Python non trovato nel PATH." -ForegroundColor Red
    Write-Host "Reinstalla Python da https://www.python.org e spunta 'Add python.exe to PATH'."
    Read-Host "Premi INVIO per chiudere"
    exit 1
}

python main.py
