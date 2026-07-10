package com.istitutiverona.conteggioore.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class NomeEtichetta(val nome: String, val etichetta: String?)

// ── Righe per i report PDF ─────────────────────────────
data class RigaPresenzaReport(
    val data: String,
    val giorno: Int,
    val fascia: String,
    val ore: Double,
    val note: String?,
)

data class RigaMensile(
    val nome: String,
    val etichetta: String?,
    val nomeCorso: String?,
    val corsoId: Long?,
    val giaAvviato: Boolean,
    val oreMese: Double,
    val oreTotali: Double,
    val oreMonte: Double,
)

data class RigaReportCorso(
    val nome: String,
    val etichetta: String?,
    val dataInizio: String,
    val giaAvviato: Boolean,
    val oreFatte: Double,
    val oreMonte: Double,
)

// Riga dashboard: persona + percorso attivo + ore fatte + n. turni abituali.
data class RigaDashboard(
    val personaId: Long,
    val nome: String,
    val etichetta: String?,
    val nomeCorso: String?,
    val corsoId: Long?,
    val oreMonte: Double,
    val oreFatte: Double,
    val nTurni: Int,
)

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

    @Query(
        """
        SELECT p.id AS personaId, p.nome AS nome, p.etichetta AS etichetta,
               c.nome AS nomeCorso, pe.corsoId AS corsoId,
               pe.oreMonte AS oreMonte,
               COALESCE((SELECT SUM(pr.ore) FROM presenze pr WHERE pr.percorsoId = pe.id), 0) AS oreFatte,
               (SELECT COUNT(*) FROM turni_abituali ta WHERE ta.personaId = p.id) AS nTurni
        FROM persone p
        JOIN percorsi pe ON pe.personaId = p.id AND pe.stato = 'ATTIVO'
        LEFT JOIN corsi c ON c.id = pe.corsoId
        WHERE p.attiva = 1
        ORDER BY p.nome
        """
    )
    fun dashboard(): Flow<List<RigaDashboard>>

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

    @Query("DELETE FROM presenze WHERE percorsoId IN (SELECT id FROM percorsi WHERE personaId = :personaId)")
    suspend fun cancellaPresenzeDi(personaId: Long)
    @Query("DELETE FROM percorsi WHERE personaId = :personaId")
    suspend fun cancellaPercorsiDi(personaId: Long)
    @Query("DELETE FROM persone WHERE id = :personaId")
    suspend fun cancellaPersona(personaId: Long)

    // Elimina definitivamente persona + percorsi + presenze + turni abituali.
    @Transaction
    suspend fun eliminaPersonaCompleta(personaId: Long) {
        cancellaPresenzeDi(personaId)
        cancellaPercorsiDi(personaId)
        cancellaTurniAbituali(personaId)
        cancellaPersona(personaId)
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

    // Assenti in una settimana [lunedi..sabato]: persone attive col percorso attivo
    // che NON hanno presenze in quella settimana e non hanno ancora completato il monte ore
    // entro fine settimana; escluse quelle iscritte dopo sabato.
    @Query(
        """
        SELECT p.nome AS nome, p.etichetta AS etichetta
        FROM persone p
        JOIN percorsi pe ON pe.personaId = p.id AND pe.stato = 'ATTIVO'
        WHERE p.attiva = 1
          AND pe.dataInizio <= :sabato
          AND (SELECT COUNT(*) FROM presenze pr
               WHERE pr.percorsoId = pe.id AND pr.data BETWEEN :lunedi AND :sabato) = 0
          AND COALESCE((SELECT SUM(pr2.ore) FROM presenze pr2
               WHERE pr2.percorsoId = pe.id AND pr2.data <= :sabato), 0) < pe.oreMonte
        ORDER BY p.nome
        """
    )
    suspend fun assentiSettimana(lunedi: String, sabato: String): List<NomeEtichetta>

    @Insert suspend fun inserisci(p: Presenza)
    @Update suspend fun aggiorna(p: Presenza)
    @Delete suspend fun elimina(p: Presenza)

    // ── Query report PDF ───────────────────────────────
    @Query(
        """
        SELECT pr.data AS data, t.giorno AS giorno, t.fascia AS fascia, pr.ore AS ore, pr.note AS note
        FROM presenze pr JOIN turni t ON t.id = pr.turnoId
        WHERE pr.percorsoId = :percorsoId
        ORDER BY pr.data, t.giorno, t.id
        """
    )
    suspend fun presenzeReport(percorsoId: Long): List<RigaPresenzaReport>

    @Query(
        """
        SELECT p.nome AS nome, p.etichetta AS etichetta, c.nome AS nomeCorso,
               pe.corsoId AS corsoId, pe.giaAvviato AS giaAvviato,
               COALESCE((SELECT SUM(ore) FROM presenze WHERE percorsoId = pe.id AND data BETWEEN :da AND :a), 0) AS oreMese,
               COALESCE((SELECT SUM(ore) FROM presenze WHERE percorsoId = pe.id), 0) AS oreTotali,
               pe.oreMonte AS oreMonte
        FROM persone p
        JOIN percorsi pe ON pe.personaId = p.id AND pe.stato = 'ATTIVO'
        LEFT JOIN corsi c ON c.id = pe.corsoId
        WHERE p.attiva = 1
        ORDER BY p.nome
        """
    )
    suspend fun righeMensili(da: String, a: String): List<RigaMensile>

    @Query(
        """
        SELECT p.nome AS nome, p.etichetta AS etichetta, pe.dataInizio AS dataInizio,
               pe.giaAvviato AS giaAvviato,
               COALESCE((SELECT SUM(ore) FROM presenze WHERE percorsoId = pe.id), 0) AS oreFatte,
               pe.oreMonte AS oreMonte
        FROM persone p
        JOIN percorsi pe ON pe.personaId = p.id AND pe.stato = 'ATTIVO' AND pe.corsoId = :corsoId
        WHERE p.attiva = 1
        ORDER BY p.nome
        """
    )
    suspend fun righeCorso(corsoId: Long): List<RigaReportCorso>

    @Query("SELECT * FROM percorsi WHERE id = :id")
    suspend fun percorso(id: Long): Percorso?

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
