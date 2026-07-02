import tkinter as tk
from tkinter import messagebox, filedialog
from datetime import date as _date
import customtkinter as ctk

from database.models import get_all_sessions, add_session, update_session, delete_session
from database.db import export_database, restore_database, get_data_version
from logic.sessions import day_name, slot_label, GIORNI

MAIN_BG    = "#f5f6fa"
CARD_BG    = "#ffffff"
HEAD_COLOR = "#2c3e50"
GREY_TEXT  = "#7f8c8d"

SLOT_LABELS  = ["mattina", "pomeriggio", "sera"]


def _popup_focus(win):
    win.attributes("-topmost", True)
    win.lift()
    win.focus_force()
    win.grab_set()
    win.after(200, lambda: win.attributes("-topmost", False))
DAY_OPTIONS  = GIORNI[:6]                             # Lun–Sab
SLOT_OPTIONS = [slot_label(s) for s in SLOT_LABELS]


class SettingsFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color=MAIN_BG, corner_radius=0)
        self.app = app
        self._rendered_version = None
        self._build()

    def _build(self):
        top = ctk.CTkFrame(self, fg_color=MAIN_BG)
        top.pack(fill="x", padx=24, pady=(20, 0))
        ctk.CTkLabel(top, text="Impostazioni — Turni",
                     font=ctk.CTkFont(size=22, weight="bold"),
                     text_color=HEAD_COLOR).pack(side="left")
        ctk.CTkButton(top, text="+ Aggiungi turno", width=140,
                      command=self._open_add_dialog).pack(side="right")

        # Backup / Restore database
        backup_bar = ctk.CTkFrame(self, fg_color=MAIN_BG)
        backup_bar.pack(fill="x", padx=24, pady=(10, 0))
        ctk.CTkLabel(backup_bar, text="Database:",
                     text_color=HEAD_COLOR,
                     font=ctk.CTkFont(size=12)).pack(side="left", padx=(0, 10))
        ctk.CTkButton(backup_bar, text="📦 Esporta backup", width=150,
                      fg_color="#27ae60", hover_color="#1e8449",
                      font=ctk.CTkFont(size=12),
                      command=self._backup_db).pack(side="left", padx=(0, 8))
        ctk.CTkButton(backup_bar, text="📥 Importa backup", width=150,
                      fg_color="#e67e22", hover_color="#ca6f1e",
                      font=ctk.CTkFont(size=12),
                      command=self._restore_db).pack(side="left")
        ctk.CTkLabel(backup_bar,
                     text="Backup automatico ad ogni salvataggio in Documenti\\ConteggioOreAllievi\\backups",
                     text_color=GREY_TEXT,
                     font=ctk.CTkFont(size=11)).pack(side="left", padx=(12, 0))

        self.scroll = ctk.CTkScrollableFrame(self, fg_color=MAIN_BG)
        self.scroll.pack(fill="both", expand=True, padx=24, pady=(14, 16))

    def on_show(self):
        # Ricostruisce la tabella solo se il DB è cambiato dall'ultimo render
        if get_data_version() != self._rendered_version:
            self._refresh()

    def _refresh(self):
        self._rendered_version = get_data_version()
        for w in self.scroll.winfo_children():
            w.destroy()

        sessions = get_all_sessions()

        hdr = ctk.CTkFrame(self.scroll, fg_color=HEAD_COLOR, corner_radius=6)
        hdr.pack(fill="x", pady=(0, 2))
        for col, width in [("Giorno", 160), ("Fascia", 130), ("Ore default", 110), ("Azioni", 180)]:
            ctk.CTkLabel(hdr, text=col, width=width,
                         font=ctk.CTkFont(size=11, weight="bold"),
                         text_color="white", anchor="w").pack(side="left", padx=8, pady=7)

        if not sessions:
            ctk.CTkLabel(self.scroll, text="Nessun turno configurato.",
                         text_color=GREY_TEXT).pack(pady=30)
            return

        for i, s in enumerate(sessions):
            bg = CARD_BG if i % 2 == 0 else "#f0f3f4"
            row = ctk.CTkFrame(self.scroll, fg_color=bg, corner_radius=4)
            row.pack(fill="x", pady=1)

            ctk.CTkLabel(row, text=day_name(s["day_of_week"]), width=160, anchor="w",
                         font=ctk.CTkFont(size=12)).pack(side="left", padx=8, pady=8)
            ctk.CTkLabel(row, text=slot_label(s["slot"]), width=130, anchor="w",
                         font=ctk.CTkFont(size=12)).pack(side="left", padx=8)
            ctk.CTkLabel(row, text=f"{s['default_hours']:.1f} h", width=110, anchor="center",
                         font=ctk.CTkFont(size=12), text_color="#2980b9").pack(side="left", padx=8)

            actions = ctk.CTkFrame(row, fg_color="transparent")
            actions.pack(side="left", padx=8)
            ctk.CTkButton(actions, text="Modifica", width=80, height=28,
                          font=ctk.CTkFont(size=11),
                          command=lambda s_=s: self._open_edit_dialog(s_)).pack(side="left", padx=2)
            ctk.CTkButton(actions, text="Elimina", width=80, height=28,
                          font=ctk.CTkFont(size=11),
                          fg_color="#e74c3c", hover_color="#c0392b",
                          command=lambda s_=s: self._delete(s_)).pack(side="left", padx=2)

    def _backup_db(self):
        dest = filedialog.asksaveasfilename(
            defaultextension=".db",
            filetypes=[("Database SQLite", "*.db"), ("Tutti i file", "*.*")],
            initialfile=f"backup_{_date.today().strftime('%Y%m%d')}.db",
            title="Salva backup database"
        )
        if dest:
            try:
                export_database(dest)
                messagebox.showinfo("Backup completato",
                                    f"Database salvato in:\n{dest}\n\n"
                                    "Conserva questo file per ripristinare tutti i dati "
                                    "(allievi, presenze, corsi, turni).")
            except Exception as e:
                messagebox.showerror("Errore backup", str(e))

    def _restore_db(self):
        src = filedialog.askopenfilename(
            filetypes=[("Database SQLite", "*.db"), ("Tutti i file", "*.*")],
            title="Seleziona file di backup da ripristinare"
        )
        if not src:
            return
        if messagebox.askyesno(
            "Conferma ripristino",
            "Ripristinare il database dal backup selezionato?\n\n"
            "⚠️ I dati attuali verranno SOSTITUITI con quelli del backup.\n"
            "L'app andrà riavviata dopo il ripristino."
        ):
            try:
                restore_database(src)
                messagebox.showinfo("Ripristino completato",
                                    "Database ripristinato correttamente.\n\n"
                                    "Chiudi e riapri l'applicazione per applicare le modifiche.")
            except Exception as e:
                messagebox.showerror("Errore ripristino", str(e))

    def _open_add_dialog(self):
        SessionDialog(self, None, self._refresh)

    def _open_edit_dialog(self, session):
        SessionDialog(self, session, self._refresh)

    def _delete(self, session):
        label = f"{day_name(session['day_of_week'])} — {slot_label(session['slot'])}"
        if not messagebox.askyesno("Elimina turno", f"Eliminare il turno:\n{label}?"):
            return
        try:
            delete_session(session["id"])
            self._refresh()
        except ValueError as e:
            messagebox.showerror("Impossibile eliminare", str(e))


