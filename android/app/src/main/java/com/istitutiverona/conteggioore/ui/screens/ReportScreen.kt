package com.istitutiverona.conteggioore.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istitutiverona.conteggioore.pdf.PdfReport
import com.istitutiverona.conteggioore.ui.AppViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

// Report PDF: scheda individuale, registro mensile, report per corso.
@Composable
fun ReportScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val persone by vm.persone.collectAsState()
    val corsi by vm.corsiAttivi.collectAsState()

    var pdfPronto by remember { mutableStateOf<File?>(null) }
    var scegliPersona by remember { mutableStateOf(false) }
    var scegliMese by remember { mutableStateOf(false) }
    var scegliCorso by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Report PDF", fontWeight = FontWeight.Bold, fontSize = 18.sp)

        CardReport("Scheda individuale", "Presenze e totali di un allievo") { scegliPersona = true }
        CardReport("Registro mensile", "Tutti gli allievi attivi in un mese") { scegliMese = true }
        CardReport("Report per corso", "Avanzamento allievi di un corso") { scegliCorso = true }
    }

    if (scegliPersona) {
        val conPercorso = persone.filter { it.percorsoId != null }
        AlertDialog(
            onDismissRequest = { scegliPersona = false },
            title = { Text("Scegli allievo") },
            confirmButton = { TextButton(onClick = { scegliPersona = false }) { Text("Annulla") } },
            text = {
                if (conPercorso.isEmpty()) Text("Nessun allievo con percorso attivo.")
                else LazyColumn {
                    items(conPercorso, key = { it.persona.id }) { p ->
                        ListItem(
                            modifier = Modifier.clickable {
                                scegliPersona = false
                                scope.launch { pdfPronto = vm.pdfSchedaIndividuale(p) }
                            },
                            headlineContent = {
                                Text(p.persona.nome + (p.persona.etichetta?.let { " · $it" } ?: ""))
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        )
    }

    if (scegliMese) {
        MeseDialog(onDismiss = { scegliMese = false }) { anno, mese ->
            scegliMese = false
            scope.launch { pdfPronto = vm.pdfRegistroMensile(anno, mese) }
        }
    }

    if (scegliCorso) {
        AlertDialog(
            onDismissRequest = { scegliCorso = false },
            title = { Text("Scegli corso") },
            confirmButton = { TextButton(onClick = { scegliCorso = false }) { Text("Annulla") } },
            text = {
                if (corsi.isEmpty()) Text("Nessun corso attivo.")
                else LazyColumn {
                    items(corsi, key = { it.id }) { c ->
                        ListItem(
                            modifier = Modifier.clickable {
                                scegliCorso = false
                                scope.launch { pdfPronto = vm.pdfReportCorso(c) }
                            },
                            headlineContent = { Text(c.nome) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        )
    }

    pdfPronto?.let { f ->
        AlertDialog(
            onDismissRequest = { pdfPronto = null },
            title = { Text("PDF pronto") },
            text = { Text(f.name) },
            confirmButton = {
                Column {
                    TextButton(onClick = { PdfReport.condividi(ctx, f); pdfPronto = null }) { Text("Condividi") }
                    TextButton(onClick = { PdfReport.stampa(ctx, f); pdfPronto = null }) { Text("Stampa") }
                    TextButton(onClick = {
                        val ok = PdfReport.salvaInDownload(ctx, f)
                        Toast.makeText(ctx, if (ok) "Salvato in Download" else "Errore salvataggio", Toast.LENGTH_SHORT).show()
                        pdfPronto = null
                    }) { Text("Salva in Download") }
                }
            },
            dismissButton = { TextButton(onClick = { pdfPronto = null }) { Text("Chiudi") } }
        )
    }
}

@Composable
private fun CardReport(titolo: String, sotto: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(titolo, fontWeight = FontWeight.SemiBold)
            Text(sotto, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MeseDialog(onDismiss: () -> Unit, onScelto: (anno: Int, mese: Int) -> Unit) {
    val oggi = LocalDate.now()
    var anno by remember { mutableStateOf(oggi.year) }
    var mese by remember { mutableStateOf(oggi.monthValue) }
    val mesi = listOf("Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registro mensile") },
        confirmButton = { TextButton(onClick = { onScelto(anno, mese) }) { Text("Genera") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        text = {
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(onClick = { anno-- }) { Text("−") }
                    Text(anno.toString(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                    TextButton(onClick = { anno++ }) { Text("+") }
                }
                LazyColumn(Modifier.height(240.dp)) {
                    items((1..12).toList()) { m ->
                        ListItem(
                            modifier = Modifier.clickable { mese = m },
                            headlineContent = {
                                Text(mesi[m - 1],
                                    fontWeight = if (mese == m) FontWeight.Bold else FontWeight.Normal)
                            },
                            trailingContent = { if (mese == m) Text("✓") }
                        )
                    }
                }
            }
        }
    )
}
