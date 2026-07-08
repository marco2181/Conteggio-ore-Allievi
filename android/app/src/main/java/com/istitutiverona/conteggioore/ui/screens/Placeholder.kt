package com.istitutiverona.conteggioore.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// ponytail: schermate delle fasi successive. Riempite in Fase 2/3.
@Composable
fun PlaceholderScreen(nome: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$nome — in arrivo")
    }
}
