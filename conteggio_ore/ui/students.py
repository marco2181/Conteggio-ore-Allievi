import csv
import os
import tkinter as tk
from tkinter import messagebox, filedialog
from datetime import date
import customtkinter as ctk
from tkcalendar import DateEntry

from database.models import (
    get_all_students, get_student, add_student, update_student,
    archive_student, restore_student,
    get_all_courses, get_student_attendance_history, get_student_total_hours,
    SYSTEM_COURSE, get_system_course_id
)
from logic.pdf_generator import generate_certificate

SENZA_CORSO_LABEL = "— Senza corso (ore individuali) —"
from logic.sessions import day_name, slot_label, progress_color


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


class StudentsFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color=MAIN_BG, corner_radius=0)
        self.app = app
        self._show_archived = False
        self._build()

    def _build(self):
        # Titolo + pulsanti
        top = ctk.CTkFrame(self, fg_color=MAIN_BG)
        top.pack(fill="x", padx=24, pady=(20, 0))
        ctk.CTkLabel(top, text="Gestione Allievi",
                     font=ctk.CTkFont(size=22, weight="bold"),
                     text_color=HEAD_COLOR).pack(side="left")

        ctk.CTkButton(top, text="+ Nuovo allievo", width=140,
                      command=self._open_add_dialog).pack(side="right", padx=(8, 0))
        ctk.CTkButton(top, text="↓ Esporta CSV", width=130,
                      fg_color="#27ae60", hover_color="#1e8449",
                      command=self._export_csv).pack(side="right", padx=(8, 0))
        ctk.CTkButton(top, text="↑ Importa CSV", width=130,
                      fg_color="#8e44ad", hover_color="#6c3483",
                      command=self._import_csv).pack(side="right", padx=(8, 0))
        self.archive_toggle = ctk.CTkButton(
            top, text="Mostra archiviati", width=150,
            fg_color="#7f8c8d", hover_color="#636e72",
            command=self._toggle_archived
        )
        self.archive_toggle.pack(side="right")

        # Ricerca
        search_frame = ctk.CTkFrame(self, fg_color=MAIN_BG)
        search_frame.pack(fill="x", padx=24, pady=(10, 0))
        ctk.CTkLabel(search_frame, text="Cerca:", text_color=HEAD_COLOR).pack(side="left", padx=(0, 6))
        self.search_var = tk.StringVar()
        self.search_var.trace_add("write", lambda *_: self._refresh_table())
        ctk.CTkEntry(search_frame, textvariable=self.search_var, width=250,
                     placeholder_text="Nome allievo...").pack(side="left")

        # Tabella
        self.scroll = ctk.CTkScrollableFrame(self, fg_color=MAIN_BG)
        self.scroll.pack(fill="both", expand=True, padx=24, pady=(10, 16))

    def on_show(self):
        self._refresh_table()

    def _toggle_archived(self):
        self._show_archived = not self._show_archived
        self.archive_toggle.configure(
            text="Nascondi archiviati" if self._show_archived else "Mostra archiviati"
        )
        self._refresh_table()

    def _refresh_table(self):
        for w in self.scroll.winfo_children():
            w.destroy()

        search = self.search_var.get().lower()
        students = get_all_students(active_only=not self._show_archived)
        students = [s for s in students if search in s["name"].lower()]

        # Header
        cols = ["Nome allievo", "Corso", "Ore fatte / Totali", "% Completamento", "Azioni"]
        widths = [200, 180, 160, 130, 180]
        hdr = ctk.CTkFrame(self.scroll, fg_color=HEAD_COLOR, corner_radius=6)
        hdr.pack(fill="x", pady=(0, 2))
        for col, w in zip(cols, widths):
            ctk.CTkLabel(hdr, text=col, width=w,
                         font=ctk.CTkFont(size=11, weight="bold"),
                         text_color="white", anchor="w").pack(side="left", padx=8, pady=7)

        if not students:
            ctk.CTkLabel(self.scroll, text="Nessun allievo trovato.",
                         text_color=GREY_TEXT).pack(pady=30)
            return

        for i, stu in enumerate(students):
            total = stu["total_hours"]
            done = get_student_total_hours(stu["id"])
            pct = min((done / total * 100) if total > 0 else 0, 100)
            color = progress_color(pct)

            bg = CARD_BG if i % 2 == 0 else "#f0f3f4"
            row = ctk.CTkFrame(self.scroll, fg_color=bg, corner_radius=4)
            row.pack(fill="x", pady=1)

            course_display = "Senza corso" if stu["course_name"] == SYSTEM_COURSE else stu["course_name"]
            ctk.CTkLabel(row, text=stu["name"], width=200, anchor="w",
                         font=ctk.CTkFont(size=12, weight="bold" if not stu["active"] else "normal"),
                         text_color=GREY_TEXT if not stu["active"] else "black").pack(side="left", padx=8, pady=7)
            ctk.CTkLabel(row, text=course_display, width=180, anchor="w",
                         font=ctk.CTkFont(size=11), text_color=GREY_TEXT).pack(side="left", padx=8)
            ctk.CTkLabel(row, text=f"{done:.1f} / {total:.1f} h", width=160, anchor="center",
                         font=ctk.CTkFont(size=11)).pack(side="left", padx=8)
            ctk.CTkLabel(row, text=f"{pct:.0f}%", width=130, anchor="center",
                         font=ctk.CTkFont(size=12, weight="bold"),
                         text_color=color).pack(side="left", padx=8)

            # Azioni
            actions = ctk.CTkFrame(row, fg_color="transparent", width=310)
            actions.pack(side="left", padx=8)
            ctk.CTkButton(actions, text="Modifica", width=70, height=28,
                          font=ctk.CTkFont(size=11),
                          command=lambda s=stu: self._open_edit_dialog(s)).pack(side="left", padx=2)
            ctk.CTkButton(actions, text="Storico", width=65, height=28,
                          font=ctk.CTkFont(size=11),
                          fg_color="#2980b9", hover_color="#1a6fa8",
                          command=lambda s=stu: self._open_history(s)).pack(side="left", padx=2)
            if pct >= 100:
                s_done = done
                s_total = total
                ctk.CTkButton(actions, text="📜 Attestato", width=95, height=28,
                              font=ctk.CTkFont(size=11),
                              fg_color="#8e44ad", hover_color="#6c3483",
                              command=lambda s=stu, sd=s_done, st=s_total: self._generate_certificate(s, sd, st)
                              ).pack(side="left", padx=2)
            arch_label = "Ripristina" if not stu["active"] else "Archivia"
            arch_color = "#27ae60" if not stu["active"] else "#e67e22"
            ctk.CTkButton(actions, text=arch_label, width=70, height=28,
                          font=ctk.CTkFont(size=11),
                          fg_color=arch_color, hover_color="#e67e22" if stu["active"] else "#1e8449",
                          command=lambda s=stu: self._toggle_archive(s)).pack(side="left", padx=2)
            if not stu["active"]:
                ctk.CTkButton(actions, text="Elimina", width=65, height=28,
                              font=ctk.CTkFont(size=11),
                              fg_color="#e74c3c", hover_color="#c0392b",
                              command=lambda s=stu: self._delete_permanently(s)).pack(side="left", padx=2)

    def _generate_certificate(self, student, hours_done, total_hours):
        name = student["name"]
        safe_name = name.replace(" ", "_")
        output_path = filedialog.asksaveasfilename(
            defaultextension=".pdf",
            filetypes=[("PDF", "*.pdf")],
            initialfile=f"Attestato_{safe_name}.pdf",
            title="Salva attestato di frequenza"
        )
        if not output_path:
            return
        try:
            generate_certificate(
                output_path, name, student["course_name"],
                student["enrollment_date"], hours_done, total_hours
            )
            if messagebox.askyesno("Attestato generato", f"PDF salvato in:\n{output_path}\n\nAprire il file?"):
                os.startfile(output_path)
        except Exception as e:
            messagebox.showerror("Errore", f"Errore nella generazione dell'attestato:\n{e}")

    def _export_csv(self):
        students = get_all_students(active_only=not self._show_archived)
        if not students:
            messagebox.showinfo("Export CSV", "Nessun allievo da esportare.")
            return
        output_path = filedialog.asksaveasfilename(
            defaultextension=".csv",
            filetypes=[("CSV", "*.csv")],
            initialfile="allievi.csv",
            title="Esporta allievi in CSV"
        )
        if not output_path:
            return
        try:
            with open(output_path, "w", newline="", encoding="utf-8-sig") as f:
                writer = csv.writer(f)
                writer.writerow(["Nome", "Corso", "Ore fatte", "Ore totali", "% Completamento", "Iscrizione"])
                for s in students:
                    total = s["total_hours"]
                    done = get_student_total_hours(s["id"])
                    pct = min((done / total * 100) if total > 0 else 0, 100)
                    course_disp = "Senza corso" if s["course_name"] == SYSTEM_COURSE else s["course_name"]
                    writer.writerow([s["name"], course_disp, f"{done:.1f}", f"{total:.1f}", f"{pct:.0f}%", s["enrollment_date"]])
            messagebox.showinfo("Export CSV", f"File salvato:\n{output_path}")
        except Exception as e:
            messagebox.showerror("Errore export", str(e))

    def _import_csv(self):
        file_path = filedialog.askopenfilename(
            filetypes=[("CSV", "*.csv"), ("Tutti i file", "*.*")],
            title="Importa allievi da CSV"
        )
        if not file_path:
            return
        courses = get_all_courses()
        course_map = {c["name"].lower(): c["id"] for c in courses}
        added, skipped = 0, []
        try:
            with open(file_path, newline="", encoding="utf-8-sig") as f:
                reader = csv.DictReader(f)
                for row in reader:
                    name = (row.get("Nome") or "").strip()
                    corso = (row.get("Corso") or "").strip()
                    ore_str = (row.get("Ore personalizzate") or "").strip()
                    if not name:
                        continue
                    course_id = course_map.get(corso.lower())
                    if not course_id:
                        skipped.append(f"{name} (corso '{corso}' non trovato)")
                        continue
                    custom_hours = None
                    if ore_str:
                        try:
                            custom_hours = float(ore_str.replace(",", "."))
                        except ValueError:
                            pass
                    try:
                        add_student(name, course_id, date.today().strftime("%Y-%m-%d"), custom_hours)
                        added += 1
                    except ValueError as e:
                        skipped.append(f"{name} ({e})")
        except Exception as e:
            messagebox.showerror("Errore import", str(e))
            return
        msg = f"Importati: {added} allievi."
        if skipped:
            msg += f"\n\nSaltati ({len(skipped)}):\n" + "\n".join(skipped[:10])
            if len(skipped) > 10:
                msg += f"\n... e altri {len(skipped) - 10}"
        messagebox.showinfo("Import CSV completato", msg)
        self._refresh_table()

    def _delete_permanently(self, student):
        name = student["name"]
        if messagebox.askyesno(
            "Elimina definitivamente",
            f"Eliminare permanentemente '{name}'?\n\nVerranno cancellati anche tutti i dati di presenza.\nQuesta operazione non è reversibile.",
            icon="warning"
        ):
            from database.models import delete_student_permanently
            delete_student_permanently(student["id"])
            self._refresh_table()

    def _open_add_dialog(self):
        StudentDialog(self, None, self._refresh_table)

    def _open_edit_dialog(self, student):
        StudentDialog(self, student, self._refresh_table)

    def _toggle_archive(self, student):
        name = student["name"]
        if student["active"]:
            if messagebox.askyesno("Archivia", f"Archiviare l'allievo '{name}'?"):
                archive_student(student["id"])
                self._refresh_table()
        else:
            restore_student(student["id"])
            self._refresh_table()

    def _open_history(self, student):
        HistoryDialog(self, student)


