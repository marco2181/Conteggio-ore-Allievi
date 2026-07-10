package com.istitutiverona.conteggioore.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.istitutiverona.conteggioore.drive.Backup
import com.istitutiverona.conteggioore.drive.Drive
import com.istitutiverona.conteggioore.drive.ImportExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

private fun opzioniGoogle() = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestScopes(Scope(Drive.SCOPE)).build()

// Impostazioni backup: Google Drive, ripristino, import/export locale.
@Composable
fun BackupScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(ctx)) }
    var occupato by remember { mutableStateOf(false) }
    var listaDrive by remember { mutableStateOf<List<Drive.FileDrive>?>(null) }
    var confermaRipristino by remember { mutableStateOf<Drive.FileDrive?>(null) }
    var importFile by remember { mutableStateOf<Pair<File, ImportExport.TipoDb>?>(null) }
    var riavvioNecessario by remember { mutableStateOf(false) }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        // Parsare esplicitamente il risultato salva l'account per GoogleAuthUtil.
        runCatching { GoogleSignIn.getSignedInAccountFromIntent(res.data).getResult() }
            .onSuccess { account = it }
            .onFailure { toast("Login Google non riuscito: ${it.message ?: "errore sconosciuto"}") }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            runCatching {
                val snap = Backup.snapshot(ctx)
                ctx.contentResolver.openOutputStream(uri)!!.use { o -> snap.inputStream().copyTo(o) }
            }.onSuccess { withContext(Dispatchers.Main) { toast("Esportato") } }
                .onFailure { withContext(Dispatchers.Main) { toast("Errore: ${it.message}") } }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val tmp = File(ctx.cacheDir, "import.db")
            runCatching {
                ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                ImportExport.tipoDb(tmp)
            }.onSuccess { tipo ->
                withContext(Dispatchers.Main) {
                    if (tipo == ImportExport.TipoDb.SCONOSCIUTO) toast("File non riconosciuto")
                    else importFile = tmp to tipo
                }
            }.onFailure { withContext(Dispatchers.Main) { toast("Errore: ${it.message}") } }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Stato ──
        Text("Google Drive", fontWeight = FontWeight.Bold)
        Backup.ultimoErrore(ctx)?.let {
            Text("⚠ Ultimo backup fallito: $it", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Text(
            "Ultimo backup riuscito: ${Backup.ultimoOk(ctx) ?: "mai"}",
            fontSize = 13.sp
        )

        if (account == null) {
            Button(onClick = {
                loginLauncher.launch(GoogleSignIn.getClient(ctx, opzioniGoogle()).signInIntent)
            }) { Text("Connetti account Google") }
        } else {
            Text("Connesso: ${account?.email ?: ""}", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !occupato, onClick = {
                    occupato = true
                    scope.launch(Dispatchers.IO) {
                        runCatching { Backup.eseguiUpload(ctx) }
                            .onSuccess { withContext(Dispatchers.Main) { toast("Backup su Drive completato") } }
                            .onFailure { withContext(Dispatchers.Main) { toast("Errore: ${it.message}") } }
                        occupato = false
                    }
                }) { Text("Backup ora") }
                OutlinedButton(enabled = !occupato, onClick = {
                    occupato = true
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            val t = Drive.token(ctx)!!
                            Drive.lista(t, Drive.cartellaBackups(t))
                        }.onSuccess { withContext(Dispatchers.Main) { listaDrive = it } }
                            .onFailure { withContext(Dispatchers.Main) { toast("Errore: ${it.message}") } }
                        occupato = false
                    }
                }) { Text("Ripristina…") }
            }
            TextButton(onClick = {
                GoogleSignIn.getClient(ctx, opzioniGoogle()).signOut()
                account = null
            }) { Text("Disconnetti") }
        }

        HorizontalDivider()

        // ── Locale ──
        Text("Import / export locale", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch(Backup.nomeBackup()) }) { Text("Esporta DB") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Importa DB…") }
        }
        Text("L'import sostituisce tutti i dati attuali (viene fatto prima un backup locale).",
            fontSize = 12.sp)

        HorizontalDivider()

        // ── Sicurezza ──
        Text("Sicurezza", fontWeight = FontWeight.Bold)
        val blocco = com.istitutiverona.conteggioore.sicurezza.Blocco
        var bloccoOn by remember { mutableStateOf(blocco.attivo(ctx)) }
        val activity = ctx as androidx.fragment.app.FragmentActivity
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(bloccoOn, { voluto ->
                if (!blocco.disponibile(ctx)) {
                    toast("Nessuna biometria o PIN configurati sul dispositivo")
                } else if (voluto) {
                    blocco.imposta(ctx, true); bloccoOn = true
                } else {
                    // Disattivabile solo dopo conferma biometrica/PIN.
                    blocco.chiedi(activity, "Conferma per disattivare il blocco") { ok ->
                        if (ok) { blocco.imposta(ctx, false); bloccoOn = false }
                    }
                }
            })
            Spacer(Modifier.width(8.dp))
            Text("Richiedi biometria o PIN all'apertura")
        }

        if (occupato) LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    // ── Dialog: lista backup Drive ──
    listaDrive?.let { lista ->
        AlertDialog(
            onDismissRequest = { listaDrive = null },
            title = { Text("Backup su Drive") },
            confirmButton = { TextButton(onClick = { listaDrive = null }) { Text("Chiudi") } },
            text = {
                if (lista.isEmpty()) Text("Nessun backup trovato.")
                else LazyColumn {
                    items(lista, key = { it.id }) { f ->
                        ListItem(
                            modifier = Modifier.clickable { confermaRipristino = f; listaDrive = null },
                            headlineContent = { Text(f.nome, fontSize = 14.sp) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        )
    }

    // ── Dialog: conferma ripristino da Drive ──
    confermaRipristino?.let { f ->
        AlertDialog(
            onDismissRequest = { confermaRipristino = null },
            title = { Text("Ripristinare?") },
            text = { Text("${f.nome}\n\nI dati attuali verranno sostituiti (backup locale automatico prima).") },
            confirmButton = {
                TextButton(onClick = {
                    confermaRipristino = null
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            val t = Drive.token(ctx)!!
                            val tmp = File(ctx.cacheDir, "ripristino.db")
                            Drive.scarica(t, f.id, tmp)
                            ImportExport.importaAndroid(ctx, tmp)
                        }.onSuccess { withContext(Dispatchers.Main) { riavvioNecessario = true } }
                            .onFailure { withContext(Dispatchers.Main) { toast("Errore: ${it.message}") } }
                    }
                }) { Text("Ripristina") }
            },
            dismissButton = { TextButton(onClick = { confermaRipristino = null }) { Text("Annulla") } }
        )
    }

    // ── Dialog: conferma import locale ──
    importFile?.let { (file, tipo) ->
        var includiPresenze by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { importFile = null },
            title = { Text(if (tipo == ImportExport.TipoDb.WINDOWS) "Import da Windows" else "Import backup") },
            text = {
                Column {
                    Text("Cancella i dati attuali e li sostituisce (backup locale automatico prima).")
                    if (tipo == ImportExport.TipoDb.WINDOWS) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(includiPresenze, { includiPresenze = it })
                            Text("Includi presenze storiche")
                        }
                        Text("Gli allievi importati non avranno turni abituali (anagrafiche incomplete).",
                            fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    importFile = null
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            if (tipo == ImportExport.TipoDb.WINDOWS)
                                ImportExport.importaWindows(ctx, file, includiPresenze)
                            else ImportExport.importaAndroid(ctx, file)
                        }.onSuccess {
                            withContext(Dispatchers.Main) {
                                if (tipo == ImportExport.TipoDb.WINDOWS) toast("Import completato")
                                else riavvioNecessario = true
                            }
                        }.onFailure { withContext(Dispatchers.Main) { toast("Errore: ${it.message}") } }
                    }
                }) { Text("Cancella dati attuali e importa") }
            },
            dismissButton = { TextButton(onClick = { importFile = null }) { Text("Annulla") } }
        )
    }

    if (riavvioNecessario) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Riavvio necessario") },
            text = { Text("Il database è stato sostituito. L'app ora si chiude: riaprila per vedere i dati.") },
            confirmButton = { TextButton(onClick = { exitProcess(0) }) { Text("Chiudi app") } }
        )
    }
}
