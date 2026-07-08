package com.istitutiverona.conteggioore.ui

import com.istitutiverona.conteggioore.data.Turno

val GIORNI = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")

fun giornoNome(g: Int): String = GIORNI.getOrElse(g) { "?" }

fun etichettaTurno(t: Turno): String =
    "${giornoNome(t.giorno)} ${t.fascia} (${oreFmt(t.oreDefault)}h)"

/** 3.0 → "3", 2.5 → "2,5" */
fun oreFmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ',')
