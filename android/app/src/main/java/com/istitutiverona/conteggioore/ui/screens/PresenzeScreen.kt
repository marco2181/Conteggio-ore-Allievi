package com.istitutiverona.conteggioore.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istitutiverona.conteggioore.data.RigaPresenza
import com.istitutiverona.conteggioore.data.Turno
import com.istitutiverona.conteggioore.ui.AppViewModel
import com.istitutiverona.conteggioore.ui.giornoNome
import com.istitutiverona.conteggioore.ui.oreFmt
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresenzeScreen(vm: AppViewModel) {
    val turni by vm.turni.collectAsState()
    val dataSel by vm.dataSel.collectAsState()
    val turnoSelId by vm.turnoSelId.collectAsState()
    val righe by vm.righePresenza.collectAsState()

    val data = LocalDate.parse(dataSel)
    val giornoIdx = data.dayOfWeek.value - 1               // 0 = Lunedì
    val turniDelGiorno = turni.filter { it.giorno == giornoIdx }

    // Seleziona automaticamente il primo turno del giorno quando cambia data.
    LaunchedEffect(dataSel, turni) {
        if (turniDelGiorno.none { it.id == turnoSelId })
            vm.turnoSelId.value = turniDelGiorno.firstOrNull()?.id
    }

    var mostraData by remember { mutableStateOf(false) }
    var mostraAggiungi by remember { mutableStateOf(false) }

    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Selettore data
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${giornoNome(giornoIdx)} $dataSel",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { vm.dataSel.value = LocalDate.now().toString() }) { Text("Oggi") }
                TextButton(onClick = { mostraData = true }) { Text("Cambia") }
            }

            if (turniDelGiorno.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nessun turno in questo giorno")
                }
                return@Column
            }

            // Chip turni del giorno
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                turniDelGiorno.forEach { t ->
                    FilterChip(
                        selected = turnoSelId == t.id,
                        onClick = { vm.turnoSelId.value = t.id },
                        label = { Text("${t.fascia} ${oreFmt(t.oreDefault)}h") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            val turnoCorrente = turniDelGiorno.firstOrNull { it.id == turnoSelId }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            LazyColumn(Modifier.weight(1f)) {
                val attesi = righe.filter { it.atteso }
                val altri = righe.filter { !it.atteso }
                if (attesi.isNotEmpty()) {
                    item { SezioneHeader("Attesi") }
                    items(attesi, key = { it.percorsoId }) { r ->
                        RigaAllievo(r, turnoCorrente, vm)
                    }
                }
                if (altri.isNotEmpty()) {
                    item { SezioneHeader("Altri (già presenti)") }
                    items(altri.filter { it.presenzaId != null }, key = { it.percorsoId }) { r ->
                        RigaAllievo(r, turnoCorrente, vm)
                    }
                }
            }

            OutlinedButton(
                onClick = { mostraAggiungi = true },
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) { Text("Aggiungi allievo (fuori turno)") }
        }
    }

    if (mostraData) {
        DatePickerModal(dataSel) { nuova ->
            if (nuova != null) vm.dataSel.value = nuova
            mostraData = false
        }
    }

    if (mostraAggiungi && turnoSelId != null) {
        AggiungiFuoriTurno(
            candidati = righe.filter { !it.atteso && it.presenzaId == null },
            onChiudi = { mostraAggiungi = false },
        ) { r, rendiAbituale ->
            vm.segnaPresenza(r.percorsoId, turnoSelId!!, dataSel, turnoCorrenteOre(turni, turnoSelId))
            if (rendiAbituale) vm.rendiAbituale(r.personaId, turnoSelId!!)
            mostraAggiungi = false
        }
    }
}

private fun turnoCorrenteOre(turni: List<Turno>, turnoId: Long?): Double =
    turni.firstOrNull { it.id == turnoId }?.oreDefault ?: 0.0

