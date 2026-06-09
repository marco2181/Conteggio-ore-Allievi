import os
from datetime import datetime
from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.units import cm
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer, HRFlowable
)
from reportlab.lib.enums import TA_CENTER, TA_LEFT

from logic.sessions import day_name, slot_label, progress_color as _pct_to_hex, MESI
from database.models import SYSTEM_COURSE

def _course_label(name):
    return "Senza corso" if name == SYSTEM_COURSE else name

HEADER_COLOR = colors.HexColor("#2c3e50")
ROW_ALT_COLOR = colors.HexColor("#ecf0f1")
ACCENT_GREEN = colors.HexColor("#27ae60")
ACCENT_ORANGE = colors.HexColor("#e67e22")
ACCENT_RED = colors.HexColor("#e74c3c")


def _progress_color(pct):
    return colors.HexColor(_pct_to_hex(pct))


def _build_styles():
    styles = getSampleStyleSheet()
    title_style = ParagraphStyle(
        "AppTitle", parent=styles["Title"],
        fontSize=18, textColor=HEADER_COLOR, spaceAfter=4
    )
    subtitle_style = ParagraphStyle(
        "AppSubtitle", parent=styles["Normal"],
        fontSize=11, textColor=colors.HexColor("#7f8c8d"), spaceAfter=12
    )
    label_style = ParagraphStyle(
        "Label", parent=styles["Normal"],
        fontSize=10, textColor=HEADER_COLOR, fontName="Helvetica-Bold"
    )
    body_style = ParagraphStyle(
        "Body", parent=styles["Normal"],
        fontSize=10, textColor=colors.black
    )
    return title_style, subtitle_style, label_style, body_style


def generate_individual_report(output_path, student, rows, date_from, date_to):
    """
    student: dict-like con name, course_name, total_hours, hours_done
    rows: lista di sqlite3.Row con date, hours_attended, notes, day_of_week, slot
    """
    doc = SimpleDocTemplate(
        output_path, pagesize=A4,
        leftMargin=2*cm, rightMargin=2*cm, topMargin=2*cm, bottomMargin=2*cm
    )
    title_s, subtitle_s, label_s, body_s = _build_styles()
    story = []

    # Intestazione
    story.append(Paragraph("Registro Presenze", title_s))
    story.append(Paragraph(f"Scheda individuale — {student['name']}", subtitle_s))
    story.append(HRFlowable(width="100%", thickness=1, color=HEADER_COLOR))
    story.append(Spacer(1, 0.4*cm))

    # Info allievo
    hours_done = student["hours_done"]
    total = student["total_hours"]
    pct = min((hours_done / total * 100) if total > 0 else 0, 100)
    info_data = [
        ["Corso:", _course_label(student["course_name"])],
        ["Monte ore corso:", f"{total:.1f} h"],
        ["Ore frequentate:", f"{hours_done:.1f} h"],
        ["Completamento:", f"{pct:.1f}%"],
        ["Periodo:", f"{date_from} → {date_to}"],
    ]
    info_table = Table(info_data, colWidths=[4*cm, 10*cm])
    info_table.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (0, -1), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, -1), 10),
        ("TEXTCOLOR", (0, 0), (0, -1), HEADER_COLOR),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    story.append(info_table)
    story.append(Spacer(1, 0.6*cm))

    # Tabella presenze
    header = ["Data", "Giorno", "Turno", "Ore freq.", "Note"]
    table_data = [header]
    period_total = 0.0
    for r in rows:
        table_data.append([
            r["date"],
            day_name(r["day_of_week"]),
            slot_label(r["slot"]),
            f"{r['hours_attended']:.1f}",
            r["notes"] or ""
        ])
        period_total += r["hours_attended"]

    # Riga totale
    table_data.append(["", "", "TOTALE PERIODO", f"{period_total:.1f}", ""])

    col_widths = [2.8*cm, 2.8*cm, 3.5*cm, 2.5*cm, 5.4*cm]
    t = Table(table_data, colWidths=col_widths, repeatRows=1)
    n = len(table_data)
    style = TableStyle([
        # Header
        ("BACKGROUND", (0, 0), (-1, 0), HEADER_COLOR),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, 0), 10),
        ("ALIGN", (0, 0), (-1, 0), "CENTER"),
        # Body
        ("FONTSIZE", (0, 1), (-1, -1), 9),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("ROWBACKGROUNDS", (0, 1), (-1, n-2), [colors.white, ROW_ALT_COLOR]),
        ("GRID", (0, 0), (-1, -2), 0.3, colors.HexColor("#bdc3c7")),
        ("ALIGN", (3, 1), (3, -1), "CENTER"),
        # Riga totale
        ("BACKGROUND", (0, n-1), (-1, n-1), HEADER_COLOR),
        ("TEXTCOLOR", (0, n-1), (-1, n-1), colors.white),
        ("FONTNAME", (0, n-1), (-1, n-1), "Helvetica-Bold"),
        ("ALIGN", (2, n-1), (3, n-1), "CENTER"),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ])
    t.setStyle(style)
    story.append(t)

    story.append(Spacer(1, 0.6*cm))
    story.append(Paragraph(
        f"Generato il {datetime.now().strftime('%d/%m/%Y %H:%M')}",
        ParagraphStyle("Footer", fontSize=8, textColor=colors.grey, alignment=TA_LEFT)
    ))

    doc.build(story)


