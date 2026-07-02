import sys
import os
import logging
import logging.handlers
from datetime import date

# Aggiunge la directory del progetto al path per gli import relativi
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from database.db import init_db, DB_PATH, BACKUP_DIR, backup_database
from ui.app import App

_DATA_DIR = os.path.dirname(DB_PATH)


def _setup_logging():
    log_path = os.path.join(_DATA_DIR, "app.log")
    os.makedirs(_DATA_DIR, exist_ok=True)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        handlers=[
            logging.handlers.RotatingFileHandler(log_path, maxBytes=1_000_000, backupCount=2, encoding="utf-8"),
            logging.StreamHandler(),
        ],
    )


def _backup_db():
    if not os.path.exists(DB_PATH):
        return
    # Salta se il DB è vuoto (nessun allievo — installazione fresca)
    try:
        import sqlite3 as _sqlite3
        with _sqlite3.connect(DB_PATH) as _c:
            if _c.execute("SELECT COUNT(*) FROM students").fetchone()[0] == 0:
                return
    except Exception:
        return
    # Salva in Documenti: sopravvive a qualsiasi reinstallazione/aggiornamento.
    # backup_database usa l'API di SQLite: include anche il journal WAL
    # (una semplice copia del file .db perderebbe le ultime scritture).
    today = date.today().strftime("%Y%m%d")
    dest = os.path.join(BACKUP_DIR, f"auto_{today}.db")
    if not os.path.exists(dest):
        try:
            backup_database(f"auto_{today}.db")
            logging.info(f"Backup automatico creato: {dest}")
        except Exception as e:
            logging.warning(f"Backup automatico fallito: {e}")
            return
    # Mantieni solo gli ultimi 7 backup giornalieri
    # (ultimo_salvataggio.db non rientra nella rotazione)
    try:
        backups = sorted(
            [f for f in os.listdir(BACKUP_DIR) if f.startswith("auto_") and f.endswith(".db")],
            reverse=True,
        )
        for old in backups[7:]:
            os.remove(os.path.join(BACKUP_DIR, old))
            logging.info(f"Backup vecchio rimosso: {old}")
    except Exception:
        pass


def main():
    _setup_logging()
    logging.info("Avvio applicazione")
    _backup_db()
    init_db()
    app = App()
    app.mainloop()
    logging.info("Chiusura applicazione")


if __name__ == "__main__":
    main()