@Composable
private fun SezioneHeader(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun RigaAllievo(r: RigaPresenza, turno: Turno?, vm: AppViewModel) {
    val presente = r.presenzaId != null
    val dataSel by vm.dataSel.collectAsState()
    var modificaOre by remember(r.percorsoId, r.presenzaId) { mutableStateOf(false) }

    val pct = if (r.oreMonte > 0) (r.oreFatte / r.oreMonte * 100).toInt() else 0
    ListItem(
        leadingContent = {
            Checkbox(
                checked = presente,
                onCheckedChange = { on ->
                    val ore = if (on) (turno?.oreDefault ?: 0.0) else 0.0
                    vm.segnaPresenza(r.percorsoId, turno!!.id, dataSel, ore)
                }
            )
        },
        headlineContent = {
            Text(r.nome + (r.etichetta?.let { " · $it" } ?: ""),
                fontWeight = if (presente) FontWeight.SemiBold else FontWeight.Normal)
        },
        supportingContent = {
            val extra = if (r.oreFatte > r.oreMonte) " · +${oreFmt(r.oreFatte - r.oreMonte)}h extra" else ""
            Text("${oreFmt(r.oreFatte)}/${oreFmt(r.oreMonte)}h · $pct%$extra", fontSize = 12.sp)
        },
        trailingContent = {
            if (presente) TextButton(onClick = { modificaOre = true }) {
                Text("${oreFmt(r.oreSegnate ?: 0.0)}h")
            }
        }
    )
    HorizontalDivider()

    if (modificaOre && presente) {
        OreDialog(iniziale = r.oreSegnate ?: turno?.oreDefault ?: 0.0,
            onDismiss = { modificaOre = false }) { nuoveOre ->
            vm.segnaPresenza(r.percorsoId, turno!!.id, dataSel, nuoveOre)
            modificaOre = false
        }
    }
}

@Composable
private fun OreDialog(iniziale: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var ore by remember { mutableStateOf(oreFmt(iniziale)) }
    val v = ore.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ore presenza") },
        confirmButton = {
            TextButton(enabled = v != null && v >= 0, onClick = { onSave(v!!) }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        text = {
            OutlinedTextField(
                ore, { ore = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                label = { Text("Ore (0 = rimuovi presenza)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    )
}

@Composable
private fun AggiungiFuoriTurno(
    candidati: List<RigaPresenza>,
    onChiudi: () -> Unit,
    onScelto: (RigaPresenza, rendiAbituale: Boolean) -> Unit,
) {
    var scelto by remember { mutableStateOf<RigaPresenza?>(null) }
    if (scelto == null) {
        AlertDialog(
            onDismissRequest = onChiudi,
            title = { Text("Aggiungi allievo") },
            confirmButton = { TextButton(onClick = onChiudi) { Text("Annulla") } },
            text = {
                if (candidati.isEmpty()) Text("Nessun altro allievo disponibile.")
                else LazyColumn {
                    items(candidati, key = { it.percorsoId }) { r ->
                        ListItem(
                            modifier = Modifier.clickable { scelto = r },
                            headlineContent = { Text(r.nome + (r.etichetta?.let { " · $it" } ?: "")) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        )
    } else {
        val r = scelto!!
        AlertDialog(
            onDismissRequest = { scelto = null },
            title = { Text(r.nome) },
            text = { Text("Aggiungere questo turno ai suoi turni abituali?") },
            confirmButton = { TextButton(onClick = { onScelto(r, true) }) { Text("Sì, rendi abituale") } },
            dismissButton = { TextButton(onClick = { onScelto(r, false) }) { Text("No, solo oggi") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(dataIso: String, onResult: (String?) -> Unit) {
    val iniziale = LocalDate.parse(dataIso)
        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = iniziale)
    DatePickerDialog(
        onDismissRequest = { onResult(null) },
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                onResult(
                    if (ms == null) null
                    else java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate().toString()
                )
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text("Annulla") } }
    ) { DatePicker(state = state) }
}
