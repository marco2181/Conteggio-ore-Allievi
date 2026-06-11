# Crea i collegamenti cliccabili per gli script PowerShell del progetto.
# Serve perche' Smart App Control blocca i .bat ma non PowerShell (firmato
# Microsoft): i collegamenti avviano powershell.exe con lo script giusto.
# Eseguire una volta sola: click destro -> Esegui con PowerShell
Set-Location $PSScriptRoot

$ws = New-Object -ComObject WScript.Shell
$powershell = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"

$collegamenti = @(
    @{ Nome = "Avvia App";      Script = "avvia.ps1" },
    @{ Nome = "Crea Installer"; Script = "build_installer.ps1" },
    @{ Nome = "Crea Portatile"; Script = "build_portable.ps1" }
)

foreach ($c in $collegamenti) {
    $lnk = $ws.CreateShortcut("$PSScriptRoot\$($c.Nome).lnk")
    $lnk.TargetPath = $powershell
    $lnk.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$PSScriptRoot\$($c.Script)`""
    $lnk.WorkingDirectory = $PSScriptRoot
    $lnk.Save()
    Write-Host "Creato: $($c.Nome).lnk -> $($c.Script)"
}
Write-Host "Fatto. Usa i collegamenti con doppio click." -ForegroundColor Green
