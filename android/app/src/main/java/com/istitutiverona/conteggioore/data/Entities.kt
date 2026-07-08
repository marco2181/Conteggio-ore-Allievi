package com.istitutiverona.conteggioore.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// ponytail: solo Corso + Allievo per Fase 0. Percorso/Turno/Presenza arrivano in Fase 1-2.

@Entity(tableName = "corsi")
data class Corso(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val ore: Double,
    val attivo: Boolean = true,
)

@Entity(tableName = "allievi")
data class Allievo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val etichetta: String? = null,      // interna, per distinguere omonimi
    val corsoId: Long? = null,          // null = ore individuali
    val oreRimanentiIniziali: Double? = null,
    val dataInizio: String,             // ISO yyyy-MM-dd
    val attivo: Boolean = true,
)
