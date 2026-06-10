import tkinter as tk
from tkinter import messagebox, filedialog
from datetime import date
import os
import customtkinter as ctk
from tkcalendar import DateEntry

from database.models import (
    get_all_students, get_student, get_student_total_hours,
    get_student_attendance_in_period, get_monthly_report_data,
    get_all_courses, get_students_summary
)
from logic.sessions import MESI

MAIN_BG    = "#f5f6fa"
CARD_BG    = "#ffffff"
HEAD_COLOR = "#2c3e50"
GREY_TEXT  = "#7f8c8d"


class ReportsFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color=MAIN_BG, corner_radius=0)
        self.app = app
        self._build()

    def _build(self):
        ctk.CTkLabel(self, text="Report PDF",
                     font=ctk.CTkFont(size=22, weight="bold"),
                     text_color=HEAD_COLOR).pack(anchor="w", padx=24, pady=(20, 16))

        inner = ctk.CTkFrame(self, fg_color=MAIN_BG)
        inner.pack(fill="both", expand=True, padx=24)

        # Scheda individuale
        ind_card = self._section_card(inner, "📋  Scheda individuale")
        self._build_individual(ind_card)

        # Registro mensile
        mon_card = self._section_card(inner, "📅  Registro mensile")
        self._build_monthly(mon_card)

        # Report per corso
        course_card = self._section_card(inner, "📊  Report per corso")
        self._build_course(course_card)

    def _section_card(self, parent, title):
        card = ctk.CTkFrame(parent, fg_color=CARD_BG, corner_radius=10)
        card.pack(fill="x", pady=(0, 16))
        ctk.CTkLabel(card, text=title,
                     font=ctk.CTkFont(size=14, weight="bold"),
                     text_color=HEAD_COLOR).pack(anchor="w", padx=16, pady=(14, 8))
        sep = ctk.CTkFrame(card, height=1, fg_color="#dfe6e9")
        sep.pack(fill="x", padx=16, pady=(0, 12))
        return card

    def _build_individual(self, card):
        row = ctk.CTkFrame(card, fg_color=CARD_BG)
        row.pack(fill="x", padx=16, pady=(0, 14))

        ctk.CTkLabel(row, text="Allievo:", text_color=HEAD_COLOR, width=80, anchor="w").pack(side="left")
        self.student_var = tk.StringVar()
        self.student_menu = ctk.CTkOptionMenu(row, variable=self.student_var,
                                              values=["—"], width=220)
        self.student_menu.pack(side="left", padx=(0, 16))

        today = date.today()
        ctk.CTkLabel(row, text="Dal:", text_color=HEAD_COLOR,
                     font=ctk.CTkFont(size=13)).pack(side="left", padx=(0, 4))
        self.date_from = DateEntry(row, width=16, date_pattern="dd/mm/yyyy",
                                   year=today.year, month=today.month, day=1,
                                   font=("Helvetica", 14),
                                   background="#2c3e50", foreground="white",
                                   selectbackground="#2980b9")
        self.date_from.pack(side="left", padx=(0, 12), ipady=5)

        ctk.CTkLabel(row, text="Al:", text_color=HEAD_COLOR,
                     font=ctk.CTkFont(size=13)).pack(side="left", padx=(0, 4))
        self.date_to = DateEntry(row, width=16, date_pattern="dd/mm/yyyy",
                                 year=today.year, month=today.month, day=today.day,
                                 font=("Helvetica", 14),
                                 background="#2c3e50", foreground="white",
                                 selectbackground="#2980b9")
        self.date_to.pack(side="left", padx=(0, 16), ipady=5)

        ctk.CTkButton(row, text="📄  Genera PDF", width=130,
                      command=self._generate_individual).pack(side="left")

    def _build_monthly(self, card):
        row = ctk.CTkFrame(card, fg_color=CARD_BG)
        row.pack(fill="x", padx=16, pady=(0, 14))

        today = date.today()
        ctk.CTkLabel(row, text="Mese:", text_color=HEAD_COLOR, width=60, anchor="w").pack(side="left")
        self.month_var = tk.StringVar(value=MESI[today.month - 1])
        ctk.CTkOptionMenu(row, variable=self.month_var,
                          values=MESI, width=140).pack(side="left", padx=(0, 10))

        ctk.CTkLabel(row, text="Anno:", text_color=HEAD_COLOR).pack(side="left", padx=(0, 4))
        self.year_var = tk.StringVar(value=str(today.year))
        ctk.CTkEntry(row, textvariable=self.year_var, width=70).pack(side="left", padx=(0, 16))

        ctk.CTkButton(row, text="📄  Genera PDF", width=130,
                      command=self._generate_monthly).pack(side="left")

    def _build_course(self, card):
        row = ctk.CTkFrame(card, fg_color=CARD_BG)
        row.pack(fill="x", padx=16, pady=(0, 14))
        ctk.CTkLabel(row, text="Corso:", text_color=HEAD_COLOR, width=80, anchor="w").pack(side="left")
        self.course_report_var = tk.StringVar(value="—")
        self.course_report_menu = ctk.CTkOptionMenu(row, variable=self.course_report_var,
                                                    values=["—"], width=220)
        self.course_report_menu.pack(side="left", padx=(0, 16))
        ctk.CTkButton(row, text="📄  Genera PDF", width=130,
                      command=self._generate_course).pack(side="left")

    def on_show(self):
        students = get_all_students(active_only=True)
        self._student_map = {s["name"]: s["id"] for s in students}
        names = list(self._student_map.keys())
        self.student_menu.configure(values=names if names else ["—"])
        if names:
            self.student_var.set(names[0])
        else:
            self.student_var.set("—")

        courses = get_all_courses()
        self._course_map = {c["name"]: c["id"] for c in courses}
        cnames = list(self._course_map.keys())
        self.course_report_menu.configure(values=cnames if cnames else ["—"])
        if cnames:
            self.course_report_var.set(cnames[0])
        else:
            self.course_report_var.set("—")

    def _generate_individual(self):
        name = self.student_var.get()
        if name == "—" or name not in self._student_map:
            messagebox.showwarning("Attenzione", "Selezionare un allievo.")
            return

        student_id = self._student_map[name]
        stu = get_student(student_id)
        date_from = self.date_from.get_date().strftime("%Y-%m-%d")
        date_to = self.date_to.get_date().strftime("%Y-%m-%d")

        if date_from > date_to:
            messagebox.showwarning("Attenzione", "La data 'dal' deve essere precedente a 'al'.")
            return

        rows = get_student_attendance_in_period(student_id, date_from, date_to)

        hours_done = get_student_total_hours(student_id)
        student_info = {
            "name": stu["name"],
            "course_name": stu["course_name"],
            "total_hours": stu["total_hours"],
            "hours_done": hours_done,
        }

        default_name = f"Scheda_{name.replace(' ', '_')}_{date_from}_{date_to}.pdf"
        output_path = filedialog.asksaveasfilename(
            defaultextension=".pdf",
            filetypes=[("PDF", "*.pdf")],
            initialfile=default_name,
            title="Salva scheda individuale"
        )
        if not output_path:
            return

        try:
            from logic.pdf_generator import generate_individual_report
            generate_individual_report(output_path, student_info, rows, date_from, date_to)
            if messagebox.askyesno("PDF generato", f"PDF salvato in:\n{output_path}\n\nAprire il file?"):
                os.startfile(output_path)
        except Exception as e:
            messagebox.showerror("Errore", f"Errore nella generazione del PDF:\n{e}")

    def _generate_course(self):
        name = self.course_report_var.get()
        if name == "—" or name not in self._course_map:
            messagebox.showwarning("Attenzione", "Selezionare un corso.")
            return
        course_id = self._course_map[name]
        rows = get_students_summary(course_id=course_id)
        if not rows:
            messagebox.showinfo("Nessun dato", "Nessun allievo attivo per questo corso.")
            return
        default_name = f"Report_{name.replace(' ', '_')}.pdf"
        output_path = filedialog.asksaveasfilename(
            defaultextension=".pdf",
            filetypes=[("PDF", "*.pdf")],
            initialfile=default_name,
            title="Salva report per corso"
        )
        if not output_path:
            return
        try:
            from logic.pdf_generator import generate_course_report
            generate_course_report(output_path, name, rows)
            if messagebox.askyesno("PDF generato", f"PDF salvato in:\n{output_path}\n\nAprire il file?"):
                os.startfile(output_path)
        except Exception as e:
            messagebox.showerror("Errore", f"Errore nella generazione del PDF:\n{e}")

    def _generate_monthly(self):
        try:
            year = int(self.year_var.get())
            if not (2000 <= year <= 2100):
                raise ValueError
        except ValueError:
            messagebox.showwarning("Attenzione", "Anno non valido. Inserire un anno tra 2000 e 2100.")
            return

        month = MESI.index(self.month_var.get()) + 1
        rows = get_monthly_report_data(year, month)

        if not rows:
            messagebox.showinfo("Nessun dato", "Nessun allievo attivo trovato.")
            return

        default_name = f"Registro_{MESI[month-1]}_{year}.pdf"
        output_path = filedialog.asksaveasfilename(
            defaultextension=".pdf",
            filetypes=[("PDF", "*.pdf")],
            initialfile=default_name,
            title="Salva registro mensile"
        )
        if not output_path:
            return

        try:
            from logic.pdf_generator import generate_monthly_report
            generate_monthly_report(output_path, rows, year, month)
            if messagebox.askyesno("PDF generato", f"PDF salvato in:\n{output_path}\n\nAprire il file?"):
                os.startfile(output_path)
        except Exception as e:
            messagebox.showerror("Errore", f"Errore nella generazione del PDF:\n{e}")
