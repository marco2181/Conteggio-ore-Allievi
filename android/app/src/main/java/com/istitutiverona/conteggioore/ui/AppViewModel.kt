package com.istitutiverona.conteggioore.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.istitutiverona.conteggioore.data.AppDatabase
import com.istitutiverona.conteggioore.data.Corso
import com.istitutiverona.conteggioore.data.Percorso
import com.istitutiverona.conteggioore.data.Persona
import com.istitutiverona.conteggioore.data.Turno
import com.istitutiverona.conteggioore.data.TurnoAbituale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    init {
        // Seed turni al primo avvio se vuoti.
        viewModelScope.launch {
            if (db.turnoDao().conteggio() == 0)
                db.turnoDao().inserisciTutti(AppDatabase.TURNI_DEFAULT)
        }
    }

    val corsi = db.corsoDao().tutti()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val corsiAttivi = db.corsoDao().attivi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val persone = db.personaDao().attiveConPercorso()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val turni = db.turnoDao().tutti()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val dashboard = db.personaDao().dashboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Assenti della settimana scorsa (lun-sab). Ricalcolati on-demand.
    val assentiSettScorsa = MutableStateFlow<List<com.istitutiverona.conteggioore.data.NomeEtichetta>>(emptyList())
    init {
        viewModelScope.launch {
            val oggi = LocalDate.now()
            val lunScorso = oggi.minusWeeks(1)
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val sabScorso = lunScorso.plusDays(5)
            assentiSettScorsa.value =
                db.presenzaDao().assentiSettimana(lunScorso.toString(), sabScorso.toString())
        }
    }

    // ── Corsi ──────────────────────────────────────────────
    fun salvaCorso(c: Corso) = viewModelScope.launch {
        if (c.id == 0L) db.corsoDao().inserisci(c) else db.corsoDao().aggiorna(c)
    }
    fun archiviaCorso(c: Corso) = viewModelScope.launch {
        db.corsoDao().aggiorna(c.copy(attivo = !c.attivo))
    }
    /** Elimina un corso solo se nessun percorso lo usa. Ritorna true se eliminato. */
    suspend fun eliminaCorsoSePossibile(c: Corso): Boolean {
        if (db.corsoDao().percorsiConCorso(c.id) > 0) return false
        db.corsoDao().elimina(c)
        return true
    }

    // ── Persone / percorsi ─────────────────────────────────
    suspend fun omonimi(nome: String, escludiId: Long) =
        db.personaDao().contaOmonimi(nome, escludiId)

    suspend fun turniAbitualiDi(personaId: Long) =
        db.personaDao().turniAbitualiDi(personaId)

    /** Crea persona + primo percorso + turni abituali. */
    fun creaPersona(persona: Persona, percorso: Percorso, turniIds: List<Long>) =
        viewModelScope.launch {
            db.personaDao().creaPersonaCompleta(persona, percorso, turniIds)
        }

    /** Aggiorna anagrafica persona (nome/etichetta) + turni abituali. */
    fun aggiornaPersona(persona: Persona, turniIds: List<Long>) = viewModelScope.launch {
        db.personaDao().aggiornaPersona(persona)
        db.personaDao().cancellaTurniAbituali(persona.id)
        if (turniIds.isNotEmpty())
            db.personaDao().inserisciTurniAbituali(
                turniIds.map { com.istitutiverona.conteggioore.data.TurnoAbituale(persona.id, it) }
            )
    }

    /** "Nuovo corso": archivia il percorso attivo, ne apre uno nuovo. */
    fun nuovoCorso(personaId: Long, percorso: Percorso) = viewModelScope.launch {
        db.personaDao().nuovoCorso(personaId, percorso)
    }

    // ── Presenze ───────────────────────────────────────────
    // Data e turno selezionati nel registro presenze.
    val dataSel = MutableStateFlow(LocalDate.now().toString())
    val turnoSelId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val righePresenza = combine(turnoSelId, dataSel) { tId, data -> tId to data }
        .flatMapLatest { (tId, data) ->
            if (tId == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else db.presenzaDao().righe(tId, data)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun segnaPresenza(percorsoId: Long, turnoId: Long, data: String, ore: Double, note: String? = null) =
        viewModelScope.launch { db.presenzaDao().segna(percorsoId, turnoId, data, ore, note) }

    fun rendiAbituale(personaId: Long, turnoId: Long) = viewModelScope.launch {
        db.personaDao().aggiungiTurnoAbituale(TurnoAbituale(personaId, turnoId))
    }

    // ── Report PDF ─────────────────────────────────
    suspend fun pdfSchedaIndividuale(riga: com.istitutiverona.conteggioore.data.PersonaConPercorso): java.io.File {
        val ctx = getApplication<Application>()
        val percorsoId = riga.percorsoId!!
        val percorso = db.presenzaDao().percorso(percorsoId)
        val presenze = db.presenzaDao().presenzeReport(percorsoId).map {
            com.istitutiverona.conteggioore.pdf.PresenzaPdf(it.data, it.giorno, it.fascia, it.ore, it.note)
        }
        return com.istitutiverona.conteggioore.pdf.PdfReport.schedaIndividuale(
            ctx,
            riga.persona.nome + (riga.persona.etichetta?.let { " ($it)" } ?: ""),
            if (riga.corsoId == null) "Ore individuali" else riga.nomeCorso ?: "—",
            riga.oreMonte ?: 0.0,
            riga.giaAvviato == true,
            percorso?.note,
            presenze,
        )
    }

    suspend fun pdfRegistroMensile(anno: Int, mese: Int): java.io.File {
        val ctx = getApplication<Application>()
        val da = LocalDate.of(anno, mese, 1)
        val a = da.plusMonths(1).minusDays(1)
        val righe = db.presenzaDao().righeMensili(da.toString(), a.toString()).map {
            com.istitutiverona.conteggioore.pdf.RigaMensilePdf(
                it.nome + (it.etichetta?.let { e -> " ($e)" } ?: ""),
                if (it.corsoId == null) "Ore individuali" else it.nomeCorso ?: "—",
                it.giaAvviato, it.oreMese, it.oreTotali, it.oreMonte,
            )
        }
        val label = "%02d/%d".format(mese, anno)
        return com.istitutiverona.conteggioore.pdf.PdfReport.registroMensile(ctx, label, righe)
    }

    suspend fun pdfReportCorso(corso: Corso): java.io.File {
        val ctx = getApplication<Application>()
        val righe = db.presenzaDao().righeCorso(corso.id).map {
            com.istitutiverona.conteggioore.pdf.RigaCorsoPdf(
                it.nome + (it.etichetta?.let { e -> " ($e)" } ?: ""),
                it.dataInizio, it.giaAvviato, it.oreFatte, it.oreMonte,
            )
        }
        return com.istitutiverona.conteggioore.pdf.PdfReport.reportCorso(ctx, corso.nome, righe)
    }

    // ── Turni ──────────────────────────────────────────────
    fun salvaTurno(t: Turno) = viewModelScope.launch {
        if (t.id == 0L) db.turnoDao().inserisci(t) else db.turnoDao().aggiorna(t)
    }
    fun eliminaTurno(t: Turno) = viewModelScope.launch { db.turnoDao().elimina(t) }

    // ── Helper ─────────────────────────────────────────────
    fun nomeCorsoDaId(id: Long?): String =
        if (id == null) "Ore individuali"
        else corsi.value.firstOrNull { it.id == id }?.nome ?: "—"
}
