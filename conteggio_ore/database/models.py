import sqlite3
from .db import get_connection

SYSTEM_COURSE = "__LIBERO__"


# ─── CORSI ────────────────────────────────────────────────────────────────────

def get_all_courses(active_only=True):
    """Ritorna tutti i corsi escludendo il corso di sistema."""
    with get_connection() as conn:
        q = "SELECT * FROM courses WHERE name != ?"
        params = [SYSTEM_COURSE]
        if active_only:
            q += " AND active=1"
        q += " ORDER BY name"
        return conn.execute(q, params).fetchall()

def get_system_course_id():
    with get_connection() as conn:
        row = conn.execute("SELECT id FROM courses WHERE name=?", (SYSTEM_COURSE,)).fetchone()
        return row["id"] if row else None

def get_course(course_id):
    with get_connection() as conn:
        return conn.execute("SELECT * FROM courses WHERE id=?", (course_id,)).fetchone()

def add_course(name, total_hours):
    try:
        with get_connection() as conn:
            conn.execute("INSERT INTO courses (name, total_hours) VALUES (?, ?)", (name, total_hours))
            conn.commit()
    except sqlite3.IntegrityError:
        raise ValueError(f"Esiste già un corso con il nome «{name}».")

def update_course(course_id, name, total_hours):
    try:
        with get_connection() as conn:
            conn.execute("UPDATE courses SET name=?, total_hours=? WHERE id=?", (name, total_hours, course_id))
            conn.commit()
    except sqlite3.IntegrityError:
        raise ValueError(f"Esiste già un corso con il nome «{name}».")

def archive_course(course_id):
    with get_connection() as conn:
        conn.execute("UPDATE courses SET active=0 WHERE id=?", (course_id,))
        conn.commit()

def restore_course(course_id):
    with get_connection() as conn:
        conn.execute("UPDATE courses SET active=1 WHERE id=?", (course_id,))
        conn.commit()

def delete_course(course_id):
    with get_connection() as conn:
        course = conn.execute("SELECT name FROM courses WHERE id=?", (course_id,)).fetchone()
        if course and course["name"] == SYSTEM_COURSE:
            raise ValueError("Impossibile eliminare il corso di sistema.")
        students = conn.execute(
            "SELECT COUNT(*) FROM students WHERE course_id=?", (course_id,)
        ).fetchone()[0]
        if students > 0:
            raise ValueError("Impossibile eliminare: ci sono allievi (anche archiviati) iscritti a questo corso.")
        conn.execute("DELETE FROM courses WHERE id=?", (course_id,))
        conn.commit()


# ─── ALLIEVI ──────────────────────────────────────────────────────────────────

def get_all_students(active_only=True):
    with get_connection() as conn:
        q = """
            SELECT s.*, c.name AS course_name,
                   COALESCE(s.custom_hours, c.total_hours) AS total_hours,
                   c.total_hours AS course_default_hours
            FROM students s
            JOIN courses c ON s.course_id = c.id
        """
        if active_only:
            q += " WHERE s.active=1"
        q += " ORDER BY s.name"
        return conn.execute(q).fetchall()

def get_student(student_id):
    with get_connection() as conn:
        return conn.execute("""
            SELECT s.*, c.name AS course_name,
                   COALESCE(s.custom_hours, c.total_hours) AS total_hours,
                   c.total_hours AS course_default_hours
            FROM students s
            JOIN courses c ON s.course_id = c.id
            WHERE s.id=?
        """, (student_id,)).fetchone()

def add_student(name, course_id, enrollment_date, custom_hours=None):
    try:
        with get_connection() as conn:
            conn.execute(
                "INSERT INTO students (name, course_id, enrollment_date, custom_hours) VALUES (?, ?, ?, ?)",
                (name, course_id, enrollment_date, custom_hours)
            )
            conn.commit()
    except sqlite3.IntegrityError as e:
        raise ValueError(f"Impossibile aggiungere l'allievo: {e}")

