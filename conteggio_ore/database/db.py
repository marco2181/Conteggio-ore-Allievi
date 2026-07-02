import sqlite3
import os
import sys

# Quando l'app gira come exe compilato con PyInstaller, __file__ punta alla
# cartella temporanea di estrazione. Usiamo sys.executable per trovare la
# cartella dove risiede l'exe e salvare il DB lì accanto.
if getattr(sys, 'frozen', False):
    _base_dir = os.path.dirname(sys.executable)
else:
    _base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

DB_PATH = os.path.join(_base_dir, "data", "conteggio_ore.db")

# Cartella backup in Documenti: sopravvive a reinstallazioni/aggiornamenti
BACKUP_DIR = os.path.join(os.path.expanduser("~"), "Documents",
                          "ConteggioOreAllievi", "backups")

# Contatore versione dati: incrementato ad ogni scrittura sul DB.
# Le schermate lo confrontano con l'ultima versione renderizzata per
# evitare di ricostruire tabelle identiche ad ogni cambio di pagina.
_data_version = 0


def get_data_version():
    return _data_version


def mark_db_written():
    """Da chiamare dopo ogni scrittura: aggiorna versione e backup automatico."""
    global _data_version
    _data_version += 1
    try:
        backup_database("ultimo_salvataggio.db")
    except Exception:
        pass  # il backup non deve mai bloccare un salvataggio


def backup_database(filename):
    """Copia il database in BACKUP_DIR usando l'API di backup di SQLite.

    A differenza di una copia del file, include anche le transazioni
    ancora nel journal WAL ed è sicura mentre l'app è in uso.
    Ritorna il percorso del backup, o None se il DB non esiste ancora.
    """
    if not os.path.exists(DB_PATH):
        return None
    os.makedirs(BACKUP_DIR, exist_ok=True)
    dest = os.path.join(BACKUP_DIR, filename)
    src = sqlite3.connect(DB_PATH)
    try:
        dst = sqlite3.connect(dest)
        try:
            src.backup(dst)
        finally:
            dst.close()
    finally:
        src.close()
    return dest


def export_database(dest_path):
    """Esporta il DB in un percorso scelto dall'utente (WAL-safe)."""
    src = sqlite3.connect(DB_PATH)
    try:
        dst = sqlite3.connect(dest_path)
        try:
            src.backup(dst)
        finally:
            dst.close()
    finally:
        src.close()


def restore_database(src_path):
    """Sostituisce il DB con il file di backup indicato.

    Prima svuota il journal WAL e rimuove i file -wal/-shm residui:
    se restassero, SQLite li riapplicherebbe sopra il DB ripristinato
    sovrascrivendo i dati del backup.
    """
    import gc
    import shutil
    # Su Windows eventuali connessioni residue (in attesa di garbage
    # collection) tengono un handle sul file -wal e bloccano la rimozione
    gc.collect()
    if os.path.exists(DB_PATH):
        conn = sqlite3.connect(DB_PATH)
        try:
            conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        finally:
            conn.close()
    for suffix in ("-wal", "-shm"):
        leftover = DB_PATH + suffix
        try:
            os.remove(leftover)
        except OSError:
            pass  # già rimosso da SQLite o inesistente; il checkpoint lo ha comunque svuotato
    shutil.copy2(src_path, DB_PATH)
    # Nessun backup automatico qui: non va sovrascritto ultimo_salvataggio.db
    # con il file appena ripristinato (potrebbe servire per tornare indietro).
    global _data_version
    _data_version += 1

SESSIONS_DEFAULT = [
    (0, "mattina",    3.0),   # Lunedì mattina
    (1, "pomeriggio", 3.0),   # Martedì pomeriggio
    (2, "pomeriggio", 3.0),   # Mercoledì pomeriggio
    (2, "sera",       2.5),   # Mercoledì sera
    (3, "sera",       2.5),   # Giovedì sera
    (4, "mattina",    3.0),   # Venerdì mattina
    (5, "mattina",    3.0),   # Sabato mattina
    (5, "pomeriggio", 3.0),   # Sabato pomeriggio
]

def get_connection():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    # WAL: commit molto più veloci (importante su USB/dischi lenti)
    conn.execute("PRAGMA journal_mode = WAL")
    conn.execute("PRAGMA synchronous = NORMAL")
    return conn

def init_db():
    conn = get_connection()
    cur = conn.cursor()

    cur.executescript("""
        CREATE TABLE IF NOT EXISTS courses (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            total_hours REAL NOT NULL,
            created_at TEXT DEFAULT (datetime('now', 'localtime'))
        );

        CREATE TABLE IF NOT EXISTS students (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            course_id INTEGER NOT NULL REFERENCES courses(id),
            enrollment_date TEXT NOT NULL,
            active INTEGER DEFAULT 1,
            custom_hours REAL DEFAULT NULL
        );

        CREATE TABLE IF NOT EXISTS sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            day_of_week INTEGER NOT NULL,
            slot TEXT NOT NULL,
            default_hours REAL NOT NULL
        );

        CREATE TABLE IF NOT EXISTS attendance (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            student_id INTEGER NOT NULL REFERENCES students(id),
            session_id INTEGER NOT NULL REFERENCES sessions(id),
            date TEXT NOT NULL,
            hours_attended REAL NOT NULL,
            notes TEXT,
            UNIQUE(student_id, session_id, date)
        );
    """)

    cur.executescript("""
        CREATE INDEX IF NOT EXISTS idx_attendance_student ON attendance(student_id);
        CREATE INDEX IF NOT EXISTS idx_attendance_date    ON attendance(date);
        CREATE INDEX IF NOT EXISTS idx_students_active    ON students(active);
        CREATE INDEX IF NOT EXISTS idx_students_course    ON students(course_id);
    """)

    # Migrazioni per colonne aggiunte dopo la creazione iniziale
    student_cols = [r[1] for r in cur.execute("PRAGMA table_info(students)").fetchall()]
    if "custom_hours" not in student_cols:
        cur.execute("ALTER TABLE students ADD COLUMN custom_hours REAL DEFAULT NULL")

    course_cols = [r[1] for r in cur.execute("PRAGMA table_info(courses)").fetchall()]
    if "active" not in course_cols:
        cur.execute("ALTER TABLE courses ADD COLUMN active INTEGER DEFAULT 1")

    # Popola i turni fissi solo se la tabella è vuota
    existing = cur.execute("SELECT COUNT(*) FROM sessions").fetchone()[0]
    if existing == 0:
        cur.executemany(
            "INSERT INTO sessions (day_of_week, slot, default_hours) VALUES (?, ?, ?)",
            SESSIONS_DEFAULT
        )

    # Corso di sistema per allievi senza corso formale
    system = cur.execute("SELECT id FROM courses WHERE name='__LIBERO__'").fetchone()
    if not system:
        cur.execute("INSERT INTO courses (name, total_hours) VALUES ('__LIBERO__', 0)")

    conn.commit()
    conn.close()
