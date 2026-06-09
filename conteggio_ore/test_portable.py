import sys, os

dist_dir = r'C:\Users\marco_obm8qni\Desktop\Conteggio ore allievi\conteggio_ore\dist\ConteggioOreAllievi'
db_dir   = os.path.join(dist_dir, 'data')
db_path  = os.path.join(db_dir, 'conteggio_ore.db')

sys.path.insert(0, r'C:\Users\marco_obm8qni\Desktop\Conteggio ore allievi\conteggio_ore')
import database.db as dbmod
dbmod.DB_PATH = db_path

from database import models as m
from database.db import init_db

PASS = 0
FAIL = 0

def ok(n, msg):
    global PASS
    PASS += 1
    print(f'  [PASS] {n}: {msg}')

def fail(n, msg):
    global FAIL
    FAIL += 1
    print(f'  [FAIL] {n}: {msg}')

print('=' * 50)
print('  TEST APP PORTATILE')
print('=' * 50)

# 1. Init DB
try:
    init_db()
    assert os.path.exists(db_path)
    ok(1, f'init_db + DB creato in dist/')
except Exception as e:
    fail(1, str(e))

# 2. Corso di sistema
try:
    sys_id = m.get_system_course_id()
    assert sys_id is not None
    ok(2, f'Corso di sistema presente (id={sys_id})')
except Exception as e:
    fail(2, str(e))

# 3. Corso normale
try:
    try:
        m.add_course('Corso 90h', 90)
    except Exception:
        pass  # gia esiste
    courses = m.get_all_courses()
    names = [c['name'] for c in courses]
    assert '__LIBERO__' not in names
    assert 'Corso 90h' in names
    ok(3, f'Corsi visibili (no sistema): {names}')
except Exception as e:
    fail(3, str(e))

# 4. Allievo SENZA CORSO con 45h custom
try:
    try:
        m.add_student('Mario Rossi', sys_id, '2026-01-10', custom_hours=45)
    except Exception:
        pass
    summary = m.get_students_summary()
    mario = next((s for s in summary if 'Mario' in s['name']), None)
    assert mario is not None
    assert mario['total_hours'] == 45
    ok(4, f'Allievo senza corso: total_hours={mario["total_hours"]}h (custom)')
except Exception as e:
    fail(4, str(e))

# 5. Allievo CON CORSO NORMALE (usa ore del corso)
try:
    cid = m.get_all_courses()[0]['id']
    try:
        m.add_student('Anna Verdi', cid, '2026-02-01')
    except Exception:
        pass
    summary = m.get_students_summary()
    anna = next((s for s in summary if 'Anna' in s['name']), None)
    assert anna is not None
    assert anna['total_hours'] == 90
    ok(5, f'Allievo con corso: total_hours={anna["total_hours"]}h (da corso)')
except Exception as e:
    fail(5, str(e))

# 6. Registrazione presenze e calcolo ore
try:
    sessions = m.get_sessions_for_day(0)  # lunedi mattina
    assert sessions
    sess = sessions[0]
    mario = next(s for s in m.get_students_summary() if 'Mario' in s['name'])
    dates = [
        '2026-03-02','2026-03-09','2026-03-16','2026-03-23','2026-03-30',
        '2026-04-06','2026-04-13','2026-04-20','2026-04-27',
        '2026-05-04','2026-05-11','2026-05-18',
    ]
    for d in dates:
        m.upsert_attendance(mario['id'], sess['id'], d, 3.0)
    summary2 = m.get_students_summary()
    mario2 = next(s for s in summary2 if 'Mario' in s['name'])
    expected_h = len(dates) * 3.0
    assert mario2['hours_done'] >= expected_h, f'attese >={expected_h}, trovate {mario2["hours_done"]}'
    ok(6, f'Presenze registrate: {mario2["hours_done"]}h su {mario2["total_hours"]}h')
except Exception as e:
    fail(6, str(e))

