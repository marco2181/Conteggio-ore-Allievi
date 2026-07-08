import tkinter as tk
from tkinter import messagebox
from datetime import date
import customtkinter as ctk
from tkcalendar import DateEntry

from database.models import (
    get_all_students, get_sessions_for_day,
    get_attendance_for_date_session,
    save_attendance_batch,
    get_students_summary
)
from database.db import get_data_version
from logic.sessions import day_name, slot_label
from ui.style import font

MAIN_BG    = "#f5f6fa"
CARD_BG    = "#ffffff"
HEAD_COLOR = "#2c3e50"
GREY_TEXT  = "#7f8c8d"


class AttendanceFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color=MAIN_BG, corner_radius=0)
        self.app = app
        self._selected_date = date.today()
        self._entries = {}   # (student_id, session_id) -> {"check": BooleanVar, "hours": StringVar}
        self._search_job = None   # id dell'after in attesa (debounce ricerca)
        self._rendered_key = None  # (versione dati, data, ricerca) dell'ultimo render
        self._build()

    def _build(self):
        # Titolo + selettore data
        top = ctk.CTkFrame(self, fg_color=MAIN_BG)
        top.pack(fill="x", padx=24, pady=(20, 0))
        ctk.CTkLabel(top, text="Registro Presenze",
                     font=ctk.CTkFont(size=22, weight="bold"),
                     text_color=HEAD_COLOR).pack(side="left")

        date_frame = ctk.CTkFrame(top, fg_color=MAIN_BG)
        date_frame.pack(side="right")

        # Barra di ricerca allievo
        search_bar = ctk.CTkFrame(self, fg_color=MAIN_BG)
        search_bar.pack(fill="x", padx=24, pady=(8, 0))
        ctk.CTkLabel(search_bar, text="Cerca allievo:",
                     text_color=HEAD_COLOR,
                     font=font(12)).pack(side="left", padx=(0, 8))
        self._search_var = tk.StringVar()
        ctk.CTkEntry(search_bar, textvariable=self._search_var,
                     width=240, placeholder_text="Nome...").pack(side="left")
        ctk.CTkButton(search_bar, text="✕", width=28, height=28,
                      fg_color="#bdc3c7", hover_color="#95a5a6", text_color="#2c3e50",
                      command=lambda: self._search_var.set("")).pack(side="left", padx=(4, 0))
        self._search_var.trace_add("write", lambda *_: self._on_search_changed())
        ctk.CTkLabel(date_frame, text="Data:", text_color=HEAD_COLOR,
                     font=ctk.CTkFont(size=13)).pack(side="left", padx=(0, 6))
        self.date_picker = DateEntry(
            date_frame, width=16, date_pattern="dd/mm/yyyy",
            year=self._selected_date.year,
            month=self._selected_date.month,
            day=self._selected_date.day,
            font=("Helvetica", 14),
            background="#2c3e50", foreground="white",
            selectbackground="#2980b9"
        )
        self.date_picker.pack(side="left", ipady=5)
        ctk.CTkButton(date_frame, text="Carica", width=90, height=36,
                      font=ctk.CTkFont(size=13),
                      command=self._load_day).pack(side="left", padx=(10, 0))

        # Area turni
        self.scroll = ctk.CTkScrollableFrame(self, fg_color=MAIN_BG)
        self.scroll.pack(fill="both", expand=True, padx=24, pady=(14, 0))

        # Pulsante Salva
        bottom = ctk.CTkFrame(self, fg_color=MAIN_BG)
        bottom.pack(fill="x", padx=24, pady=12)
        ctk.CTkButton(bottom, text="💾  Salva presenze", height=40,
                      font=ctk.CTkFont(size=13, weight="bold"),
                      command=self._save).pack(side="right")

    def on_show(self):
        # Ricostruisce solo se DB, data o ricerca sono cambiati dall'ultimo render
        try:
            picked = self.date_picker.get_date()
        except Exception:
            picked = self._selected_date
        key = (get_data_version(), picked, self._search_var.get())
        if key != self._rendered_key:
            self._load_day()

    def _on_search_changed(self):
        # Debounce: annulla la ricarica in attesa, riparte il timer.
        # Senza questo ogni tasto digitato ricostruiva l'intera pagina.
        if self._search_job is not None:
            self.after_cancel(self._search_job)
        self._search_job = self.after(300, self._load_day)

    def _load_day(self):
        self._search_job = None
        self._rendered_key = None  # impostata a fine render riuscito
        try:
            self._selected_date = self.date_picker.get_date()
        except Exception:
            pass

        for w in self.scroll.winfo_children():
            w.destroy()
        self._entries.clear()

        day_idx = self._selected_date.weekday()  # 0=Mon..5=Sat
        sessions = get_sessions_for_day(day_idx)
        students = get_all_students(active_only=True)
        self._student_names = {s["id"]: s["name"] for s in students}
        query = self._search_var.get().strip().lower()
        if query:
            students = [s for s in students if query in s["name"].lower()]
        date_str = self._selected_date.strftime("%Y-%m-%d")

        if not sessions:
            ctk.CTkLabel(self.scroll,
                         text=f"Nessun turno previsto per {day_name(day_idx)}.",
                         text_color=GREY_TEXT,
                         font=ctk.CTkFont(size=13)).pack(pady=40)
            return

        for sess in sessions:
            self._build_session_block(sess, students, date_str)
        self._rendered_key = (get_data_version(), self._selected_date, self._search_var.get())

    def _build_session_block(self, sess, students, date_str):
        sid = sess["id"]
        label = f"{day_name(sess['day_of_week'])} — {slot_label(sess['slot'])}  ({sess['default_hours']:.1f} h)"

        block = ctk.CTkFrame(self.scroll, fg_color=CARD_BG, corner_radius=8)
        block.pack(fill="x", pady=(0, 14))

        # Header del turno
        hdr = ctk.CTkFrame(block, fg_color=HEAD_COLOR, corner_radius=0)
        hdr.pack(fill="x")
        ctk.CTkLabel(hdr, text=label, font=ctk.CTkFont(size=13, weight="bold"),
                     text_color="white").pack(side="left", padx=14, pady=8)

        # Seleziona/deseleziona tutti
        all_var = tk.BooleanVar(value=False)

        def toggle_all(var=all_var, session_id=sid):
            val = var.get()
            for (stu_id, s_id), data in self._entries.items():
                if s_id == session_id:
                    data["check"].set(val)

        ctk.CTkCheckBox(hdr, text="Tutti", variable=all_var,
                        command=toggle_all,
                        text_color="white",
                        fg_color="#2980b9", hover_color="#1a6fa8",
                        checkmark_color="white").pack(side="right", padx=14)

        # Righe allievi
        existing = {r["student_id"]: r for r in get_attendance_for_date_session(date_str, sid)}

        if not students:
            ctk.CTkLabel(block, text="Nessun allievo registrato.",
                         text_color=GREY_TEXT).pack(pady=10)
            return

        for i, stu in enumerate(students):
            stu_id = stu["id"]
            present = stu_id in existing
            hours_val = str(existing[stu_id]["hours_attended"]) if present else str(sess["default_hours"])

            notes_val = (existing[stu_id]["notes"] or "") if present else ""
            check_var = tk.BooleanVar(value=present)
            hours_var = tk.StringVar(value=hours_val)
            notes_var = tk.StringVar(value=notes_val)

            self._entries[(stu_id, sid)] = {"check": check_var, "hours": hours_var, "notes": notes_var}

            row_bg = "#f8f9fa" if i % 2 == 0 else CARD_BG
            row = ctk.CTkFrame(block, fg_color=row_bg, corner_radius=0)
            row.pack(fill="x", padx=2)

            ctk.CTkCheckBox(row, text=stu["name"], variable=check_var,
                            font=font(12),
                            fg_color="#2980b9", hover_color="#1a6fa8",
                            checkmark_color="white").pack(side="left", padx=14, pady=7)

            ctk.CTkLabel(row, text="Ore:", text_color=GREY_TEXT,
                         font=font(11)).pack(side="right", padx=(0, 6))
            ctk.CTkEntry(row, textvariable=hours_var, width=60,
                         font=font(12), justify="center").pack(side="right", padx=(0, 14), pady=7)
            ctk.CTkEntry(row, textvariable=notes_var, width=150,
                         font=font(11),
                         placeholder_text="Note...").pack(side="right", padx=(0, 4), pady=7)
            ctk.CTkLabel(row, text="Note:", text_color=GREY_TEXT,
                         font=font(11)).pack(side="right", padx=(0, 4))

    def _save(self):
        date_str = self._selected_date.strftime("%Y-%m-%d")
        errors = []

        # Snapshot percentuali PRIMA del salvataggio
        snapshot_before = {
            r["id"]: (r["hours_done"] / r["total_hours"] * 100) if r["total_hours"] > 0 else 0
            for r in get_students_summary()
        }

        saves, deletes = [], []
        for (stu_id, sess_id), data in self._entries.items():
            if data["check"].get():
                try:
                    hours = float(data["hours"].get().replace(",", "."))
                    if hours <= 0:
                        raise ValueError
                    notes = data["notes"].get().strip()
                    saves.append((stu_id, sess_id, date_str, hours, notes))
                except ValueError:
                    name = self._student_names.get(stu_id, f"id={stu_id}")
                    errors.append(f"Ore non valide per «{name}»: presenza non salvata.")
            else:
                deletes.append((stu_id, sess_id, date_str))

        # Unica transazione: molto più veloce di un commit per allievo
        save_attendance_batch(saves, deletes)
        saved = len(saves)

        if errors:
            messagebox.showwarning("Attenzione", "\n".join(errors))
        else:
            messagebox.showinfo("Salvato", f"Presenze salvate: {saved} registrazione/i.")

        # La Dashboard si aggiornerà da sola alla prossima apertura:
        # il suo on_show confronta la versione dati.
        self._check_threshold(snapshot_before)

    def _check_threshold(self, snapshot_before):
        """Mostra popup se un allievo ha appena superato l'80% delle ore."""
        after = get_students_summary()
        crossed = [
            r for r in after
            if r["total_hours"] > 0
            and snapshot_before.get(r["id"], 0) < 80
            and 80 <= (r["hours_done"] / r["total_hours"] * 100) < 100
        ]
        if not crossed:
            return

        popup = ctk.CTkToplevel(self)
        popup.title("Avviso soglia ore")
        popup.geometry("420x220")
        popup.attributes("-topmost", True)
        popup.after(100, popup.grab_set)

        header = ctk.CTkFrame(popup, fg_color="#e67e22", corner_radius=0)
        header.pack(fill="x")
        ctk.CTkLabel(header, text="⚠️  Soglia 80% raggiunta",
                     font=ctk.CTkFont(size=15, weight="bold"),
                     text_color="white").pack(pady=12)

        body = ctk.CTkScrollableFrame(popup, fg_color="#fef9f0")
        body.pack(fill="both", expand=True, padx=16, pady=8)

        for r in crossed:
            pct = r["hours_done"] / r["total_hours"] * 100
            ctk.CTkLabel(body,
                         text=f"• {r['name']}  —  {r['hours_done']:.1f} / {r['total_hours']:.1f} h  ({pct:.0f}%)",
                         font=ctk.CTkFont(size=13),
                         text_color="#7f4800",
                         anchor="w").pack(fill="x", pady=2)

        ctk.CTkButton(popup, text="OK", width=100, height=34,
                      fg_color="#e67e22", hover_color="#ca6f1e",
                      command=popup.destroy).pack(pady=(0, 12))
