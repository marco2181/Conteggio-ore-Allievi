package com.istitutiverona.conteggioore.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Modello Fase 1: Persona ← Percorso → Corso, + Turni abituali.
// ponytail: presenze/ore-fatte arrivano in Fase 2; qui solo anagrafica.

@Entity(tableName = "corsi")
data class Corso(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val ore: Double,
    val attivo: Boolean = true,
)

// Una persona = una scheda. I suoi percorsi stanno in Percorso.
@Entity(tableName = "persone")
data class Persona(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val etichetta: String? = null,   // interna, distingue omonimi; non va nei PDF
    val attiva: Boolean = true,       // archivia/ripristina
)

const val PERCORSO_ATTIVO = "ATTIVO"
const val PERCORSO_ARCHIVIATO = "ARCHIVIATO"   // percorso concluso, sostituito da "Nuovo corso"

// Un ciclo di studi di una persona. "Nuovo corso" archivia il vecchio e ne crea uno.
@Entity(tableName = "percorsi")
data class Percorso(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personaId: Long,
    val corsoId: Long? = null,        // null = ore individuali
    val oreMonte: Double,             // monte ore effettivo (da corso o ore rimanenti iniziali)
    val giaAvviato: Boolean = false,  // ore residue iniziali → percorso iniziato altrove
    val note: String? = null,
    val dataInizio: String,           // ISO yyyy-MM-dd
    val stato: String = PERCORSO_ATTIVO,
)

// Turno settimanale fisso (seed all'avvio, modificabile in Impostazioni).
@Entity(tableName = "turni")
data class Turno(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val giorno: Int,                  // 0 = Lunedì .. 5 = Sabato
    val fascia: String,               // mattina / pomeriggio / sera
    val oreDefault: Double,
)

// Turno abituale di una persona (join). Più di uno per persona.
@Entity(tableName = "turni_abituali", primaryKeys = ["personaId", "turnoId"])
data class TurnoAbituale(
    val personaId: Long,
    val turnoId: Long,
)