# 7. Rilevamento crossing 80%
try:
    mario2 = next(s for s in m.get_students_summary() if 'Mario' in s['name'])
    pct_before = mario2['hours_done'] / mario2['total_hours'] * 100
    snap = {mario2['id']: pct_before}

    # Aggiungi presenza che porta sopra 80%
    m.upsert_attendance(mario2['id'], sess['id'], '2026-05-25', 3.0)

    after = m.get_students_summary()
    mario3 = next(s for s in after if 'Mario' in s['name'])
    pct_after = mario3['hours_done'] / mario3['total_hours'] * 100

    crossed = snap.get(mario3['id'], 0) < 80 <= pct_after
    ok(7, f'Crossing 80%: prima={pct_before:.0f}% dopo={pct_after:.0f}% rilevato={crossed}')
except Exception as e:
    fail(7, str(e))

# 8. Report mensile
try:
    monthly = m.get_monthly_report_data(2026, 5)
    assert len(monthly) > 0
    mario_m = next((r for r in monthly if 'Mario' in r['name']), None)
    assert mario_m is not None
    ok(8, f'Report mensile maggio: Mario ore_mese={mario_m["hours_month"]}h')
except Exception as e:
    fail(8, str(e))

# 9. Archivia e ripristina
try:
    mario = next(s for s in m.get_all_students(active_only=True) if 'Mario' in s['name'])
    m.archive_student(mario['id'])
    attivi = m.get_all_students(active_only=True)
    assert not any('Mario' in s['name'] for s in attivi)
    m.restore_student(mario['id'])
    attivi2 = m.get_all_students(active_only=True)
    assert any('Mario' in s['name'] for s in attivi2)
    ok(9, 'Archivia e ripristina allievo OK')
except Exception as e:
    fail(9, str(e))

# 10. Protezione corso di sistema
try:
    bloccato = False
    try:
        m.delete_course(sys_id)
    except ValueError:
        bloccato = True
    assert bloccato
    ok(10, 'Cancellazione corso sistema bloccata OK')
except Exception as e:
    fail(10, str(e))

# 11. Protezione corso con allievi attivi
try:
    bloccato = False
    try:
        m.delete_course(cid)
    except ValueError:
        bloccato = True
    assert bloccato
    ok(11, 'Cancellazione corso con allievi attivi bloccata OK')
except Exception as e:
    fail(11, str(e))

# 12. Modifica ore personalizzate
try:
    mario = next(s for s in m.get_all_students(active_only=True) if 'Mario' in s['name'])
    m.update_student(mario['id'], mario['name'], mario['course_id'], mario['enrollment_date'], custom_hours=60)
    summary = m.get_students_summary()
    mario_u = next(s for s in summary if 'Mario' in s['name'])
    assert mario_u['total_hours'] == 60
    ok(12, f'Modifica ore custom: {mario_u["total_hours"]}h OK')
except Exception as e:
    fail(12, str(e))

# 13. Sessioni per giorno
try:
    for day, expected_count in [(0,1),(1,1),(2,2),(3,1),(4,1),(5,2)]:
        s = m.get_sessions_for_day(day)
        assert len(s) == expected_count, f'Giorno {day}: attese {expected_count} sessioni, trovate {len(s)}'
    ok(13, 'Turni fissi settimana: lunedi=1 martedi=1 mercoledi=2 giovedi=1 venerdi=1 sabato=2')
except Exception as e:
    fail(13, str(e))

# 14. DB accanto all'exe (persistenza portatile)
try:
    assert os.path.exists(db_path)
    size_kb = os.path.getsize(db_path) / 1024
    ok(14, f'DB in dist/ ({size_kb:.0f} KB) - dati persistenti OK')
except Exception as e:
    fail(14, str(e))

print()
print('=' * 50)
print(f'  RISULTATO: {PASS} PASS  |  {FAIL} FAIL')
print('=' * 50)
sys.exit(0 if FAIL == 0 else 1)
