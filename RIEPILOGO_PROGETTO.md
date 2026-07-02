# Conteggio Ore Allievi — Documentazione Completa

## Descrizione

Applicazione desktop Windows per la gestione delle presenze e il conteggio delle ore degli allievi iscritti a corsi di formazione. Permette di registrare le presenze giornaliere per fascia oraria, monitorare l'avanzamento rispetto al monte ore del corso ed esportare report in PDF.

---

## Requisiti concordati

| Parametro | Valore |
|---|---|
| Piattaforma | Windows (app desktop) |
| Utenti | Singolo utente locale |
| Database | SQLite locale (file `.db`) |
| Corsi | Nome personalizzato + monte ore (es. 20/90/150/300 h) |
| Allievi | Un solo corso per allievo (con possibilità di ore personalizzate) |
| Ritardi | L'operatore modifica manualmente le ore del turno |
| Assenze | Non si segnano esplicitamente (basta non marcare la presenza) |
| Più turni/giorno | Sì (es. mercoledì pomeriggio + sera nello stesso giorno) |
| Completamento | Notifica 80% (popup), banner 100% in Dashboard |
| Export | PDF: scheda individuale + registro mensile |

---

## Turni fissi

| Giorno | Fascia | Ore default |
|---|---|---|
| Lunedì | Mattina | 3.0 h |
| Martedì | Pomeriggio | 3.0 h |
| Mercoledì | Pomeriggio | 3.0 h |
| Mercoledì | Sera | 2.5 h |
| Giovedì | Sera | 2.5 h |
| Venerdì | Mattina | 3.0 h |
| Sabato | Mattina | 3.0 h |
| Sabato | Pomeriggio | 3.0 h |

> I turni sono pre-caricati nel database all'avvio. Se un allievo arriva in ritardo, l'operatore modifica manualmente il campo "Ore" nella schermata Presenze.

---

## Stack tecnologico

| Componente | Tecnologia |
|---|---|
| Linguaggio | Python 3.11+ |
| GUI | CustomTkinter (tema moderno) |
| Database | SQLite (sqlite3, incluso in Python) |
| Calendario UI | tkcalendar (DateEntry, font 14, width 16) |
| Generazione PDF | ReportLab |
| Packaging .exe portatile | PyInstaller `--onedir` → cartella + ZIP (~70 MB) |
| Installer Windows | Inno Setup (file `setup.iss` incluso) |

---

## Struttura del progetto

```
conteggio_ore/
├── main.py                  # Entry point
├── avvia.bat                # Avvia l'app con Python
├── build.bat                # Genera .exe onefile (PyInstaller)
├── build_portable.bat       # Genera cartella portatile + ZIP (PyInstaller --onedir)
├── setup.iss                # Script Inno Setup per installer Windows
├── test_portable.py         # Suite di test automatici (14 test)
├── requirements.txt         # Dipendenze pip
│
├── database/
│   ├── db.py                # Connessione SQLite, schema, init turni, migrazione
│   └── models.py            # Tutte le funzioni CRUD
│
├── logic/
│   ├── sessions.py          # Nomi giorni/slot, colori barra progresso
│   └── pdf_generator.py     # Generazione PDF con ReportLab
│
├── ui/
│   ├── app.py               # Finestra principale, sidebar navigazione
│   ├── dashboard.py         # Panoramica allievi con progress bar
│   ├── attendance.py        # Registrazione presenze + popup 80%
│   ├── students.py          # Gestione allievi + storico + elimina archiviati
│   ├── courses.py           # Gestione corsi
│   └── reports.py           # Export PDF individuale e mensile
│
├── assets/                  # Icone e risorse grafiche
└── data/
    └── conteggio_ore.db     # Database SQLite (creato automaticamente)
```

---

## Schema database

