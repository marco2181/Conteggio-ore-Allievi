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
