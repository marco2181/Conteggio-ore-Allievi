package com.istitutiverona.conteggioore.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ponytail: palette fissa vicina a istitutiverona.com. Material You dinamico si aggiunge se serve.
private val Brand = Color(0xFF2C3E50)
private val BrandDark = Color(0xFF1B2733)

private val Light = lightColorScheme(primary = Brand, secondary = Brand)
private val Dark = darkColorScheme(primary = Color(0xFF8AA6C1), secondary = Color(0xFF8AA6C1))

@Composable
fun ConteggioOreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
