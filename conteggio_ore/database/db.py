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

    # Migrazione: aggiunge custom_hours se il DB esisteva già senza la colonna
    cols = [r[1] for r in cur.execute("PRAGMA table_info(students)").fetchall()]
    if "custom_hours" not in cols:
        cur.execute("ALTER TABLE students ADD COLUMN custom_hours REAL DEFAULT NULL")

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
