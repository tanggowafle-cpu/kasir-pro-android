package com.kasirpro.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kasirpro.app.data.local.*
import com.kasirpro.app.data.repository.ProductVariant
import com.kasirpro.app.data.repository.TransactionItem
import com.kasirpro.app.ui.viewmodel.KasirViewModel
import com.kasirpro.app.ui.theme.*
import com.kasirpro.app.util.BarcodeScannerHelper
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierScreen(viewModel: KasirViewModel) {
    val productsList by viewModel.products.collectAsState()
    val cart by viewModel.cartItems.collectAsState()
    val currentCustomer by viewModel.selectedCustomer.collectAsState()
    val activePromo by viewModel.appliedPromo.collectAsState()
    val isSplitEnabled by viewModel.splitPaymentEnabled.collectAsState()
    val splits by viewModel.splitPayments.collectAsState()
    val statusTrx by viewModel.transactionStatus.collectAsState()
    val cashPaid by viewModel.cashAmountPaid.collectAsState()
    val customersList by viewModel.customers.collectAsState()
    val promosList by viewModel.promos.collectAsState()

    val user by viewModel.currentUser.collectAsState()
    val activeShift by viewModel.activeShift.collectAsState()
    val isKasir = user?.role == "kasir"
    var modalInputVal by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Semua") }
    
    // Bottom Sheet Checkout States
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showDiscountDialogItem by remember { mutableStateOf<TransactionItem?>(null) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showPromoPicker by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val categories = listOf("Semua") + productsList.map { it.kategori }.distinct()

    val filteredProducts = productsList.filter {
        (selectedCategory == "Semua" || it.kategori == selectedCategory) &&
        (it.nama.contains(searchQuery, ignoreCase = true) || (it.barcode ?: "").contains(searchQuery))
    }

    val idrFormatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    idrFormatter.maximumFractionDigits = 0

    // Cart calculations
    val totalItemsCount = cart.sumOf { it.jumlah }
    val cartSubtotal = cart.sumOf { (it.harga - it.diskon) * it.jumlah }
    
    val promoDiscountAmount = activePromo?.let { p ->
        if (p.tipe == "diskon_persen") {
            cartSubtotal * (p.nilai / 100.0)
        } else {
            p.nilai
        }
    } ?: 0.0

    val orderTotal = (cartSubtotal - promoDiscountAmount).coerceAtLeast(0.0)

    // Shift modal lock popup
    if (isKasir && activeShift == null) {
        AlertDialog(
            onDismissRequest = { /* strictly non-dismissible */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.LockClock, contentDescription = null, tint = OrangePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mulai Shift Kasir", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Masukkan jumlah saldo uang modal awal (cash) yang tersedia di laci uang kasir untuk memulai pencatatan shift hari ini.",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    OutlinedTextField(
                        value = modalInputVal,
                        onValueChange = { modalInputVal = it.filter { char -> char.isDigit() } },
                        label = { Text("Modal Awal (Rp)") },
                        placeholder = { Text("Contoh: 50000") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("modal_awal_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val modalDouble = modalInputVal.toDoubleOrNull() ?: 0.0
                        viewModel.startShift(modalDouble)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                    enabled = modalInputVal.isNotEmpty(),
                    modifier = Modifier.testTag("start_shift_trigger_btn")
                ) {
                    Text("Mulai Shift POS")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kasir Pos Penjualan", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    if (!isKasir) {
                        IconButton(onClick = { viewModel.activeScreen.value = "home" }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // Start camera scanning with real-time product lookup
                    IconButton(
                        onClick = {
                            BarcodeScannerHelper.startScan(context) { scannedValue ->
                                val matched = productsList.find { it.barcode == scannedValue }
                                if (matched != null) {
                                    if ((matched.varianRaw ?: "").isNotBlank()) {
                                        viewModel.showVarianDialog.value = matched
                                    } else {
                                        viewModel.addToCart(matched)
                                        Toast.makeText(context, "${matched.nama} sukses ditambahkan ke keranjang!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Barcode '$scannedValue' tidak ditemukan!", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("scan_barcode_icon")
                    ) {
                        Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = OrangePrimary)
                    }
                }
            )
        },
        bottomBar = {
            if (cart.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("cart_preview_bar"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("$totalItemsCount Item Terpilih", fontSize = 12.sp)
                            Text(idrFormatter.format(orderTotal), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OrangePrimary)
                        }
                        Button(
                            onClick = { showCheckoutDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("pay_sheet_trigger")
                        ) {
                            Text("Bayar Sekarang", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search & Category Tags Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari produk atau barcode...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pos_product_search"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Quick Category selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) OrangePrimary else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .clickable { selectedCategory = cat }
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = cat,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Inventory2, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(72.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Produk Kosong", fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text("Klik Mulai / Inject data di Beranda untuk demo instan!", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredProducts) { item ->
                        Card(
                            onClick = {
                                // Check if variant exist
                                val parts = (item.varianRaw ?: "").split(";").filter { it.isNotBlank() }
                                if (parts.isNotEmpty()) {
                                    viewModel.showVarianDialog.value = item
                                } else {
                                    viewModel.addToCart(item)
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(OrangeLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.fotoUrl != null) {
                                            AsyncImage(
                                                model = item.fotoUrl,
                                                contentDescription = item.nama,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(imageVector = Icons.Default.Fastfood, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(36.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = item.nama,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = idrFormatter.format(item.hargaJual),
                                        fontWeight = FontWeight.Bold,
                                        color = OrangePrimary,
                                        fontSize = 13.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Stok: ${item.stok}", fontSize = 11.sp, color = if (item.stok <= item.stokMinimum) Color.Red else Color.Gray)
                                        if ((item.varianRaw ?: "").isNotBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(OrangeLight)
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("VARIAN", fontSize = 9.sp, color = OrangeDark, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom Varian Options Dialog Selector
    val activeVarianProd by viewModel.showVarianDialog.collectAsState()
    if (activeVarianProd != null) {
        val prod = activeVarianProd!!
        val options = (prod.varianRaw ?: "").split(";").filter { it.isNotBlank() }.map { ProductVariant.fromString(it) }
        
        AlertDialog(
            onDismissRequest = { viewModel.showVarianDialog.value = null },
            title = { Text("Pilih Varian Produk") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(prod.nama, fontWeight = FontWeight.Bold)
                    options.forEach { opt ->
                        Card(
                            onClick = {
                                viewModel.addToCart(prod, opt)
                                viewModel.showVarianDialog.value = null
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(opt.nama, fontWeight = FontWeight.SemiBold)
                                Text(idrFormatter.format(opt.harga), color = OrangePrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.showVarianDialog.value = null }) { Text("Batal") }
            }
        )
    }

    // Scanner Simulated Popup Panel Dialog
    val activeScanDialog by viewModel.showScanBarcodeDialog.collectAsState()
    if (activeScanDialog) {
        var simulatedScannedBarcode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.showScanBarcodeDialog.value = false },
            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(48.dp)) },
            title = { Text("Simulator Scan Barcode") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Kamera membaca kode produk. Disini, Anda dapat memasukkan manual kode barcode untuk demo scan real-time.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    OutlinedTextField(
                        value = simulatedScannedBarcode,
                        onValueChange = { simulatedScannedBarcode = it },
                        placeholder = { Text("contoh: 899123456001") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val matched = productsList.find { it.barcode == simulatedScannedBarcode }
                        if (matched != null) {
                            if ((matched.varianRaw ?: "").isNotBlank()) {
                                viewModel.showVarianDialog.value = matched
                            } else {
                                viewModel.addToCart(matched)
                                Toast.makeText(context, "${matched.nama} sukses ditambahkan!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Barcode tidak terdaftar!", Toast.LENGTH_SHORT).show()
                        }
                        viewModel.showScanBarcodeDialog.value = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("Simpan & Scan")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showScanBarcodeDialog.value = false }) { Text("Batal") }
            }
        )
    }

    // CHECKOUT DRAWER AND PAYMENT SPLIT CONTROLLERS
    if (showCheckoutDialog) {
        AlertDialog(
            onDismissRequest = { showCheckoutDialog = false },
            title = { Text("Konfirmasi Pembayaran Kasir", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                ) {
                    // Invoice Item List Summary
                    item {
                        Text("Ringkasan Pesanan", fontWeight = FontWeight.Bold, color = OrangePrimary)
                    }
                    items(cart) { ci ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(ci.nama + (ci.varianSelected?.let { " ($it)" } ?: ""), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Qty: ${ci.jumlah} x ${idrFormatter.format(ci.harga)}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Text(idrFormatter.format(ci.subtotal()), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Divider line
                    item { HorizontalDivider() }

                    // Promo setup
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Promo & Loyalty", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                if (currentCustomer != null) {
                                    Text("Pelanggan: ${currentCustomer?.nama} (+${(orderTotal/10000).toInt()} Poin)", fontSize = 11.sp, color = OrangePrimary)
                                }
                                if (activePromo != null) {
                                    Text("Kupon: ${activePromo?.kode} (-${idrFormatter.format(promoDiscountAmount)})", fontSize = 11.sp, color = Color(0xFF15803D))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { showCustomerPicker = true }) {
                                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Add Customer", tint = OrangePrimary)
                                }
                                IconButton(onClick = { showPromoPicker = true }) {
                                    Icon(imageVector = Icons.Default.ConfirmationNumber, contentDescription = "Promo", tint = OrangePrimary)
                                }
                            }
                        }
                    }

                    // Payment partial choice / DP option
                    item {
                        Column {
                            Text("Metode Status Piutang", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = statusTrx == "lunas", onClick = { viewModel.transactionStatus.value = "lunas" })
                                    Text("LUNAS", fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = statusTrx == "dp", onClick = { viewModel.transactionStatus.value = "dp" })
                                    Text("DP / PIUTANG", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Money calculator
                    item {
                        Column {
                            Text("Nominal Pembayaran Tunai", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = if (cashPaid == 0.0) "" else cashPaid.toInt().toString(),
                                    onValueChange = { viewModel.cashAmountPaid.value = it.toDoubleOrNull() ?: 0.0 },
                                    placeholder = { Text("Bayar Pas: ${orderTotal.toInt()}") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("cash_input_text")
                                )
                                Button(
                                    onClick = { viewModel.cashAmountPaid.value = orderTotal },
                                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                                ) {
                                    Text("PAS", fontSize = 12.sp)
                                }
                            }
                            // Quick select nominal bills helper
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(20000.0, 50000.0, 100000.0).forEach { bill ->
                                    Card(
                                        modifier = Modifier
                                            .clickable { viewModel.cashAmountPaid.value = bill }
                                            .weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Text(
                                            idrFormatter.format(bill),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Auto Change Kembalian calculation
                    item {
                        val diff = cashPaid - orderTotal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (diff >= 0) "Kembalian Anda:" else "Sisa Piutang (Kekurangan):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = idrFormatter.format(Math.abs(diff)),
                                fontWeight = FontWeight.Bold,
                                color = if (diff >= 0) Color(0xFF15803D) else Color.Red,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val user = viewModel.currentUser.value
                        val isPremium = user?.subscriptionStatus == "premium"

                        // Free limit transaction guard (50 transactions limit)
                        if (!isPremium && viewModel.transactions.value.size >= 50) {
                            showCheckoutDialog = false
                            viewModel.showLimitPopup.value = "Transaksi gratis bulanan mencapai batas 50 kali. Silakan upgrade ke premium!"
                            return@Button
                        }

                        // Execute checkout
                        viewModel.processCheckout()
                        showCheckoutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                    modifier = Modifier.testTag("finalize_payment_button")
                ) {
                    Text("Proses Lunas")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCheckoutDialog = false }) { Text("Kembali") }
            }
        )
    }

    // Secondarypicker: Customer choose Loyalty DB Dialog
    if (showCustomerPicker) {
        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            title = { Text("Pilih Pelanggan Loyalty Hub") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                    if (customersList.isEmpty()) {
                        item { Text("Database pelanggan kosong. Nyalakan demo atau buat baru di menu Premium Pelanggan!", fontSize = 11.sp, color = Color.Gray) }
                    }
                    items(customersList) { c ->
                        Card(
                            onClick = {
                                viewModel.selectedCustomer.value = c
                                showCustomerPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(c.nama, fontWeight = FontWeight.SemiBold)
                                    Text("Telp: ${c.nomorHp}", fontSize = 11.sp, color = Color.Gray)
                                }
                                Text("${c.totalPoin} PTS", fontWeight = FontWeight.Bold, color = OrangePrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerPicker = false }) { Text("Batal") }
            }
        )
    }

    // Secondarypicker: Promos picker Dialog
    if (showPromoPicker) {
        AlertDialog(
            onDismissRequest = { showPromoPicker = false },
            title = { Text("Gunakan Voucher Kupon Promo") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                    if (promosList.isEmpty()) {
                        item { Text("Database voucher promo kosong.", fontSize = 11.sp, color = Color.Gray) }
                    }
                    items(promosList.filter { it.isActive }) { p ->
                        Card(
                            onClick = {
                                viewModel.appliedPromo.value = p
                                showPromoPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(p.nama, fontWeight = FontWeight.SemiBold)
                                    Text("Kode: ${p.kode}", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                }
                                val amountText = if (p.tipe == "diskon_persen") "${p.nilai.toInt()}%" else idrFormatter.format(p.nilai)
                                Text(amountText, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPromoPicker = false }) { Text("Batal") }
            }
        )
    }

    // Final digital structural thermal receipt popup modal
    val activeReceiptState by viewModel.activeReceipt.collectAsState()
    if (activeReceiptState != null) {
        val rx = activeReceiptState!!
        var showBluetoothSimulation by remember { mutableStateOf(false) }

        // Thermal Struk Dialog
        AlertDialog(
            onDismissRequest = { viewModel.activeReceipt.value = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transaksi Berhasil!", fontWeight = FontWeight.Bold, color = Color(0xFF22C55E), fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("KASIR PRO PRINT", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
                    Text("Cabang Jakarta Selatan", fontSize = 11.sp, color = Color.DarkGray)
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
                            val itemSub = (price - disc) * qty

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$name x$qty", fontSize = 11.sp, color = Color.Black)
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
                    Text("Terima kasih atas kunjungan Anda!", fontSize = 11.sp, textAlign = TextAlign.Center, color = Color.Black)
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                // WhatsApp share implicit intent action
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Terima kasih telah berbelanja di Kasir Pro! Total belanja Anda: ${idrFormatter.format(rx.total)} dengan status: ${rx.status.uppercase()}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Bagikan struk penjualan"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bagikan WA", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { showBluetoothSimulation = true },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Print, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cetak Kasir", fontSize = 11.sp)
                        }
                    }
                    TextButton(
                        onClick = { viewModel.activeReceipt.value = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Selesai")
                    }
                }
            }
        )

        // Bluetooth Simulation Popup Helper
        if (showBluetoothSimulation) {
            AlertDialog(
                onDismissRequest = { showBluetoothSimulation = false },
                icon = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = OrangePrimary) },
                title = { Text("Hubungkan Printer Thermal Bluetooth") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Mencari perangkat printer bluetooth kasir terdekat...", fontSize = 12.sp)
                        Card(
                            onClick = {
                                Toast.makeText(context, "Cetak Struk Ke: RPP02N - Selesai!", Toast.LENGTH_LONG).show()
                                showBluetoothSimulation = false
                                viewModel.activeReceipt.value = null
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("RPP02N (Printer Portable)", fontWeight = FontWeight.Bold)
                                    Text("Mac: 00:11:22:33:FF:EE", fontSize = 10.sp, color = Color.Gray)
                                }
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = OrangePrimary)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBluetoothSimulation = false }) { Text("Kembali") }
                }
            )
        }
    }
}
