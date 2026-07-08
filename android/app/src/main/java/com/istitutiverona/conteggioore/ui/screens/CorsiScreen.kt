package com.istitutiverona.conteggioore.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.istitutiverona.conteggioore.data.Corso
import com.istitutiverona.conteggioore.ui.AppViewModel
import kotlinx.coroutines.launch

private val PRESET = listOf(20.0, 90.0, 150.0, 300.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorsiScreen(vm: AppViewModel) {
    val corsi by vm.corsi.collectAsState()
    var editing by remember { mutableStateOf<Corso?>(null) }
    var errore by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Corso(nome = "", ore = 90.0) }) {
                Icon(Icons.Default.Add, contentDescription = "Nuovo corso")
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(corsi, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.nome + if (!c.attivo) "  (archiviato)" else "") },
                    supportingContent = { Text("${c.ore.toInt()} h") },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { editing = c }) { Text("Modifica") }
                            TextButton(onClick = { vm.archiviaCorso(c) }) {
                                Text(if (c.attivo) "Archivia" else "Ripristina")
                            }
                            if (!c.attivo) TextButton(onClick = {
                                scope.launch {
                                    if (!vm.eliminaCorsoSePossibile(c))
                                        errore = "Impossibile eliminare: il corso è usato da un percorso."
                                }
                            }) { Text("Elimina") }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { corso ->
        CorsoDialog(
            corso = corso,
            onDismiss = { editing = null },
            onSave = { vm.salvaCorso(it); editing = null }
        )
    }
    errore?.let { msg ->
        AlertDialog(
            onDismissRequest = { errore = null },
            confirmButton = { TextButton(onClick = { errore = null }) { Text("OK") } },
            text = { Text(msg) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorsoDialog(corso: Corso, onDismiss: () -> Unit, onSave: (Corso) -> Unit) {
    var nome by remember { mutableStateOf(corso.nome) }
    var ore by remember { mutableStateOf(corso.ore.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (corso.id == 0L) "Nuovo corso" else "Modifica corso") },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank() && (ore.toDoubleOrNull() ?: 0.0) > 0,
                onClick = { onSave(corso.copy(nome = nome.trim(), ore = ore.toDouble())) }
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        text = {
            Column {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome corso") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    ore, { ore = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Ore") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    PRESET.forEach { p ->
                        AssistChip(
                            onClick = { ore = p.toInt().toString() },
                            label = { Text(p.toInt().toString()) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }
        }
    )
}
