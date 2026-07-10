package com.istitutiverona.conteggioore.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.istitutiverona.conteggioore.sicurezza.Blocco

// Schermo di blocco: prompt biometria/PIN automatico + pulsante retry.
@Composable
fun SchermataBlocco(onSblocca: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity

    fun chiedi() = Blocco.chiedi(activity, "Sblocca Conteggio Ore") { ok ->
        if (ok) onSblocca()
    }

    LaunchedEffect(Unit) { chiedi() }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null)
        Spacer(Modifier.height(16.dp))
        Text("App bloccata")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { chiedi() }) { Text("Sblocca") }
    }
}