```sql
CREATE TABLE courses (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT NOT NULL UNIQUE,
    total_hours  REAL NOT NULL,
    created_at   TEXT DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE students (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT NOT NULL,
    course_id       INTEGER NOT NULL REFERENCES courses(id),
    enrollment_date TEXT NOT NULL,
    active          INTEGER DEFAULT 1,   -- 0 = archiviato
    custom_hours    REAL DEFAULT NULL    -- se impostato, sovrascrive le ore del corso
);

-- Corso di sistema (creato automaticamente da init_db, non visibile all'utente)
-- name = '__LIBERO__', total_hours = 0
-- Usato per allievi "Senza corso" che hanno ore individuali tramite custom_hours

CREATE TABLE sessions (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    day_of_week   INTEGER NOT NULL,  -- 0=Lun...5=Sab
    slot          TEXT NOT NULL,     -- 'mattina' | 'pomeriggio' | 'sera'
    default_hours REAL NOT NULL
);

CREATE TABLE attendance (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id     INTEGER NOT NULL REFERENCES students(id),
    session_id     INTEGER NOT NULL REFERENCES sessions(id),
    date           TEXT NOT NULL,
    hours_attended REAL NOT NULL,
    notes          TEXT,
    UNIQUE(student_id, session_id, date)
);
```

> **Migrazione automatica:** se si apre un database creato prima dell'aggiunta di `custom_hours`, la colonna viene aggiunta automaticamente da `init_db()` senza perdere dati.

---

## Schermate dell'applicazione

### 1. Dashboard
- Tabella allievi attivi con barra progresso colorata (verde <80%, arancio 80-99%, rosso 100%)
- Tre card riassuntive: allievi attivi, corsi completati, quasi al termine
- Banner rosso quando uno o più allievi hanno completato il monte ore
- Per gli allievi al 100%: pulsanti "Nuovo corso" e "Elimina"

### 2. Registro Presenze
- Selettore data grande (font 14, campo largo)
- Turni del giorno selezionato mostrati automaticamente
- Per ogni turno: checkbox per ogni allievo + ore modificabili (default pre-compilato)
- Checkbox "Tutti" per selezione rapida
- **Popup automatico** dopo il salvataggio se un allievo supera la soglia dell'80%

### 3. Allievi
- Ricerca per nome, toggle "Mostra archiviati"
- **Nuovo allievo**: nome + corso + data iscrizione + ore personalizzate opzionali
  - Prima opzione nel dropdown: **"— Senza corso (ore individuali) —"** → campo ore obbligatorio
  - Opzioni successive: corsi formali creati nella sezione Corsi
- **Modifica**: stessi campi + possibilità di cambiare corso e ore
- **Archivia/Ripristina**: conserva lo storico presenze
- **Elimina** (solo per archiviati): cancellazione definitiva con conferma, rimuove anche le presenze

### 4. Corsi
- Lista corsi con nome e monte ore
- Nuovo/Modifica: nome + ore (preset 20/90/150/300 o valore libero)
- Elimina: solo se nessun allievo attivo è iscritto

### 5. Report PDF
- **Scheda individuale**: allievo + intervallo date → PDF con tabella presenze e totali
- **Registro mensile**: mese/anno → PDF con tutti gli allievi attivi
- Salvataggio con dialogo file → apertura automatica del PDF

---

## Notifiche e avvisi

| Evento | Dove | Tipo |
|---|---|---|
| Allievo raggiunge ≥80% | Dopo salvataggio presenze (qualunque schermata) | Popup arancio con lista |
| Allievo raggiunge 100% | Dashboard (al caricamento) | Banner rosso in cima alla lista |

---

## Allievi senza corso (ore individuali)

Per allievi già avviati con un monte ore residuo diverso da tutti gli altri (es. 30+ allievi ognuno con ore rimanenti diverse), non è necessario creare un corso per ciascuno:

1. Aprire **Allievi → Nuovo allievo**
2. Nel dropdown "Corso" selezionare **"— Senza corso (ore individuali) —"** (prima voce)
3. Inserire le **ore rimanenti** per quell'allievo nel campo obbligatorio (es. `45`)
4. Salvare

Ogni allievo ha il proprio monte ore indipendente. Dashboard, storico e PDF mostrano "Senza corso" come etichetta. Il nome interno `__LIBERO__` non è mai visibile all'utente e il corso di sistema non può essere cancellato.

---

## Ore personalizzate per allievo con corso

Se un allievo iscritto a un corso formale ha un monte ore diverso dal default del corso:
- Nel form "Nuovo allievo" o "Modifica allievo", attivare il checkbox "Ore personalizzate"
- Inserire il numero di ore effettive per quell'allievo
- Dashboard, PDF e percentuali useranno le ore personalizzate al posto del totale del corso

