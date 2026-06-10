"""Cache dei font condivisi tra i widget.

Creare un CTkFont nuovo per ogni widget è costoso: nelle tabelle con molte
righe si arrivava a centinaia di oggetti font per ogni refresh. Riusare la
stessa istanza è sicuro (CTkFont è pensato per essere condiviso) e rende
il rendering delle tabelle molto più rapido.
"""
import customtkinter as ctk

_cache = {}


def font(size, weight="normal"):
    key = (size, weight)
    f = _cache.get(key)
    if f is None:
        f = ctk.CTkFont(size=size, weight=weight)
        _cache[key] = f
    return f
