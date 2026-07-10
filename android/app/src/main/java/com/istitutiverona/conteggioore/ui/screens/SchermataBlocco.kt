package com.istitutiverona.conteggioore.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.istitutiverona.conteggioore.R
import com.istitutiverona.conteggioore.sicurezza.Blocco

// Prompt automatico all'apertura; nessun pulsante intermedio.
@Composable
fun SchermataBlocco(onSblocca: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity

    fun chiedi() = Blocco.chiedi(activity, "Sblocca Conteggio Ore") { ok ->
        if (ok) onSblocca()
    }

    LaunchedEffect(Unit) { chiedi() }

    val pulse by rememberInfiniteTransition(label = "impronta")
        .animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulse",
        )

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color.Black, Color(0xFF1F2124), Color.Black)
            )
        )
    ) {
        // Bagliore rosso discreto dietro al lettore.
        Box(
            Modifier.align(Alignment.Center)
                .size(230.dp).scale(pulse).alpha(0.16f)
                .background(Color(0xFFFF4534), CircleShape)
        )

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_istituti_verona),
                contentDescription = "Istituti Verona Moda & Design",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(235.dp).height(130.dp),
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(142.dp).scale(pulse)
                        .background(Color(0xFFFF4534), CircleShape)
                        .clickable(onClick = { chiedi() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Fingerprint,
                        contentDescription = "Riprova sblocco con impronta",
                        tint = Color.White,
                        modifier = Modifier.size(88.dp),
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "Sblocca con impronta o PIN",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Autenticazione richiesta automaticamente",
                    color = Color(0xFFBDBDBD),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                "CONTEGGIO ORE ALLIEVI",
                color = Color(0xFF8D8D8D),
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.height(70.dp),
            )
        }
    }
}
