import tkinter as tk
from tkinter import messagebox
import customtkinter as ctk

from database.models import get_all_courses, add_course, update_course, delete_course, archive_course, restore_course
from database.db import get_data_version

MAIN_BG    = "#f5f6fa"
CARD_BG    = "#ffffff"
HEAD_COLOR = "#2c3e50"
GREY_TEXT  = "#7f8c8d"

PRESET_HOURS = [20, 90, 150, 300]


def _popup_focus(win):
    win.attributes("-topmost", True)
    win.lift()
    win.focus_force()
    win.grab_set()
    win.after(200, lambda: win.attributes("-topmost", False))


class CoursesFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color=MAIN_BG, corner_radius=0)
        self.app = app
        self._show_archived = False
        self._rendered_version = None
        self._build()

    def _build(self):
        top = ctk.CTkFrame(self, fg_color=MAIN_BG)
        top.pack(fill="x", padx=24, pady=(20, 0))
        ctk.CTkLabel(top, text="Gestione Corsi",
                     font=ctk.CTkFont(size=22, weight="bold"),
                     text_color=HEAD_COLOR).pack(side="left")
        ctk.CTkButton(top, text="+ Nuovo corso", width=130,
                      command=self._open_add_dialog).pack(side="right")
        self.archive_toggle = ctk.CTkButton(
            top, text="Mostra archiviati", width=150,
            fg_color="#7f8c8d", hover_color="#636e72",
            command=self._toggle_archived
        )
        self.archive_toggle.pack(side="right", padx=(0, 8))

        self.scroll = ctk.CTkScrollableFrame(self, fg_color=MAIN_BG)
        self.scroll.pack(fill="both", expand=True, padx=24, pady=(14, 16))

    def on_show(self):
        # Ricostruisce la tabella solo se il DB è cambiato dall'ultimo render
        if get_data_version() != self._rendered_version:
            self._refresh()

    def _toggle_archived(self):
        self._show_archived = not self._show_archived
        self.archive_toggle.configure(
            text="Nascondi archiviati" if self._show_archived else "Mostra archiviati"
        )
        self._refresh()

    def _refresh(self):
        self._rendered_version = get_data_version()
        for w in self.scroll.winfo_children():
            w.destroy()

        courses = get_all_courses(active_only=not self._show_archived)

        # Header
        hdr = ctk.CTkFrame(self.scroll, fg_color=HEAD_COLOR, corner_radius=6)
        hdr.pack(fill="x", pady=(0, 2))
        for col, width in [("Nome corso", 280), ("Monte ore", 110), ("Stato", 90), ("Azioni", 230)]:
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

            is_active = c["active"]
            ctk.CTkLabel(row, text=c["name"], width=280, anchor="w",
                         font=ctk.CTkFont(size=12),
                         text_color=GREY_TEXT if not is_active else "black").pack(side="left", padx=8, pady=8)
            ctk.CTkLabel(row, text=f"{c['total_hours']:.0f} h", width=110, anchor="center",
                         font=ctk.CTkFont(size=12), text_color="#2980b9").pack(side="left", padx=8)
            stato_text = "Attivo" if is_active else "Archiviato"
            stato_color = "#27ae60" if is_active else "#e67e22"
            ctk.CTkLabel(row, text=stato_text, width=90, anchor="center",
                         font=ctk.CTkFont(size=11, weight="bold"),
                         text_color=stato_color).pack(side="left", padx=8)

            actions = ctk.CTkFrame(row, fg_color="transparent")
            actions.pack(side="left", padx=8)
            if is_active:
                ctk.CTkButton(actions, text="Modifica", width=80, height=28,
                              font=ctk.CTkFont(size=11),
                              command=lambda c_=c: self._open_edit_dialog(c_)).pack(side="left", padx=2)
                ctk.CTkButton(actions, text="Archivia", width=80, height=28,
                              font=ctk.CTkFont(size=11),
                              fg_color="#e67e22", hover_color="#ca6f1e",
                              command=lambda c_=c: self._archive(c_)).pack(side="left", padx=2)
            else:
                ctk.CTkButton(actions, text="Ripristina", width=90, height=28,
                              font=ctk.CTkFont(size=11),
                              fg_color="#27ae60", hover_color="#1e8449",
                              command=lambda c_=c: self._restore(c_)).pack(side="left", padx=2)
                ctk.CTkButton(actions, text="Elimina", width=80, height=28,
                              font=ctk.CTkFont(size=11),
                              fg_color="#e74c3c", hover_color="#c0392b",
                              command=lambda c_=c: self._delete(c_)).pack(side="left", padx=2)

    def _open_add_dialog(self):
        CourseDialog(self, None, self._refresh)

    def _open_edit_dialog(self, course):
        CourseDialog(self, course, self._refresh)

    def _archive(self, course):
        if messagebox.askyesno("Archivia corso",
                               f"Archiviare il corso '{course['name']}'?\n\n"
                               "Sarà nascosto dalla lista attivi ma i dati degli allievi saranno conservati."):
            archive_course(course["id"])
            self._refresh()

    def _restore(self, course):
        restore_course(course["id"])
        self._refresh()

    def _delete(self, course):
        if not messagebox.askyesno("Elimina definitivamente", f"Eliminare il corso '{course['name']}'?"):
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
        self.after(100, lambda: _popup_focus(self))
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
