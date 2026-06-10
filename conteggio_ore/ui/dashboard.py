import os
import tkinter as tk
from tkinter import filedialog, messagebox
import customtkinter as ctk
from database.models import (
    get_students_summary, get_all_courses,
    change_student_course, delete_student_permanently, SYSTEM_COURSE
)
from logic.sessions import progress_color
from ui.style import font


def _popup_focus(win):
    win.attributes("-topmost", True)
    win.lift()
    win.focus_force()
    win.grab_set()
    win.after(200, lambda: win.attributes("-topmost", False))

MAIN_BG    = "#f5f6fa"
CARD_BG    = "#ffffff"
HEAD_COLOR = "#2c3e50"
GREY_TEXT  = "#7f8c8d"


class DashboardFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color=MAIN_BG, corner_radius=0)
        self.app = app
        self._course_filter_map = {}  # label → course_id (None per "Tutti")
        self._build()

    def _build(self):
        header = ctk.CTkFrame(self, fg_color=MAIN_BG)
        header.pack(fill="x", padx=24, pady=(20, 0))
        ctk.CTkLabel(header, text="Dashboard", font=ctk.CTkFont(size=22, weight="bold"),
                     text_color=HEAD_COLOR).pack(side="left")
        ctk.CTkButton(header, text="↻  Aggiorna", width=110,
                      command=self.on_show).pack(side="right")

        ctk.CTkLabel(header, text="Corso:", text_color=HEAD_COLOR,
                     font=font(12)).pack(side="right", padx=(0, 6))
        self._filter_var = tk.StringVar(value="Tutti i corsi")
        self._filter_menu = ctk.CTkOptionMenu(
            header, variable=self._filter_var, values=["Tutti i corsi"],
            width=180, command=lambda _: self._refresh_table()
        )
        self._filter_menu.pack(side="right", padx=(0, 10))

        self.stats_frame = ctk.CTkFrame(self, fg_color=MAIN_BG)
        self.stats_frame.pack(fill="x", padx=24, pady=(12, 0))

        self.table_frame = ctk.CTkScrollableFrame(self, fg_color=MAIN_BG, label_text="")
        self.table_frame.pack(fill="both", expand=True, padx=24, pady=(12, 16))

    def on_show(self):
        self._reload_course_filter()
        self._refresh_stats()
        self._refresh_table()

    def _reload_course_filter(self):
        courses = get_all_courses()
        self._course_filter_map = {"Tutti i corsi": None}
        for c in courses:
            self._course_filter_map[c["name"]] = c["id"]
        options = list(self._course_filter_map.keys())
        current = self._filter_var.get()
        self._filter_menu.configure(values=options)
        if current not in self._course_filter_map:
            self._filter_var.set("Tutti i corsi")

    def _refresh_stats(self):
        for w in self.stats_frame.winfo_children():
            w.destroy()

        rows = get_students_summary()
        total_students = len(rows)
        completed = sum(1 for r in rows if r["total_hours"] > 0 and r["hours_done"] >= r["total_hours"])
        near = sum(1 for r in rows if r["total_hours"] > 0 and 80 <= (r["hours_done"] / r["total_hours"] * 100) < 100)

        for title, value, color in [
            ("Allievi attivi", str(total_students), "#2980b9"),
            ("Corsi completati", str(completed), "#27ae60"),
            ("Quasi al termine (≥80%)", str(near), "#e67e22"),
        ]:
            card = ctk.CTkFrame(self.stats_frame, fg_color=CARD_BG, corner_radius=10)
            card.pack(side="left", padx=(0, 12), pady=4, ipadx=14, ipady=10)
            ctk.CTkLabel(card, text=value, font=font(28, "bold"),
                         text_color=color).pack()
            ctk.CTkLabel(card, text=title, font=font(11),
                         text_color=GREY_TEXT).pack()

    def _refresh_table(self):
        for w in self.table_frame.winfo_children():
            w.destroy()

        selected_label = self._filter_var.get()
        course_id = self._course_filter_map.get(selected_label)
        rows = get_students_summary(course_id=course_id)

        completed_names = [r["name"] for r in rows
                           if r["total_hours"] > 0 and r["hours_done"] >= r["total_hours"]]
        if completed_names:
            notif = ctk.CTkFrame(self.table_frame, fg_color="#fdecea", corner_radius=8)
            notif.pack(fill="x", pady=(0, 10))
            ctk.CTkLabel(notif,
                         text="🎓  Corso completato: " + ", ".join(completed_names),
                         text_color="#c0392b",
                         font=font(12, "bold")).pack(padx=12, pady=8)

        # Header — colonna Azioni aggiunta
        cols   = ["Nome allievo", "Corso", "Ore fatte", "Ore totali", "Progresso", "Azioni"]
        widths = [180, 160, 80, 80, 260, 200]
        hdr = ctk.CTkFrame(self.table_frame, fg_color=HEAD_COLOR, corner_radius=6)
        hdr.pack(fill="x", pady=(0, 2))
        for col, w in zip(cols, widths):
            ctk.CTkLabel(hdr, text=col, width=w,
                         font=font(11, "bold"),
                         text_color="white", anchor="w").pack(side="left", padx=8, pady=7)

        if not rows:
            ctk.CTkLabel(self.table_frame, text="Nessun allievo registrato.",
                         text_color=GREY_TEXT).pack(pady=30)
            return

        for i, r in enumerate(rows):
            total = r["total_hours"]
            done  = r["hours_done"]
            pct   = min((done / total * 100) if total > 0 else 0, 100)
            completed = pct >= 100
            bar_color = progress_color(pct)

            bg = CARD_BG if i % 2 == 0 else "#f0f3f4"
            row_frame = ctk.CTkFrame(self.table_frame, fg_color=bg, corner_radius=4)
            row_frame.pack(fill="x", pady=1)

            course_disp = "Senza corso" if r["course_name"] == SYSTEM_COURSE else r["course_name"]
            ctk.CTkLabel(row_frame, text=r["name"], width=180, anchor="w",
                         font=font(12)).pack(side="left", padx=8, pady=3)
            ctk.CTkLabel(row_frame, text=course_disp, width=160, anchor="w",
                         font=font(11), text_color=GREY_TEXT).pack(side="left", padx=8)
            ctk.CTkLabel(row_frame, text=f"{done:.1f} h", width=80, anchor="center",
                         font=font(11)).pack(side="left", padx=8)
            ctk.CTkLabel(row_frame, text=f"{total:.1f} h", width=80, anchor="center",
                         font=font(11), text_color=GREY_TEXT).pack(side="left", padx=8)

            # Barra progresso
            prog = ctk.CTkFrame(row_frame, fg_color="transparent", width=260)
            prog.pack(side="left", padx=8)
            prog.pack_propagate(False)
            bar = ctk.CTkProgressBar(prog, width=180, height=14,
                                     progress_color=bar_color, fg_color="#dfe6e9")
            bar.set(pct / 100)
            bar.pack(side="left", pady=3)
            ctk.CTkLabel(prog, text=f"{pct:.0f}%", width=44,
                         font=font(11, "bold"),
                         text_color=bar_color).pack(side="left", padx=4)

            # Colonna Azioni: pulsanti solo per allievi al 100%
            actions = ctk.CTkFrame(row_frame, fg_color="transparent", width=200)
            actions.pack(side="left", padx=8)
            actions.pack_propagate(False)

            if completed:
                sid = r["id"]
                sname = r["name"]
                scourse = r["course_name"]
                senroll = r["enrollment_date"]
                sdone = done
                stotal = total
                ctk.CTkButton(
                    actions, text="📜 Attestato", width=96, height=28,
                    fg_color="#8e44ad", hover_color="#6c3483",
                    font=font(11),
                    command=lambda sid=sid, sname=sname, scourse=scourse, senroll=senroll, sdone=sdone, stotal=stotal:
                        self._generate_certificate(sname, scourse, senroll, sdone, stotal)
                ).pack(side="left", padx=(0, 4))
                ctk.CTkButton(
                    actions, text="Nuovo corso", width=88, height=28,
                    fg_color="#2980b9", hover_color="#1a6fa5",
                    font=font(11),
                    command=lambda sid=sid, sname=sname: self._open_change_course(sid, sname)
                ).pack(side="left", padx=(0, 4))
                ctk.CTkButton(
                    actions, text="Elimina", width=66, height=28,
                    fg_color="#e74c3c", hover_color="#c0392b",
                    font=font(11),
                    command=lambda sid=sid, sname=sname: self._confirm_delete(sid, sname)
                ).pack(side="left")

    # ── Attestato PDF ─────────────────────────────────────────────────────────

    def _generate_certificate(self, name, course_name, enrollment_date, hours_done, total_hours):
        safe_name = name.replace(" ", "_")
        default_file = f"Attestato_{safe_name}.pdf"
        output_path = filedialog.asksaveasfilename(
            defaultextension=".pdf",
            filetypes=[("PDF", "*.pdf")],
            initialfile=default_file,
            title="Salva attestato di frequenza"
        )
        if not output_path:
            return
        try:
            from logic.pdf_generator import generate_certificate
            generate_certificate(output_path, name, course_name, enrollment_date, hours_done, total_hours)
            if messagebox.askyesno("Attestato generato", f"PDF salvato in:\n{output_path}\n\nAprire il file?"):
                os.startfile(output_path)
        except Exception as e:
            messagebox.showerror("Errore", f"Errore nella generazione dell'attestato:\n{e}")

    # ── Dialog: cambio corso ───────────────────────────────────────────────────

    def _open_change_course(self, student_id, student_name):
        courses = get_all_courses()
        if not courses:
            self._show_error("Nessun corso disponibile. Aggiungine uno dalla sezione Corsi.")
            return

        dialog = ctk.CTkToplevel(self)
        dialog.title("Assegna nuovo corso")
        dialog.geometry("380x240")
        dialog.resizable(False, False)
        dialog.after(100, lambda d=dialog: _popup_focus(d))

        ctk.CTkLabel(dialog, text=f"Allievo: {student_name}",
                     font=ctk.CTkFont(size=13, weight="bold"),
                     text_color=HEAD_COLOR).pack(pady=(20, 4))
        ctk.CTkLabel(dialog, text="Seleziona il nuovo corso:",
                     font=font(11), text_color=GREY_TEXT).pack()

        course_map = {f"{c['name']} ({c['total_hours']:.0f}h)": c["id"] for c in courses}
        options = list(course_map.keys())

        selected = ctk.StringVar(value=options[0])
        combo = ctk.CTkOptionMenu(dialog, values=options, variable=selected, width=320)
        combo.pack(pady=14)

        def confirm():
            new_id = course_map[selected.get()]
            change_student_course(student_id, new_id)
            dialog.destroy()
            self.on_show()

        btns = ctk.CTkFrame(dialog, fg_color="transparent")
        btns.pack()
        ctk.CTkButton(btns, text="Conferma", width=120, command=confirm).pack(side="left", padx=8)
        ctk.CTkButton(btns, text="Annulla", width=100, fg_color="grey",
                      command=dialog.destroy).pack(side="left")

    # ── Dialog: conferma eliminazione ─────────────────────────────────────────

    def _confirm_delete(self, student_id, student_name):
        dialog = ctk.CTkToplevel(self)
        dialog.title("Elimina allievo")
        dialog.geometry("400x200")
        dialog.resizable(False, False)
        dialog.after(100, lambda d=dialog: _popup_focus(d))

        ctk.CTkLabel(dialog, text="⚠️  Eliminazione definitiva",
                     font=ctk.CTkFont(size=14, weight="bold"),
                     text_color="#c0392b").pack(pady=(20, 6))
        ctk.CTkLabel(dialog,
                     text=f"Sei sicuro di voler eliminare '{student_name}'?\nTutte le presenze registrate andranno perse.",
                     font=font(12), text_color=GREY_TEXT,
                     justify="center").pack(pady=4)

        btns = ctk.CTkFrame(dialog, fg_color="transparent")
        btns.pack(pady=18)

        def confirm():
            delete_student_permanently(student_id)
            dialog.destroy()
            self.on_show()

        ctk.CTkButton(btns, text="Elimina definitivamente", width=180,
                      fg_color="#e74c3c", hover_color="#c0392b",
                      command=confirm).pack(side="left", padx=8)
        ctk.CTkButton(btns, text="Annulla", width=100, fg_color="grey",
                      command=dialog.destroy).pack(side="left")

    def _show_error(self, msg):
        dialog = ctk.CTkToplevel(self)
        dialog.title("Attenzione")
        dialog.geometry("340x130")
        dialog.resizable(False, False)
        dialog.after(100, lambda d=dialog: _popup_focus(d))
        ctk.CTkLabel(dialog, text=msg, wraplength=300,
                     font=font(12)).pack(pady=30)
        ctk.CTkButton(dialog, text="OK", width=80, command=dialog.destroy).pack()
