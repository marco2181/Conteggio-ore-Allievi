package com.istitutiverona.conteggioore.ui.screens

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.istitutiverona.conteggioore.drive.Drive
import java.time.LocalDate

// Wizard primo avvio: Google login (saltabile) + data inizio calcolo assenze.
@Composable
fun WizardIniziale() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE)
    var mostra by remember { mutableStateOf(!prefs.getBoolean("wizard_fatto", false)) }
    if (!mostra) return

    var connesso by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(ctx) != null) }
    var dataInizio by remember { mutableStateOf(LocalDate.now().toString()) }

    val login = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        connesso = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(res.data).getResult()
        }.isSuccess
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Benvenuto") },
        text = {
            Column {
                Text("Backup su Google Drive (facoltativo):")
                Spacer(Modifier.height(8.dp))
                if (connesso) Text("✓ Account connesso", fontSize = 13.sp)
                else OutlinedButton(onClick = {
                    val opts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestScopes(Scope(Drive.SCOPE)).build()
                    login.launch(GoogleSignIn.getClient(ctx, opts).signInIntent)
                }) { Text("Connetti Google") }
                Spacer(Modifier.height(16.dp))
                Text("Data inizio calcolo assenze settimanali:")
                OutlinedTextField(
                    dataInizio, { dataInizio = it },
                    label = { Text("AAAA-MM-GG") }, singleLine = true
                )
                Text("Le settimane precedenti non genereranno avvisi di assenza.", fontSize = 12.sp)
            }
        },
        confirmButton = {
            val valida = runCatching { LocalDate.parse(dataInizio) }.isSuccess
            TextButton(enabled = valida, onClick = {
                prefs.edit().putBoolean("wizard_fatto", true)
                    .putString("inizio_assenze", dataInizio).apply()
                mostra = false
            }) { Text("Inizia") }
        }
    )
}
