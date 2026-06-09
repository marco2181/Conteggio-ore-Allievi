GIORNI = ["Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica"]
MESI = ["Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"]

def day_name(day_of_week: int) -> str:
    return GIORNI[day_of_week]

def slot_label(slot: str) -> str:
    return slot.capitalize()

def session_label(day_of_week: int, slot: str) -> str:
    return f"{day_name(day_of_week)} {slot_label(slot)}"

def progress_color(pct: float) -> str:
    if pct >= 100:
        return "#27ae60"   # verde — completato
    elif pct >= 80:
        return "#e67e22"   # arancio — quasi completato
    else:
        return "#3498db"   # blu — in corso
