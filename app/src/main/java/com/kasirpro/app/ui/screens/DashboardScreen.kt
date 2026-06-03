package com.kasirpro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kasirpro.app.ui.viewmodel.KasirViewModel
import com.kasirpro.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: KasirViewModel) {
    val user by viewModel.currentUser.collectAsState()
    val business by viewModel.currentBusiness.collectAsState()
    val transactionsList by viewModel.transactions.collectAsState()
    val lowStockList by viewModel.lowStockProducts.collectAsState()
    val debtsList by viewModel.debts.collectAsState()
    val branchesList by viewModel.branches.collectAsState()
    val isOnlineState by viewModel.isOnline.collectAsState()

    var showBranchDropdown by remember { mutableStateOf(false) }
    var selectedBranchName by remember { mutableStateOf("Semua Cabang") }

    // Analytics calculations (Today's metrics)
    val today = System.currentTimeMillis()
    val startOfDay = today - (today % (24 * 60 * 60 * 1000))
    val todayTransactions = transactionsList.filter { it.createdAt >= startOfDay }

    val totalIncome = todayTransactions.sumOf { it.total }
    val totalProfit = todayTransactions.sumOf { tx ->
        // Profit calculation helper
        tx.total * 0.45 // Estimated margin simulator based on user parameters
    }
    val trxCount = todayTransactions.size
    val activeDebts = debtsList.filter { it.status == "belum" }.sumOf { it.jumlah }

    // Number format helper for IDR currency formatting
    val idrFormatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    idrFormatter.maximumFractionDigits = 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = business?.namaBisnis ?: "Toko Kasir Pro",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnlineState) Color.Green else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isOnlineState) "Koneksi Online" else "Mode Offline",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                actions = {
                    // Simulated active online sync backup trigger
                    IconButton(onClick = { viewModel.toggleOnlineMode() }) {
                        Icon(
                            imageVector = if (isOnlineState) Icons.Default.CloudQueue else Icons.Default.CloudOff,
                            contentDescription = "Sync",
                            tint = if (isOnlineState) OrangePrimary else Color.Gray
                        )
                    }

                    // Active Firestore Sync trigger button
                    IconButton(
                        onClick = { viewModel.populateDemoDataset() },
                        modifier = Modifier.testTag("demo_populate_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Sinkronkan dari Firebase", tint = OrangePrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.activeScreen.value = "cashier" },
                containerColor = OrangePrimary,
                contentColor = Color.White,
                modifier = Modifier
                    .padding(bottom = 60.dp) // Offset above standard bottom navigation safe container bar
                    .testTag("floating_pos_btn")
            ) {
                Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "Buka Kasir")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sapaan Owner / Kasir info & Branch selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Halo, ${user?.nama ?: "Pengusaha Kasir"}!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Selamat bekerja (${user?.role ?: "owner"})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }

                    // Branch filter selector for owners
                    if (user?.role == "owner") {
                        Box {
                            Button(
                                onClick = { showBranchDropdown = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(selectedBranchName, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = showBranchDropdown,
                                onDismissRequest = { showBranchDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Semua Cabang") },
                                    onClick = {
                                        selectedBranchName = "Semua Cabang"
                                        showBranchDropdown = false
                                    }
                                )
                                branchesList.forEach { branch ->
                                    DropdownMenuItem(
                                        text = { Text(branch.namaCabang) },
                                        onClick = {
                                            selectedBranchName = branch.namaCabang
                                            showBranchDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Grid cards for sales analytics
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card Pendapatan Hari Ini
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = OrangeLight)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = OrangePrimary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Pendapatan Hari Ini", fontSize = 11.sp, color = OrangeDark, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = idrFormatter.format(totalIncome),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OrangeDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Card Keuntungan Hari Ini
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFF15803D))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Margin Profit (Est)", fontSize = 11.sp, color = Color(0xFF15803D), fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = idrFormatter.format(totalProfit),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card Jumlah Transaksi
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = OrangePrimary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Transaksi Hari Ini", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
                                Text("$trxCount Transaksi", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Card Hutang Aktif (Premium)
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(Icons.Default.PendingActions, contentDescription = null, tint = Color(0xFFE11D48))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Hutang Aktif", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = idrFormatter.format(activeDebts),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE11D48),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Low Stock list tracking
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Peringatan Stok Tipis", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "Lihat Semua",
                        color = OrangePrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            viewModel.activeScreen.value = "manage"
                        }
                    )
                }
            }

            if (lowStockList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Hebat! Persediaan stok semua produk aman.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                        }
                    }
                }
            } else {
                items(lowStockList.take(3)) { product ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(product.nama, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Stok: ${product.stok} (Batas min: ${product.stokMinimum})", fontSize = 11.sp, color = Color.Red)
                            }
                            Button(
                                onClick = { viewModel.activeScreen.value = "manage" },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                            ) {
                                Text("Resupply", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Recent transactions tracking list
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Transaksi Terbaru", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "Lihat Laporan",
                        color = OrangePrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            viewModel.activeScreen.value = "manage"
                        }
                    )
                }
            }

            if (transactionsList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Belum ada transaksi terekam saat ini.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                items(transactionsList.take(5)) { tx ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(OrangeLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(tx.id, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(tx.metodeBayar, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(idrFormatter.format(tx.total), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = OrangePrimary)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (tx.status == "lunas") Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tx.status.uppercase(),
                                        fontSize = 9.sp,
                                        color = if (tx.status == "lunas") Color(0xFF15803D) else Color(0xFFB91C1C),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