def update_student(student_id, name, course_id, enrollment_date, custom_hours=None):
    try:
        with get_connection() as conn:
            conn.execute(
                "UPDATE students SET name=?, course_id=?, enrollment_date=?, custom_hours=? WHERE id=?",
                (name, course_id, enrollment_date, custom_hours, student_id)
            )
            conn.commit()
    except sqlite3.IntegrityError as e:
        raise ValueError(f"Impossibile aggiornare l'allievo: {e}")

def archive_student(student_id):
    with get_connection() as conn:
        conn.execute("UPDATE students SET active=0 WHERE id=?", (student_id,))
        conn.commit()

def restore_student(student_id):
    with get_connection() as conn:
        conn.execute("UPDATE students SET active=1 WHERE id=?", (student_id,))
        conn.commit()

def change_student_course(student_id, new_course_id):
    with get_connection() as conn:
        conn.execute("UPDATE students SET course_id=? WHERE id=?", (new_course_id, student_id))
        conn.commit()

def delete_student_permanently(student_id):
    with get_connection() as conn:
        conn.execute("DELETE FROM attendance WHERE student_id=?", (student_id,))
        conn.execute("DELETE FROM students WHERE id=?", (student_id,))
        conn.commit()


# ─── TURNI ────────────────────────────────────────────────────────────────────

def get_sessions_for_day(day_of_week):
    with get_connection() as conn:
        return conn.execute(
            "SELECT * FROM sessions WHERE day_of_week=? ORDER BY id",
            (day_of_week,)
        ).fetchall()

def get_all_sessions():
    with get_connection() as conn:
        return conn.execute("SELECT * FROM sessions ORDER BY day_of_week, id").fetchall()

def get_session(session_id):
    with get_connection() as conn:
        return conn.execute("SELECT * FROM sessions WHERE id=?", (session_id,)).fetchone()


# ─── PRESENZE ─────────────────────────────────────────────────────────────────

def get_attendance_for_date_session(date, session_id):
    with get_connection() as conn:
        return conn.execute("""
            SELECT a.*, s.name AS student_name
            FROM attendance a
            JOIN students s ON a.student_id = s.id
            WHERE a.date=? AND a.session_id=?
        """, (date, session_id)).fetchall()

def upsert_attendance(student_id, session_id, date, hours_attended, notes=""):
    with get_connection() as conn:
        conn.execute("""
            INSERT INTO attendance (student_id, session_id, date, hours_attended, notes)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(student_id, session_id, date)
            DO UPDATE SET hours_attended=excluded.hours_attended, notes=excluded.notes
        """, (student_id, session_id, date, hours_attended, notes))
        conn.commit()

def delete_attendance(student_id, session_id, date):
    with get_connection() as conn:
        conn.execute(
            "DELETE FROM attendance WHERE student_id=? AND session_id=? AND date=?",
            (student_id, session_id, date)
        )
        conn.commit()

def get_student_total_hours(student_id):
    with get_connection() as conn:
        result = conn.execute(
            "SELECT COALESCE(SUM(hours_attended), 0) FROM attendance WHERE student_id=?",
            (student_id,)
        ).fetchone()[0]
        return result

def get_attendance_totals():
    """Ore totali frequentate per ogni allievo in una sola query: {student_id: ore}."""
    with get_connection() as conn:
        rows = conn.execute(
            "SELECT student_id, SUM(hours_attended) AS total FROM attendance GROUP BY student_id"
        ).fetchall()
        return {r["student_id"]: r["total"] for r in rows}