class StudentDialog(ctk.CTkToplevel):
    def __init__(self, parent, student, on_save):
        super().__init__(parent)
        self.student = student
        self.on_save = on_save
        self.title("Nuovo allievo" if student is None else "Modifica allievo")
        self.geometry("460x460")
        self.after(100, lambda: _popup_focus(self))
        self._build()

    def _build(self):
        pad = {"padx": 24, "pady": 6}

        ctk.CTkLabel(self, text="Nome allievo:", anchor="w").pack(fill="x", **pad)
        self.name_var = tk.StringVar(value=self.student["name"] if self.student else "")
        ctk.CTkEntry(self, textvariable=self.name_var, width=370).pack(**pad)

        ctk.CTkLabel(self, text="Corso:", anchor="w").pack(fill="x", **pad)
        courses = get_all_courses()
        self.course_map = {c["name"]: c["id"] for c in courses}
        self._course_hours = {c["name"]: c["total_hours"] for c in courses}

        # "Senza corso" sempre primo; poi i corsi reali
        dropdown_options = [SENZA_CORSO_LABEL] + list(self.course_map.keys())

        # Determina selezione corrente
        if self.student:
            if self.student["course_name"] == SYSTEM_COURSE:
                current_course = SENZA_CORSO_LABEL
            else:
                current_course = self.student["course_name"]
        else:
            current_course = SENZA_CORSO_LABEL if not courses else courses[0]["name"]

        self.course_var = tk.StringVar(value=current_course)
        self.course_menu = ctk.CTkOptionMenu(self, variable=self.course_var,
                                             values=dropdown_options,
                                             command=self._on_course_change)
        self.course_menu.pack(**pad)

        ctk.CTkLabel(self, text="Data iscrizione:", anchor="w").pack(fill="x", **pad)
        today = date.today()
        enroll = self.student["enrollment_date"] if self.student else today.strftime("%Y-%m-%d")
        try:
            y, m, d_ = map(int, enroll.split("-"))
        except Exception:
            y, m, d_ = today.year, today.month, today.day
        self.date_entry = DateEntry(self, width=16, date_pattern="dd/mm/yyyy",
                                   year=y, month=m, day=d_,
                                   font=("Helvetica", 14),
                                   background="#2c3e50", foreground="white",
                                   selectbackground="#2980b9")
        self.date_entry.pack(padx=24, pady=6, ipady=5)

        sep = ctk.CTkFrame(self, height=1, fg_color="#dfe6e9")
        sep.pack(fill="x", padx=24, pady=(8, 4))

        self.use_custom_var = tk.BooleanVar(value=False)
        chk_frame = ctk.CTkFrame(self, fg_color="transparent")
        chk_frame.pack(fill="x", padx=24, pady=(0, 4))
        self._custom_chk = ctk.CTkCheckBox(chk_frame,
                                           text="Ore personalizzate per questo allievo",
                                           variable=self.use_custom_var,
                                           command=self._toggle_custom_hours,
                                           fg_color="#2980b9", hover_color="#1a6fa8",
                                           checkmark_color="white")
        self._custom_chk.pack(side="left")

        hours_row = ctk.CTkFrame(self, fg_color="transparent")
        hours_row.pack(fill="x", padx=24, pady=(0, 6))
        self._hours_label = ctk.CTkLabel(hours_row, text="Ore totali:", text_color="#7f8c8d",
                                         font=ctk.CTkFont(size=11))
        self._hours_label.pack(side="left", padx=(0, 6))
        self.custom_hours_var = tk.StringVar(value="")
        self.custom_hours_entry = ctk.CTkEntry(hours_row, textvariable=self.custom_hours_var,
                                               width=80, state="disabled",
                                               font=ctk.CTkFont(size=12), justify="center")
        self.custom_hours_entry.pack(side="left")
        self.default_label = ctk.CTkLabel(hours_row, text="", text_color="#7f8c8d",
                                          font=ctk.CTkFont(size=10))
        self.default_label.pack(side="left", padx=8)

        # Precompila se allievo con custom_hours già impostato
        if self.student and self.student["custom_hours"] is not None:
            self.use_custom_var.set(True)
            self.custom_hours_var.set(str(self.student["custom_hours"]))
            self.custom_hours_entry.configure(state="normal")

        # Applica stato iniziale in base al corso selezionato
        self._on_course_change(current_course)

        btn_frame = ctk.CTkFrame(self, fg_color="transparent")
        btn_frame.pack(pady=10)
        ctk.CTkButton(btn_frame, text="Salva", width=100, command=self._save).pack(side="left", padx=6)
        ctk.CTkButton(btn_frame, text="Annulla", width=100,
                      fg_color="#7f8c8d", hover_color="#636e72",
                      command=self.destroy).pack(side="left", padx=6)

    def _on_course_change(self, selected=None):
        if selected is None:
            selected = self.course_var.get()

        if selected == SENZA_CORSO_LABEL:
            # Forza ore obbligatorie, blocca checkbox
            self.use_custom_var.set(True)
            self.custom_hours_entry.configure(state="normal")
            self._custom_chk.configure(state="disabled",
                                        text="Ore disponibili per questo allievo (obbligatorio)")
            self._hours_label.configure(text="Ore rimanenti:", text_color="#e67e22")
            self.default_label.configure(text="es. 45", text_color="#7f8c8d")
        else:
            # Ripristina comportamento normale
            self._custom_chk.configure(state="normal",
                                        text="Ore personalizzate per questo allievo")
            self._hours_label.configure(text="Ore totali:", text_color="#7f8c8d")
            if not self.use_custom_var.get():
                self.custom_hours_entry.configure(state="disabled")
                self.custom_hours_var.set("")
            self._update_default_label()

    def _update_default_label(self):
        name = self.course_var.get()
        if name in self._course_hours:
            self.default_label.configure(
                text=f"(default corso: {self._course_hours[name]:.0f} h)",
                text_color="#7f8c8d"
            )
        else:
            self.default_label.configure(text="")

    def _toggle_custom_hours(self):
        if self.use_custom_var.get():
            self.custom_hours_entry.configure(state="normal")
            if not self.custom_hours_var.get():
                course = self.course_var.get()
                if course in self._course_hours:
                    self.custom_hours_var.set(str(int(self._course_hours[course])))
        else:
            self.custom_hours_entry.configure(state="disabled")
            self.custom_hours_var.set("")

    def _save(self):
        name = self.name_var.get().strip()
        if not name:
            messagebox.showerror("Errore", "Inserire il nome dell'allievo.", parent=self)
            return

        course_sel = self.course_var.get()
        enroll_date = self.date_entry.get_date().strftime("%Y-%m-%d")

        if course_sel == SENZA_CORSO_LABEL:
            # Usa il corso di sistema; le ore sono obbligatorie
            course_id = get_system_course_id()
            if course_id is None:
                messagebox.showerror("Errore", "Corso di sistema non trovato. Riavvia l'app.", parent=self)
                return
            try:
                custom_hours = float(self.custom_hours_var.get().replace(",", "."))
                if custom_hours <= 0:
                    raise ValueError
            except ValueError:
                messagebox.showerror("Errore", "Inserire le ore rimanenti (numero > 0).", parent=self)
                return
        else:
            if course_sel not in self.course_map:
                messagebox.showerror("Errore", "Selezionare un corso valido.", parent=self)
                return
            course_id = self.course_map[course_sel]
            custom_hours = None
            if self.use_custom_var.get():
                try:
                    custom_hours = float(self.custom_hours_var.get().replace(",", "."))
                    if custom_hours <= 0:
                        raise ValueError
                except ValueError:
                    messagebox.showerror("Errore", "Ore personalizzate non valide.", parent=self)
                    return

        try:
            if self.student is None:
                add_student(name, course_id, enroll_date, custom_hours)
            else:
                update_student(self.student["id"], name, course_id, enroll_date, custom_hours)
            self.on_save()
            self.destroy()
        except Exception as e:
            messagebox.showerror("Errore", str(e), parent=self)


