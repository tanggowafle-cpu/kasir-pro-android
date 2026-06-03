package com.kasirpro.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kasirpro.app.ui.viewmodel.KasirViewModel
import com.kasirpro.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(viewModel: KasirViewModel) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(2000)
        // Check active user login status
        val current = viewModel.currentUser.value
        if (current != null) {
            val biz = viewModel.currentBusiness.value
            if (biz == null && current.role == "owner") {
                viewModel.activeScreen.value = "setup_toko"
            } else if (current.role == "kasir") {
                viewModel.activeScreen.value = "cashier"
            } else {
                viewModel.activeScreen.value = "home"
            }
        } else {
            viewModel.activeScreen.value = "onboarding"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(OrangePrimary, OrangeDark)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alphaAnim.value)
        ) {
            Icon(
                imageVector = Icons.Default.PointOfSale,
                contentDescription = "Kasir Pro Logo",
                tint = Color.White,
                modifier = Modifier
                    .size(100.dp)
                    .testTag("splash_logo")
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Kasir Pro",
                style = MaterialTheme.typography.displaySmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            )
            Text(
                text = "Aplikasi Kasir Modern Indonesia",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.8f)
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(viewModel: KasirViewModel) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    val pages = listOf(
        OnboardingPageData(
            title = "Transaksi Instan & Barcode",
            description = "Catat penjualan kasir secepat kilat dengan pencarian pintar dan camera scan barcode otomatis.",
            icon = Icons.Default.FlashOn
        ),
        OnboardingPageData(
            title = "Manajemen Cabang & Stok",
            description = "Pantau persediaan stok minimum dan kelola banyak cabang usaha secara realtime langsung dari genggaman Anda.",
            icon = Icons.Default.PointOfSale
        ),
        OnboardingPageData(
            title = "Mode Offline & Cloud Sync",
            description = "Internet mati? Transaksi tetap berjalan normal secara offline dan otomatis sync saat online kembali.",
            icon = Icons.Default.CloudUpload
        )
    )

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicators
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { index ->
                        val selected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (selected) 24.dp else 8.dp, 8.dp)
                                .clip(CircleShape)
                                .background(if (selected) OrangePrimary else Color.LightGray)
                        )
                    }
                }

                // Call to action button
                Button(
                    onClick = {
                        if (pagerState.currentPage < 2) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            viewModel.activeScreen.value = "login"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("onboarding_next_button")
                ) {
                    Text(
                        text = if (pagerState.currentPage == 2) "Mulai Sekarang" else "Lanjut",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (pagerState.currentPage == 2) Icons.Default.Done else Icons.Default.ArrowForward,
                        contentDescription = "Selanjutnya",
                        tint = Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            OnboardingView(data = pages[page])
        }
    }
}

data class OnboardingPageData(
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
fun OnboardingView(data: OnboardingPageData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(OrangeLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = data.icon,
                contentDescription = null,
                tint = OrangePrimary,
                modifier = Modifier.size(80.dp)
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = data.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = data.description,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                lineHeight = 22.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}
