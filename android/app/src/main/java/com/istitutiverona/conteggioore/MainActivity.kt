package com.istitutiverona.conteggioore

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.istitutiverona.conteggioore.ui.AppViewModel
import com.istitutiverona.conteggioore.ui.screens.AllieviScreen
import com.istitutiverona.conteggioore.ui.screens.AltroScreen
import com.istitutiverona.conteggioore.ui.screens.DashboardScreen
import com.istitutiverona.conteggioore.ui.screens.PresenzeScreen
import com.istitutiverona.conteggioore.ui.screens.ReportScreen
import com.istitutiverona.conteggioore.ui.theme.ConteggioOreTheme

enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Filled.Dashboard),
    Presenze("Presenze", Icons.Filled.CalendarMonth),
    Allievi("Allievi", Icons.Filled.People),
    Report("Report", Icons.Filled.PictureAsPdf),
    Altro("Altro", Icons.Filled.MoreHoriz),
}

class MainActivity : FragmentActivity() {

    // Sbloccato per questa sessione? Torna false quando l'app va in background.
    private val sbloccato = androidx.compose.runtime.mutableStateOf(false)

    override fun onStop() {
        super.onStop()
        // Tenta il backup subito quando l'app va in background, senza bloccare.
        com.istitutiverona.conteggioore.drive.Backup.tentaOra(this)
        sbloccato.value = false   // ri-chiedi biometria alla prossima apertura
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ConteggioOreTheme {
                val blocco = com.istitutiverona.conteggioore.sicurezza.Blocco
                if (blocco.attivo(this) && blocco.disponibile(this) && !sbloccato.value) {
                    com.istitutiverona.conteggioore.ui.screens.SchermataBlocco(
                        onSblocca = { sbloccato.value = true }
                    )
                    return@ConteggioOreTheme
                }

                val vm: AppViewModel = viewModel()
                var tab by remember { mutableStateOf(Tab.Dashboard) }
                com.istitutiverona.conteggioore.ui.screens.WizardIniziale()

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Image(
                                    painter = painterResource(R.drawable.logo_istituti_verona),
                                    contentDescription = "Istituti Verona Moda & Design",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.width(180.dp).height(48.dp),
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Black,
                                titleContentColor = Color.White,
                            ),
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = Color.Black) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = Color.White,
                                        indicatorColor = Color(0xFFFF4534),
                                        unselectedIconColor = Color(0xFFBDBDBD),
                                        unselectedTextColor = Color(0xFFBDBDBD),
                                    ),
                                )
                            }
                        }
                    }
                ) { pad ->
                    Surface(Modifier.padding(pad)) {
                        when (tab) {
                            Tab.Dashboard -> DashboardScreen(vm)
                            Tab.Presenze -> PresenzeScreen(vm)
                            Tab.Allievi -> AllieviScreen(vm)
                            Tab.Report -> ReportScreen(vm)
                            Tab.Altro -> AltroScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
