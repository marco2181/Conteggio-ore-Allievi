package com.istitutiverona.conteggioore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.istitutiverona.conteggioore.ui.AppViewModel
import com.istitutiverona.conteggioore.ui.screens.AllieviScreen
import com.istitutiverona.conteggioore.ui.screens.AltroScreen
import com.istitutiverona.conteggioore.ui.screens.DashboardScreen
import com.istitutiverona.conteggioore.ui.screens.PlaceholderScreen
import com.istitutiverona.conteggioore.ui.screens.PresenzeScreen
import com.istitutiverona.conteggioore.ui.theme.ConteggioOreTheme

enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Filled.Dashboard),
    Presenze("Presenze", Icons.Filled.CalendarMonth),
    Allievi("Allievi", Icons.Filled.People),
    Report("Report", Icons.Filled.PictureAsPdf),
    Altro("Altro", Icons.Filled.MoreHoriz),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ConteggioOreTheme {
                val vm: AppViewModel = viewModel()
                var tab by remember { mutableStateOf(Tab.Dashboard) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) }
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
                            Tab.Report -> PlaceholderScreen("Report")
                            Tab.Altro -> AltroScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
