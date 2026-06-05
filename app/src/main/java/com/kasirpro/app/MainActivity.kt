package com.kasirpro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kasirpro.app.ui.theme.MyApplicationTheme
import com.kasirpro.app.ui.theme.OrangePrimary
import com.kasirpro.app.ui.viewmodel.KasirViewModel
import com.kasirpro.app.ui.screens.*

class MainActivity : ComponentActivity() {
    private val viewModel: KasirViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkTheme by viewModel.isDarkMode.collectAsState()
            
            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val activeScreenState by viewModel.activeScreen.collectAsState()

                    Scaffold(
                        bottomBar = {
                            val currUser by viewModel.currentUser.collectAsState()
                            val isKasir = currUser?.role == "kasir"
                            val allowedScreens = if (isKasir) {
                                listOf("cashier", "manage", "settings")
                            } else {
                                listOf("home", "cashier", "manage", "premium", "settings")
                            }

                            if (activeScreenState in allowedScreens) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    if (!isKasir) {
                                        NavigationBarItem(
                                            selected = activeScreenState == "home",
                                            onClick = { viewModel.activeScreen.value = "home" },
                                            icon = { Icon(Icons.Default.Home, contentDescription = "Beranda") },
                                            label = { Text("Beranda") },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = OrangePrimary,
                                                selectedTextColor = OrangePrimary,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    }

                                    NavigationBarItem(
                                        selected = activeScreenState == "cashier",
                                        onClick = { viewModel.activeScreen.value = "cashier" },
                                        icon = { Icon(Icons.Default.PointOfSale, contentDescription = "Kasir") },
                                        label = { Text("Kasir") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = OrangePrimary,
                                            selectedTextColor = OrangePrimary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )

                                    NavigationBarItem(
                                        selected = activeScreenState == "manage",
                                        onClick = { viewModel.activeScreen.value = "manage" },
                                        icon = { Icon(Icons.Default.Inventory, contentDescription = "Produk") },
                                        label = { Text("Stok") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = OrangePrimary,
                                            selectedTextColor = OrangePrimary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )

                                    if (!isKasir) {
                                        NavigationBarItem(
                                            selected = activeScreenState == "premium",
                                            onClick = { viewModel.activeScreen.value = "premium" },
                                            icon = { Icon(Icons.Default.Stars, contentDescription = "Premium") },
                                            label = { Text("Premium") },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = OrangePrimary,
                                                selectedTextColor = OrangePrimary,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    }

                                    NavigationBarItem(
                                        selected = activeScreenState == "settings",
                                        onClick = { viewModel.activeScreen.value = "settings" },
                                        icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                                        label = { Text("Pengaturan") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = OrangePrimary,
                                            selectedTextColor = OrangePrimary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    ) { pad ->
                        Box(modifier = Modifier.padding(pad)) {
                            when (activeScreenState) {
                                "splash" -> SplashScreen(viewModel)
                                "onboarding" -> OnboardingScreen(viewModel)
                                "login" -> LoginScreen(viewModel)
                                "register" -> RegisterScreen(viewModel)
                                "forgot_password" -> ForgotPasswordScreen(viewModel)
                                "setup_toko" -> SetupTokoScreen(viewModel)
                                "home" -> DashboardScreen(viewModel)
                                "cashier" -> CashierScreen(viewModel)
                                "manage" -> ProductsStokScreen(viewModel)
                                "premium" -> PremiumScreens(viewModel)
                                "settings" -> BackupSettingsScreen(viewModel)
                                "premium_pricing" -> BackupSettingsScreen(viewModel) // embedded view inside BackupSettingsScreen switcher
                            }
                        }
                    }

                    // Global validation popover modal to notify about Free Limitations and trigger Upgrades
                    val limitMsg by viewModel.showLimitPopup.collectAsState()
                    if (limitMsg != null) {
                        AlertDialog(
                            onDismissRequest = { viewModel.showLimitPopup.value = null },
                            icon = { Icon(Icons.Default.NewReleases, contentDescription = null, tint = OrangePrimary) },
                            title = { Text("Batas Limit Akun Gratis") },
                            text = { Text(limitMsg!!) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.showLimitPopup.value = null
                                        viewModel.activeScreen.value = "premium_pricing"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                                ) {
                                    Text("Upgrade Sekarang")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.showLimitPopup.value = null }) {
                                    Text("Nanti Saja")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
