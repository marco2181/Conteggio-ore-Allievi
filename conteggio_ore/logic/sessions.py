GIORNI = ["Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica"]
SLOT_ORDER = {"mattina": 0, "pomeriggio": 1, "sera": 2}

def day_name(day_of_week: int) -> str:
    return GIORNI[day_of_week]

def slot_label(slot: str) -> str:
    return slot.capitalize()

def session_label(day_of_week: int, slot: str) -> str:
    return f"{day_name(day_of_week)} {slot_label(slot)}"

def progress_color(pct: float) -> str:
    if pct >= 100:
        return "#e74c3c"   # rosso
    elif pct >= 80:
        return "#e67e22"   # arancio
    else:
        return "#27ae60"   # verde
