package com.istitutiverona.conteggioore.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istitutiverona.conteggioore.data.*
import com.istitutiverona.conteggioore.ui.AppViewModel
import com.istitutiverona.conteggioore.ui.etichettaTurno
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllieviScreen(vm: AppViewModel) {
    val persone by vm.persone.collectAsState()
    var query by remember { mutableStateOf("") }
    var creando by remember { mutableStateOf(false) }
    var scheda by remember { mutableStateOf<PersonaConPercorso?>(null) }

    val filtrate = persone.filter {
        query.isBlank() ||
            it.persona.nome.contains(query, true) ||
            (it.persona.etichetta?.contains(query, true) == true)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { creando = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nuovo allievo")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                query, { query = it },
                label = { Text("Cerca allievo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtrate, key = { it.persona.id }) { pp ->
                    ListItem(
                        modifier = Modifier.clickable { scheda = pp },
                        headlineContent = { Text(pp.persona.nome, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Column {
                                pp.persona.etichetta?.let { Text(it, fontSize = 12.sp) }
                                Text(
                                    if (pp.corsoId == null && pp.oreMonte != null) "Ore individuali"
                                    else pp.nomeCorso ?: "Nessun percorso attivo",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (creando) {
        PersonaWizard(vm, onDismiss = { creando = false })
    }
    scheda?.let { SchedaPersona(vm, it, onDismiss = { scheda = null }) }
}

/** Creazione nuova persona: nome, corso/ore, data, turni abituali. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaWizard(vm: AppViewModel, onDismiss: () -> Unit) {
    val corsi by vm.corsiAttivi.collectAsState()
    val turni by vm.turni.collectAsState()

    var nome by remember { mutableStateOf("") }
    var etichetta by remember { mutableStateOf("") }
    var corsoId by remember { mutableStateOf<Long?>(null) }
    var ore by remember { mutableStateOf("") }              // ore rimanenti iniziali (se individuale)
    var omonimo by remember { mutableStateOf(false) }
    val turniSel = remember { mutableStateListOf<Long>() }

    LaunchedEffect(nome) { omonimo = nome.isNotBlank() && vm.omonimi(nome.trim(), 0) > 0 }

    val corso = corsi.firstOrNull { it.id == corsoId }
    val monte = corso?.ore ?: ore.toDoubleOrNull()
    val etichettaObbligatoria = omonimo && etichetta.isBlank()
    val valido = nome.isNotBlank() && monte != null && monte > 0 && !etichettaObbligatoria

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuovo allievo") },
        confirmButton = {
            TextButton(enabled = valido, onClick = {
                val persona = Persona(nome = nome.trim(), etichetta = etichetta.trim().ifBlank { null })
                val giaAvviato = corsoId == null && ore.toDoubleOrNull() != null
                val percorso = Percorso(
                    personaId = 0,
                    corsoId = corsoId,
                    oreMonte = monte!!,
                    giaAvviato = giaAvviato,
                    note = if (giaAvviato)
                        "Percorso già avviato, ore residue iniziali ${ore.trim()}" else null,
                    dataInizio = LocalDate.now().toString(),
                )
                vm.creaPersona(persona, percorso, turniSel.toList())
                onDismiss()
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome e cognome") }, singleLine = true)
                if (omonimo) {
                    Text("Nome duplicato: aggiungi un'etichetta.",
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    OutlinedTextField(etichetta, { etichetta = it }, label = { Text("Etichetta (interna)") }, singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                SelettoreCorso(corsi, corsoId) { corsoId = it }
                if (corsoId == null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        ore, { ore = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                        label = { Text("Ore rimanenti iniziali") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Turni abituali", fontWeight = FontWeight.SemiBold)
                if (turniSel.isEmpty())
                    Text("Nessun turno selezionato — verrà segnalato come incompleto.",
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                turni.forEach { t ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = turniSel.contains(t.id),
                            onCheckedChange = { on -> if (on) turniSel.add(t.id) else turniSel.remove(t.id) }
                        )
                        Text(etichettaTurno(t))
                    }
                }
            }
        }
    )
}

/** Scheda persona: modifica anagrafica/turni + Nuovo corso + storico. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedaPersona(vm: AppViewModel, pp: PersonaConPercorso, onDismiss: () -> Unit) {
    val corsi by vm.corsiAttivi.collectAsState()
    val turni by vm.turni.collectAsState()

    var nome by remember { mutableStateOf(pp.persona.nome) }
    var etichetta by remember { mutableStateOf(pp.persona.etichetta ?: "") }
    val turniSel = remember { mutableStateListOf<Long>() }
    var caricato by remember { mutableStateOf(false) }
    var nuovoCorso by remember { mutableStateOf(false) }
    var confermaElimina by remember { mutableStateOf(false) }

    LaunchedEffect(pp.persona.id) {
        turniSel.clear(); turniSel.addAll(vm.turniAbitualiDi(pp.persona.id)); caricato = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pp.persona.nome) },
        confirmButton = {
            TextButton(enabled = nome.isNotBlank(), onClick = {
                vm.aggiornaPersona(
                    pp.persona.copy(nome = nome.trim(), etichetta = etichetta.trim().ifBlank { null }),
                    turniSel.toList()
                )
                onDismiss()
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Percorso attivo: " +
                    (if (pp.corsoId == null && pp.oreMonte != null) "Ore individuali (${pp.oreMonte.toInt()}h)"
                     else pp.nomeCorso ?: "nessuno"))
                if (pp.giaAvviato == true) Text("• percorso già avviato", fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome e cognome") }, singleLine = true)
                OutlinedTextField(etichetta, { etichetta = it }, label = { Text("Etichetta (interna)") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Text("Turni abituali", fontWeight = FontWeight.SemiBold)
                turni.forEach { t ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = turniSel.contains(t.id),
                            onCheckedChange = { on -> if (on) turniSel.add(t.id) else turniSel.remove(t.id) }
                        )
                        Text(etichettaTurno(t))
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { nuovoCorso = true }) { Text("Nuovo corso") }
                TextButton(onClick = { confermaElimina = true }) {
                    Text("Elimina allievo", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )

    if (confermaElimina) {
        AlertDialog(
            onDismissRequest = { confermaElimina = false },
            title = { Text("Eliminare ${pp.persona.nome}?") },
            text = { Text("Verranno eliminati definitivamente anche percorsi e presenze. L'operazione non si può annullare.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.eliminaPersona(pp.persona.id)
                    confermaElimina = false
                    onDismiss()
                }) { Text("Elimina definitivamente", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confermaElimina = false }) { Text("Annulla") } }
        )
    }

    if (nuovoCorso) {
        NuovoCorsoDialog(corsi, onDismiss = { nuovoCorso = false }) { corsoId, oreInd ->
            val monte = corsi.firstOrNull { it.id == corsoId }?.ore ?: oreInd!!
            vm.nuovoCorso(pp.persona.id, Percorso(
                personaId = pp.persona.id,
                corsoId = corsoId,
                oreMonte = monte,
                dataInizio = LocalDate.now().toString(),
            ))
            nuovoCorso = false; onDismiss()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NuovoCorsoDialog(
    corsi: List<Corso>,
    onDismiss: () -> Unit,
    onConfirm: (corsoId: Long?, oreIndividuali: Double?) -> Unit,
) {
    var corsoId by remember { mutableStateOf<Long?>(null) }
    var ore by remember { mutableStateOf("") }
    val valido = corsoId != null || ore.toDoubleOrNull()?.let { it > 0 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuovo corso") },
        confirmButton = {
            TextButton(enabled = valido, onClick = {
                onConfirm(corsoId, if (corsoId == null) ore.toDoubleOrNull() else null)
            }) { Text("Conferma") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        text = {
            Column {
                Text("Il percorso attuale verrà archiviato.", fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                SelettoreCorso(corsi, corsoId) { corsoId = it }
                if (corsoId == null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        ore, { ore = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                        label = { Text("Ore individuali") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelettoreCorso(corsi: List<Corso>, corsoId: Long?, onSel: (Long?) -> Unit) {
    var espanso by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = espanso, onExpandedChange = { espanso = it }) {
        OutlinedTextField(
            value = if (corsoId == null) "Ore individuali"
                    else (corsi.firstOrNull { it.id == corsoId }?.nome ?: "—"),
            onValueChange = {}, readOnly = true, label = { Text("Corso") },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = espanso, onDismissRequest = { espanso = false }) {
            DropdownMenuItem(text = { Text("Ore individuali") }, onClick = { onSel(null); espanso = false })
            corsi.forEach { c ->
                DropdownMenuItem(text = { Text("${c.nome} (${c.ore.toInt()}h)") },
                    onClick = { onSel(c.id); espanso = false })
            }
        }
    }
}
