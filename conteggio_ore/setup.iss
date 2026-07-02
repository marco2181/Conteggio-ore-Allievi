; Script Inno Setup - Conteggio Ore Allievi
; Per generare l'installer: eseguire build_installer.bat
; (oppure aprire questo file con Inno Setup e premere F9)
;
; L'installer usa la build --onedir (avvio più rapido della --onefile) e
; installa PER UTENTE in %LOCALAPPDATA%\Programs: nessuna richiesta UAC e
; la cartella è scrivibile, quindi il database in data\ accanto all'exe
; funziona esattamente come nella versione portatile.
; Alla disinstallazione la cartella data\ con il database NON viene toccata.

#define AppName "Conteggio Ore Allievi"
#define AppVersion "1.2"
#define AppPublisher "Studio Formazione"
#define AppExeName "ConteggioOreAllievi.exe"

[Setup]
AppId={{A1B2C3D4-E5F6-7890-ABCD-EF1234567890}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
; Installazione per-utente: niente UAC, cartella app scrivibile (serve per il DB)
PrivilegesRequired=lowest
DefaultDirName={autopf}\{#AppName}
DisableProgramGroupPage=yes
OutputDir=dist
OutputBaseFilename=ConteggioOreAllievi_Setup
UninstallDisplayIcon={app}\{#AppExeName}
Compression=lzma
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "italian"; MessagesFile: "compiler:Languages\Italian.isl"

[Tasks]
; Spuntato di default: collegamento sul Desktop
Name: "desktopicon"; Description: "Crea collegamento sul Desktop"; GroupDescription: "Icone aggiuntive:"

[Files]
; La cartella generata da build_portable.bat (PyInstaller --onedir)
; "Excludes: data\*" per non distribuire mai un database locale per errore
Source: "dist\ConteggioOreAllievi\*"; DestDir: "{app}"; Excludes: "data\*"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Avvia {#AppName}"; Flags: nowait postinstall skipifsilent
