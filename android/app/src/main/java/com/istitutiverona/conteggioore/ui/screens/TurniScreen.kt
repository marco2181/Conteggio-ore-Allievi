package com.istitutiverona.conteggioore.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.istitutiverona.conteggioore.data.Turno
import com.istitutiverona.conteggioore.ui.AppViewModel
import com.istitutiverona.conteggioore.ui.GIORNI
import com.istitutiverona.conteggioore.ui.giornoNome
import com.istitutiverona.conteggioore.ui.oreFmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurniScreen(vm: AppViewModel) {
    val turni by vm.turni.collectAsState()
    var editing by remember { mutableStateOf<Turno?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Turno(giorno = 0, fascia = "mattina", oreDefault = 3.0) }) {
                Icon(Icons.Default.Add, contentDescription = "Nuovo turno")
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(turni, key = { it.id }) { t ->
                ListItem(
                    headlineContent = { Text("${giornoNome(t.giorno)} — ${t.fascia}") },
                    supportingContent = { Text("${oreFmt(t.oreDefault)} h") },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { editing = t }) { Text("Modifica") }
                            TextButton(onClick = { vm.eliminaTurno(t) }) { Text("Elimina") }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { t ->
        TurnoDialog(t, onDismiss = { editing = null }, onSave = { vm.salvaTurno(it); editing = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TurnoDialog(turno: Turno, onDismiss: () -> Unit, onSave: (Turno) -> Unit) {
    var giorno by remember { mutableStateOf(turno.giorno) }
    var fascia by remember { mutableStateOf(turno.fascia) }
    var ore by remember { mutableStateOf(oreFmt(turno.oreDefault)) }
    val valido = ore.replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (turno.id == 0L) "Nuovo turno" else "Modifica turno") },
        confirmButton = {
            TextButton(enabled = valido, onClick = {
                onSave(turno.copy(giorno = giorno, fascia = fascia, oreDefault = ore.replace(',', '.').toDouble()))
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        text = {
            Column {
                DropdownSelect("Giorno", GIORNI[giorno], GIORNI.dropLast(1)) { giorno = it }
                Spacer(Modifier.height(8.dp))
                DropdownSelect("Fascia", fascia, listOf("mattina", "pomeriggio", "sera")) { fascia = listOf("mattina","pomeriggio","sera")[it] }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    ore, { ore = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text("Ore") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelect(label: String, valore: String, opzioni: List<String>, onSel: (Int) -> Unit) {
    var espanso by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = espanso, onExpandedChange = { espanso = it }) {
        OutlinedTextField(
            value = valore, onValueChange = {}, readOnly = true, label = { Text(label) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = espanso, onDismissRequest = { espanso = false }) {
            opzioni.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSel(i); espanso = false })
            }
        }
    }
}
