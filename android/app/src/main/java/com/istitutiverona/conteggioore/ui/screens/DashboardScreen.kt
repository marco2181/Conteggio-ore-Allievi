package com.istitutiverona.conteggioore.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istitutiverona.conteggioore.data.RigaDashboard
import com.istitutiverona.conteggioore.ui.AppViewModel
import com.istitutiverona.conteggioore.ui.oreFmt

private fun coloreProgresso(pct: Int): Color = when {
    pct >= 100 -> Color(0xFF27AE60)
    pct >= 80 -> Color(0xFFE67E22)
    else -> Color(0xFF3498DB)
}

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val righe by vm.dashboard.collectAsState()
    val assenti by vm.assentiSettScorsa.collectAsState()

    val attivi = righe.size
    val completati = righe.count { it.oreMonte > 0 && it.oreFatte >= it.oreMonte }
    val quasi = righe.count {
        val p = if (it.oreMonte > 0) it.oreFatte / it.oreMonte * 100 else 0.0
        p in 80.0..99.999
    }
    val incompleti = righe.filter { it.nTurni == 0 }

    LazyColumn(Modifier.fillMaxSize()) {
        // Card riassuntive compatte
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardMini("Attivi", attivi.toString(), Modifier.weight(1f))
                CardMini("Quasi", quasi.toString(), Modifier.weight(1f))
                CardMini("Completati", completati.toString(), Modifier.weight(1f))
            }
        }

        // Avvisi
        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            if (com.istitutiverona.conteggioore.drive.Backup.problema(ctx)) {
                Avviso(
                    "Backup Drive non riuscito",
                    "Dettagli in Altro → Backup. Ultimo riuscito: " +
                        (com.istitutiverona.conteggioore.drive.Backup.ultimoOk(ctx) ?: "mai"),
                    Color(0xFFFDECEA), Color(0xFFC0392B)
                )
            }
        }
        if (assenti.isNotEmpty()) {
            item {
                Avviso(
                    "Assenti settimana scorsa (${assenti.size})",
                    assenti.joinToString(", ") { it.nome + (it.etichetta?.let { e -> " ($e)" } ?: "") },
                    Color(0xFFFDECEA), Color(0xFFC0392B)
                )
            }
        }
        if (incompleti.isNotEmpty()) {
            item {
                Avviso(
                    "Anagrafiche incomplete (${incompleti.size})",
                    incompleti.joinToString(", ") { it.nome } + " — turni abituali mancanti",
                    Color(0xFFFEF5E7), Color(0xFFB9770E)
                )
            }
        }

        item { Text("Allievi", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp)) }
        items(righe, key = { it.personaId }) { r -> RigaDash(r) }
    }
}

@Composable
private fun CardMini(label: String, valore: String, mod: Modifier) {
    Card(mod) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valore, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Avviso(titolo: String, corpo: String, sfondo: Color, testo: Color) {
    Column(
        Modifier.fillMaxWidth().padding(12.dp, 4.dp)
            .clip(RoundedCornerShape(8.dp)).background(sfondo).padding(12.dp)
    ) {
        Text(titolo, fontWeight = FontWeight.Bold, color = testo)
        Text(corpo, fontSize = 13.sp, color = testo)
    }
}

@Composable
private fun RigaDash(r: RigaDashboard) {
    val pct = if (r.oreMonte > 0) (r.oreFatte / r.oreMonte * 100).toInt() else 0
    val rimanenti = (r.oreMonte - r.oreFatte).coerceAtLeast(0.0)
    val extra = if (r.oreFatte > r.oreMonte) r.oreFatte - r.oreMonte else 0.0
    ListItem(
        headlineContent = { Text(r.nome + (r.etichetta?.let { " · $it" } ?: ""), fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Column {
                Text(
                    (if (r.corsoId == null) "Ore individuali" else r.nomeCorso ?: "—") +
                        " · ${oreFmt(r.oreFatte)}/${oreFmt(r.oreMonte)}h" +
                        (if (extra > 0) " · Completato +${oreFmt(extra)}h extra"
                         else " · ${oreFmt(rimanenti)}h rimanenti"),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                    color = coloreProgresso(pct),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        trailingContent = { Text("$pct%", color = coloreProgresso(pct), fontWeight = FontWeight.Bold) }
    )
    HorizontalDivider()
}