---

## Formato dei PDF

### Scheda individuale
- Intestazione: nome allievo, corso, ore totali/frequentate, **ore rimanenti** (o "Corso completato"), % completamento, periodo
- Tabella: Data | Giorno | Turno | Ore frequentate | Note
- Riga totale in fondo

### Registro mensile
- Intestazione: mese/anno
- Tabella: Nome allievo | Corso | Ore mese | Ore totali | Monte ore | **Ore rimanenti** | Compl.
- La colonna "Compl." è colorata (verde/arancio/rosso)

### Report per corso
- Tabella: Allievo | Iscrizione | Ore fatte | Monte ore | **Ore rimanenti** | Completamento

---

## Installazione e avvio

### Prima installazione (con Python)

1. Scaricare **Python 3.11+** da [python.org](https://www.python.org/downloads/) — spuntare **"Add Python to PATH"**
2. Aprire il terminale nella cartella `conteggio_ore/`
3. Eseguire:
   ```
   pip install -r requirements.txt
   python main.py
   ```
   oppure doppio clic su `avvia.bat`

### Creare la versione portatile (consigliato)

1. Doppio clic su `build_portable.bat`
2. Attendere 2-3 minuti
3. Nella cartella `dist/` vengono creati:
   - `ConteggioOreAllievi/` — cartella portatile da copiare ovunque (USB, condivisione)
   - `ConteggioOreAllievi_Portatile.zip` — archivio ZIP pronto da inviare (~30 MB)
4. Avviare `ConteggioOreAllievi/ConteggioOreAllievi.exe`
5. Il database si crea in `ConteggioOreAllievi/data/conteggio_ore.db` e persiste tra i riavvii

| | Portatile | .exe singolo |
|---|---|---|
| Avvio | Rapido (nessuna estrazione) | Lento (estrae in temp) |
| Spostabile su USB | Sì, copia la cartella | Sì, copia solo l'exe |
| Aggiornamento | Sostituisci la cartella | Sostituisci il file |

### Creare il file .exe singolo

1. Doppio clic su `build.bat`
2. L'exe viene generato in `dist/ConteggioOreAllievi.exe` (~5 MB)
3. Il database viene salvato in `dist/data/conteggio_ore.db` (persistente tra i riavvii)

### Creare un installer Windows professionale

1. Scaricare **Inno Setup** gratis da [jrsoftware.org/isinfo.php](https://jrsoftware.org/isinfo.php)
2. Prima eseguire `build.bat` per generare l'exe
3. Aprire `setup.iss` con Inno Setup → premere **F9** (Compile)
4. Viene generato `ConteggioOreAllievi_Setup.exe` — installer completo con collegamento Desktop e voce nel Pannello di controllo

---

## Backup dei dati

Il database SQLite si trova in:
```
conteggio_ore/data/conteggio_ore.db                      ← avvio con Python
dist/ConteggioOreAllievi/data/conteggio_ore.db           ← versione portatile
dist/data/conteggio_ore.db                               ← exe singolo
```

### Backup automatici (nessuna azione richiesta)

Tutti i backup automatici vanno in `Documenti\ConteggioOreAllievi\backups\` (sopravvivono a reinstallazioni e aggiornamenti):

| File | Quando viene creato |
|---|---|
| `ultimo_salvataggio.db` | **Ad ogni salvataggio** (presenze, allievi, corsi, turni) — sempre aggiornato all'ultimo stato |
| `auto_AAAAMMGG.db` | Uno al giorno all'avvio dell'app (rotazione: ultimi 7 giorni) |

Tutti i backup usano l'API di backup di SQLite: includono anche le transazioni nel journal WAL, quindi sono sempre coerenti anche se creati mentre l'app è in uso.

### Backup / ripristino manuale

Da **Impostazioni → 📦 Esporta backup / 📥 Importa backup**. In caso di crash o database danneggiato, importare `ultimo_salvataggio.db` dalla cartella dei backup automatici.

---

## Test automatici

Il file `test_portable.py` esegue 14 test automatici su DB e logica senza aprire la GUI:

```
python test_portable.py
```

| # | Test |
|---|---|
| 1 | DB creato nella cartella portatile |
| 2 | Corso di sistema `__LIBERO__` presente |
| 3 | `get_all_courses()` esclude il corso sistema |
| 4 | Allievo senza corso con ore individuali |
| 5 | Allievo con corso usa le ore del corso |
| 6 | Registrazione presenze e calcolo ore cumulative |
| 7 | Rilevamento crossing soglia 80% |
| 8 | Report mensile con dati corretti |
| 9 | Archivia e ripristina allievo |
| 10 | Cancellazione corso sistema bloccata |
| 11 | Cancellazione corso con allievi attivi bloccata |
| 12 | Modifica ore personalizzate allievo |
| 13 | Turni fissi settimana (8 turni su 6 giorni) |
| 14 | DB persiste nella cartella portatile |

---

## Dipendenze (requirements.txt)

```
customtkinter>=5.2.0
tkcalendar>=1.6.1
reportlab>=4.0.0
pyinstaller>=6.0.0
```

---

## Registro modifiche

| Data | Modifica |
|---|---|
| 08/06/2026 | Versione iniziale: DB, UI completa, 5 schermate, export PDF |
| 08/06/2026 | Ore personalizzate per allievo (`custom_hours`) |
| 08/06/2026 | Fix dropdown corsi vuoto con messaggio guida |
| 08/06/2026 | Fix dialog (altezze corrette + grab_set ritardato su Windows) |
| 08/06/2026 | Pulsante "Elimina" per allievi archiviati |
| 08/06/2026 | Fix percorso database per exe compilato (sys.frozen) |
| 08/06/2026 | build.bat migliorato + script Inno Setup (setup.iss) |
| 08/06/2026 | Selettori data più grandi in tutta l'app (font 14, width 16) |
| 08/06/2026 | Popup avviso soglia 80% ore dopo salvataggio presenze |
| 09/06/2026 | App portatile: `build_portable.bat` genera cartella + ZIP (PyInstaller --onedir) |
| 09/06/2026 | Allievi senza corso: opzione "Senza corso (ore individuali)" nel form allievo |
| 09/06/2026 | Corso di sistema `__LIBERO__` auto-creato in `init_db()`, invisibile all'utente |
| 09/06/2026 | Protezione: corso sistema non cancellabile, PDF e dashboard mostrano "Senza corso" |
| 09/06/2026 | Suite di test automatici `test_portable.py` (14 test, 0 fallimenti) |
| 02/07/2026 | Dashboard: righe compatte (i riquadri Progresso/Azioni non forzano più l'altezza; pulsanti solo per allievi al 100%) |
| 02/07/2026 | Backup automatico ad ogni salvataggio: `ultimo_salvataggio.db` in Documenti, WAL-safe (API backup SQLite) |
| 02/07/2026 | Fix backup/ripristino: i backup includono il journal WAL; il ripristino svuota i file `-wal`/`-shm` residui |
| 02/07/2026 | Perf: versione dati globale — le schermate si ricostruiscono solo se il DB è cambiato dall'ultimo render |
| 02/07/2026 | Presenze: messaggio errore ore non valide mostra il nome allievo (non più l'id interno) |
| 02/07/2026 | Test suite estesa a 17 test (backup automatico + ripristino) — installer v1.2 |
| 02/07/2026 | Report PDF: ore rimanenti al completamento in scheda individuale, registro mensile e report per corso |

---

## Conversazione di progettazione (08/06/2026)

| Domanda | Risposta |
|---|---|
| Come gestire i ritardi? | Ore modificate manualmente dall'operatore |
| Un allievo può avere più corsi? | No, un solo corso (con ore personalizzate opzionali) |
| Serve esportare report? | Sì, PDF |
| Quanti utenti usano l'app? | Singolo utente |
| Cosa succede al 100% ore? | Notifica banner + barra rossa |
| Cosa succede all'80% ore? | Popup immediato dopo salvataggio presenze |
| Le assenze si registrano esplicitamente? | No, basta non segnare la presenza |
| Un allievo può frequentare più turni lo stesso giorno? | Sì |
| Formato export preferito? | PDF |
| I corsi hanno nomi o solo ore? | Nome personalizzato |
| Tipo di PDF? | Sia scheda individuale che registro mensile |
| Database locale o server? | SQLite locale |
