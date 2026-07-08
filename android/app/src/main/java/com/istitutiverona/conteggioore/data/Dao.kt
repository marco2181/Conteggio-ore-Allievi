package com.istitutiverona.conteggioore.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Persona + il suo percorso attivo (se c'è) + nome corso, per le liste.
data class PersonaConPercorso(
    @Embedded val persona: Persona,
    val percorsoId: Long?,
    val corsoId: Long?,
    val oreMonte: Double?,
    val giaAvviato: Boolean?,
    val nomeCorso: String?,
)

// Riga per il registro presenze: persona + suo percorso attivo + ore già fatte + presenza del turno/data (se c'è).
data class RigaPresenza(
    val personaId: Long,
    val nome: String,
    val etichetta: String?,
    val percorsoId: Long,
    val oreMonte: Double,
    val oreFatte: Double,
    val presenzaId: Long?,   // null = non ancora segnato oggi in questo turno
    val oreSegnate: Double?,
    val atteso: Boolean,     // true se ha questo turno tra gli abituali
)

@Dao
interface CorsoDao {
    @Query("SELECT * FROM corsi ORDER BY attivo DESC, nome")
    fun tutti(): Flow<List<Corso>>

    @Query("SELECT * FROM corsi WHERE attivo = 1 ORDER BY nome")
    fun attivi(): Flow<List<Corso>>

    @Query("SELECT COUNT(*) FROM percorsi WHERE corsoId = :corsoId")
    suspend fun percorsiConCorso(corsoId: Long): Int

    @Insert suspend fun inserisci(c: Corso): Long
    @Update suspend fun aggiorna(c: Corso)
    @Delete suspend fun elimina(c: Corso)
}

@Dao
interface TurnoDao {
    @Query("SELECT * FROM turni ORDER BY giorno, id")
    fun tutti(): Flow<List<Turno>>

    @Query("SELECT COUNT(*) FROM turni")
    suspend fun conteggio(): Int

    @Insert suspend fun inserisci(t: Turno): Long
    @Insert suspend fun inserisciTutti(t: List<Turno>)
    @Update suspend fun aggiorna(t: Turno)
    @Delete suspend fun elimina(t: Turno)
}

@Dao
interface PersonaDao {
    @Transaction
    @Query(
        """
        SELECT p.*, pe.id AS percorsoId, pe.corsoId AS corsoId,
               pe.oreMonte AS oreMonte, pe.giaAvviato AS giaAvviato,
               c.nome AS nomeCorso
        FROM persone p
        LEFT JOIN percorsi pe ON pe.personaId = p.id AND pe.stato = 'ATTIVO'
        LEFT JOIN corsi c ON c.id = pe.corsoId
        WHERE p.attiva = 1
        ORDER BY p.nome
        """
    )
    fun attiveConPercorso(): Flow<List<PersonaConPercorso>>

    @Query("SELECT COUNT(*) FROM persone WHERE nome = :nome AND id != :escludiId")
    suspend fun contaOmonimi(nome: String, escludiId: Long): Int

    @Query("SELECT * FROM percorsi WHERE personaId = :personaId ORDER BY dataInizio DESC")
    fun percorsiDi(personaId: Long): Flow<List<Percorso>>

    @Query("SELECT turnoId FROM turni_abituali WHERE personaId = :personaId")
    suspend fun turniAbitualiDi(personaId: Long): List<Long>

    @Insert suspend fun inserisciPersona(p: Persona): Long
    @Update suspend fun aggiornaPersona(p: Persona)
    @Insert suspend fun inserisciPercorso(pe: Percorso): Long
    @Update suspend fun aggiornaPercorso(pe: Percorso)

    @Query("DELETE FROM turni_abituali WHERE personaId = :personaId")
    suspend fun cancellaTurniAbituali(personaId: Long)
    @Insert suspend fun inserisciTurniAbituali(t: List<TurnoAbituale>)

    @Query("UPDATE percorsi SET stato = 'ARCHIVIATO' WHERE personaId = :personaId AND stato = 'ATTIVO'")
    suspend fun archiviaPercorsoAttivo(personaId: Long)

    @Query("SELECT id FROM percorsi WHERE personaId = :personaId AND stato = 'ATTIVO' LIMIT 1")
    suspend fun percorsoAttivoId(personaId: Long): Long?

    @Insert suspend fun aggiungiTurnoAbituale(t: TurnoAbituale)

    // Crea persona + primo percorso + turni abituali in una transazione.
    @Transaction
    suspend fun creaPersonaCompleta(persona: Persona, percorso: Percorso, turniIds: List<Long>): Long {
        val personaId = inserisciPersona(persona)
        inserisciPercorso(percorso.copy(personaId = personaId))
        if (turniIds.isNotEmpty())
            inserisciTurniAbituali(turniIds.map { TurnoAbituale(personaId, it) })
        return personaId
    }

    // "Nuovo corso": archivia il percorso attivo, ne apre uno nuovo.
    @Transaction
    suspend fun nuovoCorso(personaId: Long, nuovo: Percorso) {
        archiviaPercorsoAttivo(personaId)
        inserisciPercorso(nuovo.copy(personaId = personaId, stato = PERCORSO_ATTIVO))
    }
}

@Dao
interface PresenzaDao {
    // Tutte le persone attive col percorso attivo: ore fatte + eventuale presenza per (turno,data).
    // atteso = la persona ha quel turno tra gli abituali.
    @Query(
        """
        SELECT p.id AS personaId, p.nome AS nome, p.etichetta AS etichetta,
               pe.id AS percorsoId, pe.oreMonte AS oreMonte,
               COALESCE((SELECT SUM(pr2.ore) FROM presenze pr2 WHERE pr2.percorsoId = pe.id), 0) AS oreFatte,
               pr.id AS presenzaId, pr.ore AS oreSegnate,
               (SELECT COUNT(*) FROM turni_abituali ta WHERE ta.personaId = p.id AND ta.turnoId = :turnoId) > 0 AS atteso
        FROM persone p
        JOIN percorsi pe ON pe.personaId = p.id AND pe.stato = 'ATTIVO'
        LEFT JOIN presenze pr ON pr.percorsoId = pe.id AND pr.turnoId = :turnoId AND pr.data = :data
        WHERE p.attiva = 1
        ORDER BY atteso DESC, p.nome
        """
    )
    fun righe(turnoId: Long, data: String): Flow<List<RigaPresenza>>

    @Query("SELECT * FROM presenze WHERE percorsoId = :percorsoId AND turnoId = :turnoId AND data = :data LIMIT 1")
    suspend fun trova(percorsoId: Long, turnoId: Long, data: String): Presenza?

    @Insert suspend fun inserisci(p: Presenza)
    @Update suspend fun aggiorna(p: Presenza)
    @Delete suspend fun elimina(p: Presenza)

    // Upsert: segna/aggiorna la presenza. Se ore <= 0, cancella.
    @Transaction
    suspend fun segna(percorsoId: Long, turnoId: Long, data: String, ore: Double, note: String?) {
        val esistente = trova(percorsoId, turnoId, data)
        when {
            ore <= 0.0 && esistente != null -> elimina(esistente)
            esistente == null && ore > 0.0 ->
                inserisci(Presenza(percorsoId = percorsoId, turnoId = turnoId, data = data, ore = ore, note = note))
            esistente != null && ore > 0.0 ->
                aggiorna(esistente.copy(ore = ore, note = note ?: esistente.note))
        }
    }
}
