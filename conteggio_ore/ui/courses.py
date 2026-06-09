import tkinter as tk
from tkinter import messagebox
import customtkinter as ctk

from database.models import get_all_courses, add_course, update_course, delete_course

MAIN_BG    = "#f5f6fa"
CARD_BG    = "#ffffff"
HEAD_COLOR = "#2c3e50"
GREY_TEXT  = "#7f8c8d"

PRESET_HOURS = [20, 90, 150, 300]


class CoursesFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color=MAIN_BG, corner_radius=0)
        self.app = app
        self._build()

    def _build(self):
        top = ctk.CTkFrame(self, fg_color=MAIN_BG)
        top.pack(fill="x", padx=24, pady=(20, 0))
        ctk.CTkLabel(top, text="Gestione Corsi",
                     font=ctk.CTkFont(size=22, weight="bold"),
                     text_color=HEAD_COLOR).pack(side="left")
        ctk.CTkButton(top, text="+ Nuovo corso", width=130,
                      command=self._open_add_dialog).pack(side="right")

        self.scroll = ctk.CTkScrollableFrame(self, fg_color=MAIN_BG)
        self.scroll.pack(fill="both", expand=True, padx=24, pady=(14, 16))

    def on_show(self):
        self._refresh()

    def _refresh(self):
        for w in self.scroll.winfo_children():
            w.destroy()

        courses = get_all_courses()

        # Header
        hdr = ctk.CTkFrame(self.scroll, fg_color=HEAD_COLOR, corner_radius=6)
        hdr.pack(fill="x", pady=(0, 2))
        for col, width in [("Nome corso", 300), ("Monte ore", 120), ("Azioni", 200)]:
            ctk.CTkLabel(hdr, text=col, width=width,
                         font=ctk.CTkFont(size=11, weight="bold"),
                         text_color="white", anchor="w").pack(side="left", padx=8, pady=7)

        if not courses:
            ctk.CTkLabel(self.scroll, text="Nessun corso configurato.",
                         text_color=GREY_TEXT).pack(pady=30)
            return

        for i, c in enumerate(courses):
            bg = CARD_BG if i % 2 == 0 else "#f0f3f4"
            row = ctk.CTkFrame(self.scroll, fg_color=bg, corner_radius=4)
            row.pack(fill="x", pady=1)

            ctk.CTkLabel(row, text=c["name"], width=300, anchor="w",
                         font=ctk.CTkFont(size=12)).pack(side="left", padx=8, pady=8)
            ctk.CTkLabel(row, text=f"{c['total_hours']:.0f} h", width=120, anchor="center",
                         font=ctk.CTkFont(size=12), text_color="#2980b9").pack(side="left", padx=8)

            actions = ctk.CTkFrame(row, fg_color="transparent")
            actions.pack(side="left", padx=8)
            ctk.CTkButton(actions, text="Modifica", width=80, height=28,
                          font=ctk.CTkFont(size=11),
                          command=lambda c_=c: self._open_edit_dialog(c_)).pack(side="left", padx=2)
            ctk.CTkButton(actions, text="Elimina", width=80, height=28,
                          font=ctk.CTkFont(size=11),
                          fg_color="#e74c3c", hover_color="#c0392b",
                          command=lambda c_=c: self._delete(c_)).pack(side="left", padx=2)

    def _open_add_dialog(self):
        CourseDialog(self, None, self._refresh)

    def _open_edit_dialog(self, course):
        CourseDialog(self, course, self._refresh)

    def _delete(self, course):
        if not messagebox.askyesno("Elimina", f"Eliminare il corso '{course['name']}'?"):
            return
        try:
            delete_course(course["id"])
            self._refresh()
        except ValueError as e:
            messagebox.showerror("Impossibile eliminare", str(e))


class CourseDialog(ctk.CTkToplevel):
    def __init__(self, parent, course, on_save):
        super().__init__(parent)
        self.course = course
        self.on_save = on_save
        self.title("Nuovo corso" if course is None else "Modifica corso")
        self.geometry("400x340")
        self.after(100, self.grab_set)
        self._build()

    def _build(self):
        pad = {"padx": 24, "pady": 8}

        ctk.CTkLabel(self, text="Nome corso:", anchor="w").pack(fill="x", **pad)
        self.name_var = tk.StringVar(value=self.course["name"] if self.course else "")
        ctk.CTkEntry(self, textvariable=self.name_var, width=310).pack(**pad)

        ctk.CTkLabel(self, text="Monte ore:", anchor="w").pack(fill="x", **pad)

        hours_frame = ctk.CTkFrame(self, fg_color="transparent")
        hours_frame.pack(**pad)

        current = str(int(self.course["total_hours"])) if self.course else "90"
        self.hours_var = tk.StringVar(value=current)

        # Bottoni preset
        for h in PRESET_HOURS:
            ctk.CTkButton(hours_frame, text=f"{h}h", width=55, height=30,
                          font=ctk.CTkFont(size=11),
                          command=lambda v=h: self.hours_var.set(str(v))).pack(side="left", padx=2)

        ctk.CTkLabel(self, text="Oppure inserisci manualmente:", anchor="w",
                     font=ctk.CTkFont(size=10), text_color="#7f8c8d").pack(fill="x", padx=24)
        ctk.CTkEntry(self, textvariable=self.hours_var, width=100).pack(padx=24, pady=4)

        btn_frame = ctk.CTkFrame(self, fg_color="transparent")
        btn_frame.pack(pady=12)
        ctk.CTkButton(btn_frame, text="Salva", width=100, command=self._save).pack(side="left", padx=6)
        ctk.CTkButton(btn_frame, text="Annulla", width=100,
                      fg_color="#7f8c8d", hover_color="#636e72",
                      command=self.destroy).pack(side="left", padx=6)

    def _save(self):
        name = self.name_var.get().strip()
        if not name:
            messagebox.showerror("Errore", "Inserire il nome del corso.", parent=self)
            return
        try:
            hours = float(self.hours_var.get().replace(",", "."))
            if hours <= 0:
                raise ValueError
        except ValueError:
            messagebox.showerror("Errore", "Monte ore non valido.", parent=self)
            return

        try:
            if self.course is None:
                add_course(name, hours)
            else:
                update_course(self.course["id"], name, hours)
            self.on_save()
            self.destroy()
        except Exception as e:
            messagebox.showerror("Errore", str(e), parent=self)