def generate_monthly_report(output_path, rows, year, month):
    """
    rows: lista di dict-like con id, name, course_name, total_hours, hours_month, hours_total
    """
    doc = SimpleDocTemplate(
        output_path, pagesize=A4,
        leftMargin=2*cm, rightMargin=2*cm, topMargin=2*cm, bottomMargin=2*cm
    )
    title_s, subtitle_s, label_s, body_s = _build_styles()
    story = []

    story.append(Paragraph("Registro Mensile Presenze", title_s))
    story.append(Paragraph(f"{MESI[month - 1]} {year}", subtitle_s))
    story.append(HRFlowable(width="100%", thickness=1, color=HEADER_COLOR))
    story.append(Spacer(1, 0.5*cm))

    header = ["Allievo", "Corso", "Ore del mese", "Ore totali", "Monte ore", "Completamento"]
    table_data = [header]

    for r in rows:
        total = r["total_hours"]
        done = r["hours_total"]
        pct = min((done / total * 100) if total > 0 else 0, 100)
        table_data.append([
            r["name"],
            _course_label(r["course_name"]),
            f"{r['hours_month']:.1f}",
            f"{done:.1f}",
            f"{total:.1f}",
            f"{pct:.1f}%",
        ])

    col_widths = [4.5*cm, 4.0*cm, 2.5*cm, 2.5*cm, 2.5*cm, 2.5*cm]
    t = Table(table_data, colWidths=col_widths, repeatRows=1)
    n = len(table_data)

    row_styles = [
        ("BACKGROUND", (0, 0), (-1, 0), HEADER_COLOR),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, 0), 9),
        ("ALIGN", (0, 0), (-1, 0), "CENTER"),
        ("FONTSIZE", (0, 1), (-1, -1), 9),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("GRID", (0, 0), (-1, -1), 0.3, colors.HexColor("#bdc3c7")),
        ("ALIGN", (2, 1), (-1, -1), "CENTER"),
        ("ROWBACKGROUNDS", (0, 1), (-1, n-1), [colors.white, ROW_ALT_COLOR]),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]

    # Colora % completamento per ogni riga
    for i, r in enumerate(rows, start=1):
        total = r["total_hours"]
        done = r["hours_total"]
        pct = min((done / total * 100) if total > 0 else 0, 100)
        c = _progress_color(pct)
        row_styles.append(("TEXTCOLOR", (5, i), (5, i), c))
        row_styles.append(("FONTNAME", (5, i), (5, i), "Helvetica-Bold"))

    t.setStyle(TableStyle(row_styles))
    story.append(t)

    story.append(Spacer(1, 0.6*cm))
    story.append(Paragraph(
        f"Generato il {datetime.now().strftime('%d/%m/%Y %H:%M')}",
        ParagraphStyle("Footer", fontSize=8, textColor=colors.grey)
    ))

    doc.build(story)
