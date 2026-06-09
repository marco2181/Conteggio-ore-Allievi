; Script Inno Setup - Conteggio Ore Allievi
; Per generare l'installer:
;   1. Scaricare Inno Setup gratis da: https://jrsoftware.org/isinfo.php
;   2. Aprire questo file con Inno Setup
;   3. Premere F9 (Compile) — genera ConteggioOreAllievi_Setup.exe

#define AppName "Conteggio Ore Allievi"
#define AppVersion "1.0"
#define AppPublisher "Studio Formazione"
#define AppExeName "ConteggioOreAllievi.exe"

[Setup]
AppId={{A1B2C3D4-E5F6-7890-ABCD-EF1234567890}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
OutputBaseFilename=ConteggioOreAllievi_Setup
Compression=lzma
SolidCompression=yes
WizardStyle=modern
; Il programma salva il DB nella sua cartella — servono permessi di scrittura
; Se installato in Program Files su Windows 10/11 potrebbe servire UAC
; Alternativa: installare in AppData dell'utente
PrivilegesRequired=admin

[Languages]
Name: "italian"; MessagesFile: "compiler:Languages\Italian.isl"

[Tasks]
Name: "desktopicon"; Description: "Crea collegamento sul Desktop"; GroupDescription: "Icone aggiuntive:"; Flags: unchecked

[Files]
; L'exe generato da build.bat
Source: "dist\{#AppExeName}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{group}\Disinstalla {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Avvia {#AppName}"; Flags: nowait postinstall skipifsilent
