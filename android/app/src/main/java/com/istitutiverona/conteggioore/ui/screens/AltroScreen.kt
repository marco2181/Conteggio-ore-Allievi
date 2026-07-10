package com.istitutiverona.conteggioore.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.istitutiverona.conteggioore.ui.AppViewModel

// "Altro": Corsi, Turni e Backup, meno usati della nav principale.
@Composable
fun AltroScreen(vm: AppViewModel) {
    var sub by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = sub) {
            Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("Corsi") })
            Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("Turni") })
            Tab(selected = sub == 2, onClick = { sub = 2 }, text = { Text("Backup") })
        }
        when (sub) {
            0 -> CorsiScreen(vm)
            1 -> TurniScreen(vm)
            else -> BackupScreen()
        }
    }
}