class HistoryDialog(ctk.CTkToplevel):
    def __init__(self, parent, student):
        super().__init__(parent)
        self.student = student
        self.title(f"Storico presenze — {student['name']}")
        self.geometry("620x500")
        self.after(100, lambda: _popup_focus(self))
        self._build()

    def _build(self):
        done = get_student_total_hours(self.student["id"])
        total = self.student["total_hours"]
        pct = min((done / total * 100) if total > 0 else 0, 100)

        info = ctk.CTkFrame(self, fg_color="#f5f6fa", corner_radius=0)
        info.pack(fill="x", padx=16, pady=(16, 8))
        course_disp = "Senza corso" if self.student["course_name"] == SYSTEM_COURSE else self.student["course_name"]
        ctk.CTkLabel(info, text=f"Corso: {course_disp}  |  "
                                f"Ore frequentate: {done:.1f} / {total:.1f} h  |  "
                                f"Completamento: {pct:.0f}%",
                     font=ctk.CTkFont(size=12)).pack(padx=8, pady=6)

        scroll = ctk.CTkScrollableFrame(self, fg_color="#f5f6fa")
        scroll.pack(fill="both", expand=True, padx=16, pady=(0, 16))

        rows = get_student_attendance_history(self.student["id"])

        cols = ["Data", "Giorno", "Turno", "Ore", "Note"]
        widths = [90, 100, 100, 60, 200]
        hdr = ctk.CTkFrame(scroll, fg_color="#2c3e50", corner_radius=6)
        hdr.pack(fill="x", pady=(0, 2))
        for col, w in zip(cols, widths):
            ctk.CTkLabel(hdr, text=col, width=w, font=ctk.CTkFont(size=11, weight="bold"),
                         text_color="white", anchor="w").pack(side="left", padx=8, pady=6)

        if not rows:
            ctk.CTkLabel(scroll, text="Nessuna presenza registrata.",
                         text_color="#7f8c8d").pack(pady=20)
            return

        for i, r in enumerate(rows):
            bg = "#ffffff" if i % 2 == 0 else "#ecf0f1"
            row = ctk.CTkFrame(scroll, fg_color=bg, corner_radius=4)
            row.pack(fill="x", pady=1)
            ctk.CTkLabel(row, text=r["date"], width=90, anchor="w",
                         font=ctk.CTkFont(size=11)).pack(side="left", padx=8, pady=5)
            ctk.CTkLabel(row, text=day_name(r["day_of_week"]), width=100, anchor="w",
                         font=ctk.CTkFont(size=11)).pack(side="left", padx=8)
            ctk.CTkLabel(row, text=slot_label(r["slot"]), width=100, anchor="w",
                         font=ctk.CTkFont(size=11)).pack(side="left", padx=8)
            ctk.CTkLabel(row, text=f"{r['hours_attended']:.1f}", width=60, anchor="center",
                         font=ctk.CTkFont(size=11)).pack(side="left", padx=8)
            ctk.CTkLabel(row, text=r["notes"] or "", width=200, anchor="w",
                         font=ctk.CTkFont(size=10), text_color="#7f8c8d").pack(side="left", padx=8)
