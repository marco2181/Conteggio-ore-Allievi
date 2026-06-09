import tkinter as tk
from tkinter import messagebox
import customtkinter as ctk

from database.models import get_all_sessions, add_session, update_session, delete_session
from logic.sessions import day_name, slot_label, GIORNI

MAIN_BG    = "#f5f6fa"
CARD_BG    = "#ffffff"
HEAD_COLOR = "#2c3e50"
GREY_TEXT  = "#7f8c8d"

SLOT_LABELS  = ["mattina", "pomeriggio", "sera"]
DAY_OPTIONS  = GIORNI[:6]                             # Lun–Sab
SLOT_OPTIONS = [slot_label(s) for s in SLOT_LABELS]


class SettingsFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color=MAIN_BG, corner_radius=0)
        self.app = app
        self._build()

    def _build(self):
        top = ctk.CTkFrame(self, fg_color=MAIN_BG)
        top.pack(fill="x", padx=24, pady=(20, 0))
        ctk.CTkLabel(top, text="Impostazioni — Turni",
                     font=ctk.CTkFont(size=22, weight="bold"),
                     text_color=HEAD_COLOR).pack(side="left")
        ctk.CTkButton(top, text="+ Aggiungi turno", width=140,
                      command=self._open_add_dialog).pack(side="right")

        self.scroll = ctk.CTkScrollableFrame(self, fg_color=MAIN_BG)
        self.scroll.pack(fill="both", expand=True, padx=24, pady=(14, 16))

    def on_show(self):
        self._refresh()

    def _refresh(self):
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
        self.after(100, self.grab_set)
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