class SessionDialog(ctk.CTkToplevel):
    def __init__(self, parent, session, on_save):
        super().__init__(parent)
        self.session = session
        self.on_save = on_save
        self.title("Nuovo turno" if session is None else "Modifica turno")
        self.geometry("380x280")
        self.resizable(False, False)
        self.after(100, lambda: _popup_focus(self))
        self._build()

    def _build(self):
        pad = {"padx": 28, "pady": 8}

        ctk.CTkLabel(self, text="Giorno:", anchor="w").pack(fill="x", **pad)
        day_idx = self.session["day_of_week"] if self.session else 0
        self.day_var = tk.StringVar(value=day_name(day_idx))
        ctk.CTkOptionMenu(self, variable=self.day_var, values=DAY_OPTIONS, width=300).pack(**pad)

        ctk.CTkLabel(self, text="Fascia oraria:", anchor="w").pack(fill="x", **pad)
        slot_val = self.session["slot"] if self.session else SLOT_LABELS[0]
        self.slot_var = tk.StringVar(value=slot_label(slot_val))
        ctk.CTkOptionMenu(self, variable=self.slot_var, values=SLOT_OPTIONS, width=300).pack(**pad)

        ctk.CTkLabel(self, text="Ore default:", anchor="w").pack(fill="x", **pad)
        hours_val = str(self.session["default_hours"]) if self.session else "3.0"
        self.hours_var = tk.StringVar(value=hours_val)
        ctk.CTkEntry(self, textvariable=self.hours_var, width=120, justify="center").pack(**pad)

        btn_frame = ctk.CTkFrame(self, fg_color="transparent")
        btn_frame.pack(pady=12)
        ctk.CTkButton(btn_frame, text="Salva", width=100, command=self._save).pack(side="left", padx=6)
        ctk.CTkButton(btn_frame, text="Annulla", width=100,
                      fg_color="#7f8c8d", hover_color="#636e72",
                      command=self.destroy).pack(side="left", padx=6)

    def _save(self):
        try:
            hours = float(self.hours_var.get().replace(",", "."))
            if hours <= 0:
                raise ValueError
        except ValueError:
            messagebox.showerror("Errore", "Ore non valide (numero > 0).", parent=self)
            return

        day_idx = DAY_OPTIONS.index(self.day_var.get())
        slot_key = SLOT_LABELS[SLOT_OPTIONS.index(self.slot_var.get())]

        try:
            if self.session is None:
                add_session(day_idx, slot_key, hours)
            else:
                update_session(self.session["id"], day_idx, slot_key, hours)
            self.on_save()
            self.destroy()
        except Exception as e:
            messagebox.showerror("Errore", str(e), parent=self)