def save_attendance_batch(saves, deletes):
    """Salva/elimina presenze in un'unica transazione.

    saves:   lista di tuple (student_id, session_id, date, hours_attended, notes)
    deletes: lista di tuple (student_id, session_id, date)
    """
    with get_connection() as conn:
        if saves:
            conn.executemany("""
                INSERT INTO attendance (student_id, session_id, date, hours_attended, notes)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(student_id, session_id, date)
                DO UPDATE SET hours_attended=excluded.hours_attended, notes=excluded.notes
            """, saves)
        if deletes:
            conn.executemany(
                "DELETE FROM attendance WHERE student_id=? AND session_id=? AND date=?",
                deletes
            )
        conn.commit()

def get_student_attendance_history(student_id):
    with get_connection() as conn:
        return conn.execute("""
            SELECT a.date, a.hours_attended, a.notes,
                   se.day_of_week, se.slot, se.default_hours
            FROM attendance a
            JOIN sessions se ON a.session_id = se.id
            WHERE a.student_id=?
            ORDER BY a.date DESC, se.day_of_week, se.slot
        """, (student_id,)).fetchall()

def get_students_summary(course_id=None):
    """Ritorna tutti gli allievi attivi con ore totali frequentate. Filtro opzionale per corso."""
    with get_connection() as conn:
        q = """
            SELECT s.id, s.name, s.enrollment_date,
                   c.name AS course_name,
                   COALESCE(s.custom_hours, c.total_hours) AS total_hours,
                   COALESCE(SUM(a.hours_attended), 0) AS hours_done
            FROM students s
            JOIN courses c ON s.course_id = c.id
            LEFT JOIN attendance a ON a.student_id = s.id
            WHERE s.active=1
        """
        params = []
        if course_id is not None:
            q += " AND s.course_id = ?"
            params.append(course_id)
        q += " GROUP BY s.id ORDER BY s.name"
        return conn.execute(q, params).fetchall()

def get_monthly_report_data(year, month):
    """Dati per il registro mensile: ore del mese + totale cumulativo per allievo."""
    month_str = f"{year}-{month:02d}"
    with get_connection() as conn:
        return conn.execute("""
            SELECT s.id, s.name, c.name AS course_name,
                   COALESCE(s.custom_hours, c.total_hours) AS total_hours,
                   COALESCE(SUM(CASE WHEN a.date LIKE ? THEN a.hours_attended ELSE 0 END), 0) AS hours_month,
                   COALESCE(SUM(a.hours_attended), 0) AS hours_total
            FROM students s
            JOIN courses c ON s.course_id = c.id
            LEFT JOIN attendance a ON a.student_id = s.id
            WHERE s.active=1
            GROUP BY s.id
            ORDER BY s.name
        """, (month_str + "%",)).fetchall()

def add_session(day_of_week, slot, default_hours):
    with get_connection() as conn:
        conn.execute(
            "INSERT INTO sessions (day_of_week, slot, default_hours) VALUES (?, ?, ?)",
            (day_of_week, slot, default_hours)
        )
        conn.commit()

def update_session(session_id, day_of_week, slot, default_hours):
    with get_connection() as conn:
        conn.execute(
            "UPDATE sessions SET day_of_week=?, slot=?, default_hours=? WHERE id=?",
            (day_of_week, slot, default_hours, session_id)
        )
        conn.commit()

def delete_session(session_id):
    with get_connection() as conn:
        linked = conn.execute(
            "SELECT COUNT(*) FROM attendance WHERE session_id=?", (session_id,)
        ).fetchone()[0]
        if linked > 0:
            raise ValueError("Impossibile eliminare: esistono presenze registrate per questo turno.")
        conn.execute("DELETE FROM sessions WHERE id=?", (session_id,))
        conn.commit()


def get_student_attendance_in_period(student_id, date_from, date_to):
    with get_connection() as conn:
        return conn.execute("""
            SELECT a.date, a.hours_attended, a.notes,
                   se.day_of_week, se.slot
            FROM attendance a
            JOIN sessions se ON a.session_id = se.id
            WHERE a.student_id=? AND a.date >= ? AND a.date <= ?
            ORDER BY a.date, se.day_of_week, se.slot
        """, (student_id, date_from, date_to)).fetchall()
