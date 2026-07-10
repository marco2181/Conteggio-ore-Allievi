package com.istitutiverona.conteggioore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// Identità visiva di istitutiverona.com: nero, bianco e rosso corallo #FF4534.
private val Rosso = Color(0xFFFF4534)
private val Nero = Color(0xFF000000)
private val QuasiNero = Color(0xFF1F2124)
private val Sfondo = Color(0xFFF5F5F5)

private val Light = lightColorScheme(
    primary = Rosso,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Nero,
    onSecondary = Color.White,
    background = Sfondo,
    onBackground = QuasiNero,
    surface = Color.White,
    onSurface = QuasiNero,
    surfaceVariant = Color(0xFFECECEC),
    outline = Color(0xFF69727D),
    error = Color(0xFFB12A26),
)

private val Dark = darkColorScheme(
    primary = Rosso,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8D160D),
    secondary = Color.White,
    onSecondary = Nero,
    background = Nero,
    onBackground = Color.White,
    surface = QuasiNero,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF303236),
    outline = Color(0xFF9A9A9A),
)

private val BrandShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
)

@Composable
fun ConteggioOreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(),
        shapes = BrandShapes,
        content = content,
    )
}
