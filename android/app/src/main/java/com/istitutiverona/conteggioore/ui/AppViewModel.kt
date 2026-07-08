package com.istitutiverona.conteggioore.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.istitutiverona.conteggioore.data.Allievo
import com.istitutiverona.conteggioore.data.AppDatabase
import com.istitutiverona.conteggioore.data.Corso
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    val corsi = db.corsoDao().tutti()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val corsiAttivi = db.corsoDao().attivi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allievi = db.allievoDao().attivi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun salvaCorso(c: Corso) = viewModelScope.launch {
        if (c.id == 0L) db.corsoDao().inserisci(c) else db.corsoDao().aggiorna(c)
    }
    fun archiviaCorso(c: Corso) = viewModelScope.launch {
        db.corsoDao().aggiorna(c.copy(attivo = !c.attivo))
    }

    fun salvaAllievo(a: Allievo) = viewModelScope.launch {
        if (a.id == 0L) db.allievoDao().inserisci(a) else db.allievoDao().aggiorna(a)
    }
    suspend fun omonimi(nome: String, escludiId: Long) =
        db.allievoDao().contaOmonimi(nome, escludiId)

    fun nomeCorso(id: Long?): String =
        if (id == null) "Ore individuali"
        else corsi.value.firstOrNull { it.id == id }?.nome ?: "—"
}
