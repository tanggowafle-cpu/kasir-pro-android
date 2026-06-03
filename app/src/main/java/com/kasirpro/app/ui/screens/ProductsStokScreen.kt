package com.kasirpro.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.kasirpro.app.util.BarcodeScannerHelper
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kasirpro.app.data.local.*
import com.kasirpro.app.data.repository.ProductVariant
import com.kasirpro.app.ui.viewmodel.KasirViewModel
import com.kasirpro.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsStokScreen(viewModel: KasirViewModel) {
    val context = LocalContext.current
    val productsList by viewModel.products.collectAsState()
    val stockHistList by viewModel.stockHistory.collectAsState()
    val currentUserState by viewModel.currentUser.collectAsState()
    val isKasir = currentUserState?.role == "kasir"
    val isPremiumState = currentUserState?.subscriptionStatus == "premium"

    var activeTab by remember { mutableStateOf("PRODUK") } // "PRODUK", "STOK", "RIWAYAT"

    // Dialog state controllers
    var showAddProductDialog by remember { mutableStateOf(false) }
    var showRestockDialogItem by remember { mutableStateOf<ProductEntity?>(null) }
    var showOpnameDialogItem by remember { mutableStateOf<ProductEntity?>(null) }
    var editProductItem by remember { mutableStateOf<ProductEntity?>(null) }

    val idrFormatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    idrFormatter.maximumFractionDigits = 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kelola Produk & Stok", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (!isKasir) {
                        IconButton(onClick = { viewModel.activeScreen.value = "home" }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (activeTab == "PRODUK" && !isKasir) {
                        IconButton(
                            onClick = { showAddProductDialog = true },
                            modifier = Modifier.testTag("add_product_action_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Produk", tint = OrangePrimary)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tab Header Layouts
            TabRow(
                selectedTabIndex = when (activeTab) {
                    "PRODUK" -> 0
                    "STOK" -> 1
                    else -> 2
                },
                contentColor = OrangePrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[when (activeTab) {
                            "PRODUK" -> 0
                            "STOK" -> 1
                            else -> 2
                        }]),
                        color = OrangePrimary
                    )
                }
            ) {
                Tab(selected = activeTab == "PRODUK", onClick = { activeTab = "PRODUK" }) {
                    Text("PRODUK", modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Tab(selected = activeTab == "STOK", onClick = { activeTab = "STOK" }) {
                    Text("STOK FISIK", modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Tab(selected = activeTab == "RIWAYAT", onClick = { activeTab = "RIWAYAT" }) {
                    Text("RIWAYAT", modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            when (activeTab) {
                "PRODUK" -> {
                    if (productsList.isEmpty()) {
                        EmptyStateIllustration(
                            title = "Produk Belum Ada",
                            desc = "Klik tombol '+' di pojok kanan atas untuk menambahkan produk jualan utama Anda!"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(productsList) { prod ->
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
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(prod.nama, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("Modal: ${idrFormatter.format(prod.hargaModal)} • Jual: ${idrFormatter.format(prod.hargaJual)}", fontSize = 12.sp, color = Color.Gray)
                                            Text("Sisa Stok: ${prod.stok} unit", fontSize = 11.sp, color = if (prod.stok <= prod.stokMinimum) Color.Red else Color.Gray, fontWeight = FontWeight.SemiBold)
                                        }

                                        if (!isKasir) {
                                            Row {
                                                IconButton(onClick = { editProductItem = prod }) {
                                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = OrangePrimary)
                                                }
                                                IconButton(onClick = { viewModel.deleteProduct(prod.id) }) {
                                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "STOK" -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(productsList) { prod ->
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
                                        Text(prod.nama, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Sistem Stok: ${prod.stok} unit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }

                                    if (!isKasir) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Button(
                                                onClick = { showRestockDialogItem = prod },
                                                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("TAMBAH", fontSize = 11.sp)
                                            }

                                            Button(
                                                onClick = {
                                                    if (isPremiumState) {
                                                        showOpnameDialogItem = prod
                                                    } else {
                                                        viewModel.showLimitPopup.value = "Stok Opname fisik otomatis dengan sinkronisasi penyesuaian (Selisih Hitung) hanya tersedia untuk premium!"
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("OPNAME", fontSize = 11.sp)
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFF1F5F9))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("Lihat Stok Saja", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> { // RIWAYAT STOK HISTORY REC
                    if (stockHistList.isEmpty()) {
                        EmptyStateIllustration(title = "Belum Ada Mutasi", desc = "Seluruh log penjualan kasir dan penyesuaian resupply tercatat lengkap di riwayat ini.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(stockHistList) { log ->
                                val matchedProd = productsList.find { it.id == log.productId }
                                val colorLabel = when (log.tipe) {
                                    "masuk" -> Color(0xFF15803D)
                                    "keluar" -> Color(0xFFB91C1C)
                                    else -> OrangePrimary
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(matchedProd?.nama ?: "Produk Terhapus", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(log.keterangan ?: "Mutasi penyesuaian stok", fontSize = 11.sp, color = Color.Gray)
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(colorLabel.copy(alpha = 0.15f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "${log.tipe.uppercase()}: ${log.jumlah}",
                                                    color = colorLabel,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
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
        }
    }

    // Modal: Add New Product Dialog
    if (showAddProductDialog) {
        var addNama by remember { mutableStateOf("") }
        var addKategori by remember { mutableStateOf("Makanan") }
        var addHargaJual by remember { mutableStateOf("") }
        var addHargaModal by remember { mutableStateOf("") }
        var addStok by remember { mutableStateOf("") }
        var addMinStok by remember { mutableStateOf("") }
        var addBarcode by remember { mutableStateOf("") }

        var showBarcodeScannerSim by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddProductDialog = false },
            title = { Text("Tambah Produk Jualan Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(value = addNama, onValueChange = { addNama = it }, label = { Text("Nama Produk") }, modifier = Modifier.fillMaxWidth().testTag("add_prod_nama"))
                    OutlinedTextField(value = addKategori, onValueChange = { addKategori = it }, label = { Text("Kategori") }, modifier = Modifier.fillMaxWidth())
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = addHargaJual, onValueChange = { addHargaJual = it }, label = { Text("Harga Jual") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = addHargaModal, onValueChange = { addHargaModal = it }, label = { Text("Harga Modal") }, modifier = Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = addStok, onValueChange = { addStok = it }, label = { Text("Stok Awal") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = addMinStok, onValueChange = { addMinStok = it }, label = { Text("Stok Minimum") }, modifier = Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(value = addBarcode, onValueChange = { addBarcode = it }, label = { Text("Barcode/UPC (Optional)") }, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            BarcodeScannerHelper.startScan(context) { scannedValue ->
                                addBarcode = scannedValue
                            }
                        }) {
                            Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = OrangePrimary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val limitCheck = !isPremiumState && productsList.size >= 10
                        if (limitCheck) {
                            showAddProductDialog = false
                            viewModel.showLimitPopup.value = "Akun Gratis dibatasi maksimal 10 produk. Upgrade ke premium sekarang!"
                            return@Button
                        }

                        if (addNama.isBlank() || addHargaJual.isBlank() || addHargaModal.isBlank() || addStok.isBlank() || addMinStok.isBlank()) {
                            return@Button
                        }

                        viewModel.addProduct(
                            nama = addNama,
                            kategori = addKategori,
                            hargaJual = addHargaJual.toDoubleOrNull() ?: 0.0,
                            hargaModal = addHargaModal.toDoubleOrNull() ?: 0.0,
                            stok = addStok.toIntOrNull() ?: 0,
                            stokMinimum = addMinStok.toIntOrNull() ?: 0,
                            barcode = if (addBarcode.isBlank()) null else addBarcode,
                            fotoUrl = null,
                            varianList = emptyList()
                        )
                        showAddProductDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                    modifier = Modifier.testTag("submit_new_prod")
                ) {
                    Text("Daftarkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddProductDialog = false }) { Text("Batal") }
            }
        )

        if (showBarcodeScannerSim) {
            AlertDialog(
                onDismissRequest = { showBarcodeScannerSim = false },
                title = { Text("Scan Barcode Simulator") },
                text = { Text("Barcode di-scan cepat. Menghasilkan kode standard: '89999920231'") },
                confirmButton = {
                    Button(
                        onClick = {
                            addBarcode = "8999992" + (1000..9999).random().toString()
                            showBarcodeScannerSim = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                    ) {
                        Text("Apply Code")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBarcodeScannerSim = false }) { Text("Batal") }
                }
            )
        }
    }

    // Modal: Simple Restock Dialog
    if (showRestockDialogItem != null) {
        val prod = showRestockDialogItem!!
        var restockAmount by remember { mutableStateOf("") }
        var restockNote by remember { mutableStateOf("Restock baru dari supplier") }

        AlertDialog(
            onDismissRequest = { showRestockDialogItem = null },
            icon = { Icon(Icons.Default.Upload, contentDescription = null, tint = OrangePrimary) },
            title = { Text("Tambah Manual Stok") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(prod.nama, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = restockAmount,
                        onValueChange = { restockAmount = it },
                        label = { Text("Jumlah unit masuk") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = restockNote,
                        onValueChange = { restockNote = it },
                        label = { Text("Catatan riwayat") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = restockAmount.toIntOrNull() ?: 0
                        if (qty > 0) {
                            viewModel.updateProductStock(prod.id, "masuk", qty, restockNote)
                        }
                        showRestockDialogItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("Tambahkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestockDialogItem = null }) { Text("Batal") }
            }
        )
    }

    // Modal: Stock Opname (Penyesuaian Fisik Selisih) Dialog
    if (showOpnameDialogItem != null) {
        val prod = showOpnameDialogItem!!
        var physicalAmount by remember { mutableStateOf("") }
        var opnameNote by remember { mutableStateOf("Opname stok akhir bulan") }

        AlertDialog(
            onDismissRequest = { showOpnameDialogItem = null },
            icon = { Icon(Icons.Default.Calculate, contentDescription = null, tint = OrangePrimary) },
            title = { Text("Stok Opname Fisik") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(prod.nama, fontWeight = FontWeight.Bold)
                    Text("Persediaan system terhitung: ${prod.stok} unit", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = physicalAmount,
                        onValueChange = { physicalAmount = it },
                        label = { Text("Stok fisik nyata di rak") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = opnameNote,
                        onValueChange = { opnameNote = it },
                        label = { Text("Keterangan opname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val phys = physicalAmount.toIntOrNull() ?: 0
                        viewModel.updateProductStock(prod.id, "opname", phys, opnameNote)
                        showOpnameDialogItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("Simpan & Sesuaikan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOpnameDialogItem = null }) { Text("Batal") }
            }
        )
    }

    // Modal: Edit Product
    if (editProductItem != null) {
        val prod = editProductItem!!
        var eNama by remember { mutableStateOf(prod.nama) }
        var eHargaJual by remember { mutableStateOf(prod.hargaJual.toString()) }
        var eHargaModal by remember { mutableStateOf(prod.hargaModal.toString()) }

        AlertDialog(
            onDismissRequest = { editProductItem = null },
            title = { Text("Edit Detail Produk") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = eNama, onValueChange = { eNama = it }, label = { Text("Nama Produk") })
                    OutlinedTextField(value = eHargaJual, onValueChange = { eHargaJual = it }, label = { Text("Harga Jual") })
                    OutlinedTextField(value = eHargaModal, onValueChange = { eHargaModal = it }, label = { Text("Harga Modal") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = prod.copy(
                            nama = eNama,
                            hargaJual = eHargaJual.toDoubleOrNull() ?: prod.hargaJual,
                            hargaModal = eHargaModal.toDoubleOrNull() ?: prod.hargaModal
                        )
                        viewModel.editProduct(updated)
                        editProductItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { editProductItem = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
fun EmptyStateIllustration(title: String, desc: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Inventory,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text = desc,
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 12.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
        }
    }
}
