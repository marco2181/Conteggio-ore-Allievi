import sys
import os
import shutil
import logging
import logging.handlers
from datetime import date

# Aggiunge la directory del progetto al path per gli import relativi
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from database.db import init_db, DB_PATH
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
    backup_dir = os.path.join(_DATA_DIR, "backups")
    os.makedirs(backup_dir, exist_ok=True)
    today = date.today().strftime("%Y%m%d")
    dest = os.path.join(backup_dir, f"conteggio_ore_{today}.db")
    if not os.path.exists(dest):
        shutil.copy2(DB_PATH, dest)
        logging.info(f"Backup DB creato: {dest}")
    # Mantieni solo gli ultimi 7 backup
    backups = sorted(
        [f for f in os.listdir(backup_dir) if f.endswith(".db")],
        reverse=True,
    )
    for old in backups[7:]:
        os.remove(os.path.join(backup_dir, old))
        logging.info(f"Backup vecchio rimosso: {old}")


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
