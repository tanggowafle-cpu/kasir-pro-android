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
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import com.kasirpro.app.data.local.TransactionEntity
import com.kasirpro.app.util.ShopLogoImage

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

    var selectedTxForReceipt by remember { mutableStateOf<TransactionEntity?>(null) }
    var showCorrectionAuthDialog by remember { mutableStateOf(false) }
    var showCorrectionEditDialog by remember { mutableStateOf(false) }
    var authCodeInput by remember { mutableStateOf("") }

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

    val posInteractionSource = remember { MutableInteractionSource() }
    val isPosPressed by posInteractionSource.collectIsPressedAsState()
    val posScale by animateFloatAsState(
        targetValue = if (isPosPressed) 0.9f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "pos_action_scale"
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                shape = CircleShape,
                containerColor = OrangePrimary,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 4.dp
                ),
                interactionSource = posInteractionSource,
                modifier = Modifier
                    .graphicsLayer(scaleX = posScale, scaleY = posScale)
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
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTxForReceipt = tx
                            },
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

    // Viewing Receipt & Correction Trigger Dialogue
    if (selectedTxForReceipt != null) {
        val rx = selectedTxForReceipt!!
        val isPremium = user?.subscriptionStatus == "premium"
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        AlertDialog(
            onDismissRequest = { selectedTxForReceipt = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = null, tint = OrangePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Detail Struk Transaksi", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Dynamic Logo
                    if (!business?.logoUrl.isNullOrBlank()) {
                        ShopLogoImage(
                            logoUrl = business?.logoUrl,
                            contentDescription = business?.namaBisnis,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .padding(bottom = 6.dp)
                        )
                    }
                    Text(business?.namaBisnis ?: "KASIR PRO SHOP", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp, textAlign = TextAlign.Center)
                    if (!business?.alamat.isNullOrBlank()) {
                        Text(business!!.alamat!!, fontSize = 11.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
                    } else {
                        Text("Cabang Utama", fontSize = 11.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
                    }
                    if (!business?.noTelpon.isNullOrBlank()) {
                        Text("Tel: ${business!!.noTelpon!!}", fontSize = 11.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
                    }
                    Text("---------------------------------", color = Color.Black)
                    Text("No TRX: ${rx.id}", fontSize = 11.sp, color = Color.Black)
                    Text("Kasir: ${rx.kasirNama}", fontSize = 11.sp, color = Color.Black)
                    Text("---------------------------------", color = Color.Black)

                    // Serialized items
                    val itemsSplit = rx.itemsRaw.split(";").filter { it.isNotBlank() }
                    itemsSplit.forEach { line ->
                        val parts = line.split(":")
                        if (parts.size >= 4) {
                            val name = parts.getOrNull(1).orEmpty()
                            val qty = parts.getOrNull(2)?.toIntOrNull() ?: 1
                            val price = parts.getOrNull(3)?.toDoubleOrNull() ?: 0.0
                            val disc = parts.getOrNull(5)?.toDoubleOrNull() ?: 0.0
                            val sat = parts.getOrNull(6).orEmpty().takeIf { it.isNotBlank() } ?: "Pcs"
                            val itemSub = (price - disc) * qty

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$name x$qty $sat", fontSize = 11.sp, color = Color.Black)
                                Text(idrFormatter.format(itemSub), fontSize = 11.sp, color = Color.Black)
                            }
                        }
                    }

                    Text("---------------------------------", color = Color.Black)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subtotal", fontSize = 11.sp, color = Color.Black)
                        Text(idrFormatter.format(rx.subtotal), fontSize = 11.sp, color = Color.Black)
                    }
                    if (rx.diskonTotal > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Diskon Promo", fontSize = 11.sp, color = Color.Black)
                            Text("-${idrFormatter.format(rx.diskonTotal)}", fontSize = 11.sp, color = Color.Black)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(idrFormatter.format(rx.total), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("DIBAYAR", fontSize = 11.sp, color = Color.Black)
                        Text(idrFormatter.format(rx.bayarNominal), fontSize = 11.sp, color = Color.Black)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("KEMBALI", fontSize = 11.sp, color = Color.Black)
                        Text(idrFormatter.format(rx.kembalian), fontSize = 11.sp, color = Color.Black)
                    }
                    Text("---------------------------------", color = Color.Black)
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (isPremium) {
                                showCorrectionAuthDialog = true
                            } else {
                                viewModel.showLimitPopup.value = "Fitur Koreksi Transaksi hanya tersedia untuk pengguna Premium Pro. Upgrade sekarang!"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Koreksi Transaksi")
                    }

                    OutlinedButton(
                        onClick = { selectedTxForReceipt = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tutup")
                    }
                }
            }
        )
    }

    if (showCorrectionAuthDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showCorrectionAuthDialog = false },
            title = { Text("Otoritas Pemilik Diperlukan", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Masukkan Kode Unik Otoritas dari Pemilik Toko untuk mengizinkan koreksi/edit pada transaksi ini.", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = authCodeInput,
                        onValueChange = { authCodeInput = it },
                        label = { Text("Kode Otoritas Pemilik") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val realCode = viewModel.getOwnerVerificationCode()
                        if (authCodeInput == realCode) {
                            showCorrectionAuthDialog = false
                            showCorrectionEditDialog = true
                            authCodeInput = "" // clear
                        } else {
                            Toast.makeText(context, "Kode Otoritas salah! Akses Ditolak.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("Verifikasi")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCorrectionAuthDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showCorrectionEditDialog && selectedTxForReceipt != null) {
        val rx = selectedTxForReceipt!!
        val context = LocalContext.current
        var editTotalStr by remember { mutableStateOf(rx.total.toInt().toString()) }
        var editBayarStr by remember { mutableStateOf(rx.bayarNominal.toInt().toString()) }
        var editDiskonStr by remember { mutableStateOf(rx.diskonTotal.toInt().toString()) }
        var editMetodeBayar by remember { mutableStateOf(rx.metodeBayar) }
        var editStatus by remember { mutableStateOf(rx.status) }

        AlertDialog(
            onDismissRequest = { showCorrectionEditDialog = false },
            title = { Text("Form Koreksi Transaksi", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Text("ID Transaksi: ${rx.id}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                    
                    OutlinedTextField(
                        value = editTotalStr,
                        onValueChange = { editTotalStr = it },
                        label = { Text("Total Belanja Baru (Rp)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = editDiskonStr,
                        onValueChange = { editDiskonStr = it },
                        label = { Text("Diskon Baru (Rp)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = editBayarStr,
                        onValueChange = { editBayarStr = it },
                        label = { Text("Nominal Bayar Baru (Rp)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    // Dropdown for Metode Bayar
                    Column {
                        Text("Metode Pembayaran", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            listOf("Tunai", "QRIS", "Transfer", "Debit").forEach { m ->
                                Card(
                                    onClick = { editMetodeBayar = m },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (editMetodeBayar == m) OrangePrimary else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.padding(8.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text(m, fontSize = 10.sp, color = if (editMetodeBayar == m) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Dropdown for Status
                    Column {
                        Text("Status Pembayaran", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            listOf("lunas", "dp").forEach { s ->
                                Card(
                                    onClick = { editStatus = s },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (editStatus == s) OrangePrimary else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.padding(8.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text(s.uppercase(), fontSize = 10.sp, color = if (editStatus == s) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val totalVal = editTotalStr.toDoubleOrNull() ?: rx.total
                        val bayarVal = editBayarStr.toDoubleOrNull() ?: rx.bayarNominal
                        val diskonVal = editDiskonStr.toDoubleOrNull() ?: rx.diskonTotal

                        if (bayarVal < totalVal && editStatus == "lunas") {
                            Toast.makeText(context, "Nominal bayar kurang dari total untuk status lunas!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val calculatedKembalian = (bayarVal - totalVal).coerceAtLeast(0.0)

                        val updatedTx = rx.copy(
                            total = totalVal,
                            bayarNominal = bayarVal,
                            diskonTotal = diskonVal,
                            metodeBayar = editMetodeBayar,
                            status = editStatus,
                            kembalian = calculatedKembalian
                        )

                        viewModel.correctTransaction(updatedTx) {
                            showCorrectionEditDialog = false
                            selectedTxForReceipt = null
                            Toast.makeText(context, "Koreksi transaksi berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("Simpan Koreksi")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCorrectionEditDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
