import sys
import os

# Aggiunge la directory del progetto al path per gli import relativi
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from database.db import init_db
from ui.app import App


def main():
    init_db()
    app = App()
    app.mainloop()


if __name__ == "__main__":
    main()
