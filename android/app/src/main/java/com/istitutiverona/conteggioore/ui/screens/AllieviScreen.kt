package com.istitutiverona.conteggioore.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istitutiverona.conteggioore.data.Allievo
import com.istitutiverona.conteggioore.ui.AppViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllieviScreen(vm: AppViewModel) {
    val allievi by vm.allievi.collectAsState()
    var editing by remember { mutableStateOf<Allievo?>(null) }
    var query by remember { mutableStateOf("") }

    val filtrati = allievi.filter {
        query.isBlank() ||
            it.nome.contains(query, true) ||
            (it.etichetta?.contains(query, true) == true)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = Allievo(nome = "", dataInizio = LocalDate.now().toString())
            }) { Icon(Icons.Default.Add, contentDescription = "Nuovo allievo") }
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
                items(filtrati, key = { it.id }) { a ->
                    ListItem(
                        headlineContent = { Text(a.nome, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Column {
                                a.etichetta?.let { Text(it, fontSize = 12.sp) }
                                Text(vm.nomeCorso(a.corsoId), fontSize = 12.sp)
                            }
                        },
                        trailingContent = { TextButton(onClick = { editing = a }) { Text("Modifica") } }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    editing?.let { a ->
        AllievoDialog(a, vm, onDismiss = { editing = null }, onSave = { vm.salvaAllievo(it); editing = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllievoDialog(
    allievo: Allievo,
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onSave: (Allievo) -> Unit,
) {
    val corsi by vm.corsiAttivi.collectAsState()
    var nome by remember { mutableStateOf(allievo.nome) }
    var etichetta by remember { mutableStateOf(allievo.etichetta ?: "") }
    var corsoId by remember { mutableStateOf(allievo.corsoId) }
    var oreRimanenti by remember { mutableStateOf(allievo.oreRimanentiIniziali?.toInt()?.toString() ?: "") }
    var omonimo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(nome) {
        omonimo = nome.isNotBlank() && vm.omonimi(nome.trim(), allievo.id) > 0
    }

    val etichettaObbligatoria = omonimo && etichetta.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (allievo.id == 0L) "Nuovo allievo" else "Modifica allievo") },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank() && !etichettaObbligatoria,
                onClick = {
                    onSave(
                        allievo.copy(
                            nome = nome.trim(),
                            etichetta = etichetta.trim().ifBlank { null },
                            corsoId = corsoId,
                            oreRimanentiIniziali = if (corsoId == null) oreRimanenti.toDoubleOrNull() else null,
                        )
                    )
                }
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        text = {
            Column {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome e cognome") }, singleLine = true)
                if (omonimo) {
                    Text(
                        "Esiste già un allievo con questo nome: aggiungi un'etichetta.",
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp
                    )
                    OutlinedTextField(etichetta, { etichetta = it }, label = { Text("Etichetta (interna)") }, singleLine = true)
                }
                Spacer(Modifier.height(8.dp))

                var espanso by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = espanso, onExpandedChange = { espanso = it }) {
                    OutlinedTextField(
                        value = if (corsoId == null) "Ore individuali" else (corsi.firstOrNull { it.id == corsoId }?.nome ?: "—"),
                        onValueChange = {}, readOnly = true, label = { Text("Corso") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = espanso, onDismissRequest = { espanso = false }) {
                        DropdownMenuItem(text = { Text("Ore individuali") }, onClick = { corsoId = null; espanso = false })
                        corsi.forEach { c ->
                            DropdownMenuItem(text = { Text("${c.nome} (${c.ore.toInt()}h)") }, onClick = { corsoId = c.id; espanso = false })
                        }
                    }
                }

                if (corsoId == null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        oreRimanenti, { oreRimanenti = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Ore rimanenti iniziali") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }
    )
}
