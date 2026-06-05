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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.kasirpro.app.util.BarcodeScannerHelper
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kasirpro.app.data.local.*
import com.kasirpro.app.data.repository.ProductVariant
import com.kasirpro.app.ui.viewmodel.KasirViewModel
import com.kasirpro.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import android.net.Uri
import coil.compose.AsyncImage
import com.kasirpro.app.util.ImageHelper
import com.kasirpro.app.util.ProductImage
import com.kasirpro.app.util.ExcelHelper
import java.util.UUID
import android.provider.OpenableColumns

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
    var showBulkUploadDialog by remember { mutableStateOf(false) }
    var showRestockDialogItem by remember { mutableStateOf<ProductEntity?>(null) }
    var showOpnameDialogItem by remember { mutableStateOf<ProductEntity?>(null) }
    var editProductItem by remember { mutableStateOf<ProductEntity?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val templateSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ExcelHelper.writeProductTemplateXlsx(out)
                    Toast.makeText(context, "Template Excel berhasil didownload!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Gagal mendownload template: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val idrFormatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    idrFormatter.maximumFractionDigits = 0

    val addProductInteractionSource = remember { MutableInteractionSource() }
    val isAddProductPressed by addProductInteractionSource.collectIsPressedAsState()
    val addProductScale by animateFloatAsState(
        targetValue = if (isAddProductPressed) 0.9f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "add_product_scale"
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                        Button(
                            onClick = { showBulkUploadDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 6.dp).testTag("bulk_upload_action_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload Massal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (activeTab == "PRODUK" && !isKasir) {
                FloatingActionButton(
                    onClick = { showAddProductDialog = true },
                    shape = CircleShape,
                    containerColor = OrangePrimary,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 4.dp
                    ),
                    interactionSource = addProductInteractionSource,
                    modifier = Modifier
                        .graphicsLayer(scaleX = addProductScale, scaleY = addProductScale)
                        .testTag("add_product_action_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah Produk",
                        modifier = Modifier.size(24.dp)
                    )
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
                            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                ProductImage(
                                                    fotoUrl = prod.fotoUrl,
                                                    contentDescription = prod.nama,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(prod.nama, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                if (prod.kategori.isNotBlank()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(vertical = 2.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(OrangePrimary.copy(alpha = 0.1f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = prod.kategori,
                                                            fontSize = 10.sp,
                                                            color = OrangePrimary,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                Text("Modal: ${idrFormatter.format(prod.hargaModal)} • Jual: ${idrFormatter.format(prod.hargaJual)}", fontSize = 12.sp, color = Color.Gray)
                                                Text("Sisa Stok: ${prod.stok} ${prod.satuan}", fontSize = 11.sp, color = if (prod.stok <= prod.stokMinimum) Color.Red else Color.Gray, fontWeight = FontWeight.SemiBold)
                                            }
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
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
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
                            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
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
        var addKategori by remember { mutableStateOf("") }
        var addHargaJual by remember { mutableStateOf("") }
        var addHargaModal by remember { mutableStateOf("") }
        var addStok by remember { mutableStateOf("") }
        var addMinStok by remember { mutableStateOf("5") }
        var addBarcode by remember { mutableStateOf("") }
        var addSatuan by remember { mutableStateOf("Pcs") }

        var addImgBytes by remember { mutableStateOf<ByteArray?>(null) }
        var addImgUri by remember { mutableStateOf<Uri?>(null) }
        var isUploadingPhoto by remember { mutableStateOf(false) }

        val galleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                addImgUri = uri
                val compressed = ImageHelper.compressImageUri(context, uri)
                if (compressed != null) {
                    addImgBytes = compressed
                } else {
                    Toast.makeText(context, "Gagal memproses foto", Toast.LENGTH_SHORT).show()
                }
            }
        }

        var showBarcodeScannerSim by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isUploadingPhoto) showAddProductDialog = false },
            title = { Text("Tambah Produk Jualan Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Photo Upload Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (addImgUri != null) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = addImgUri,
                                    contentDescription = "Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Delete/Clear photo overlay
                                FilledIconButton(
                                    onClick = {
                                        addImgUri = null
                                        addImgBytes = null
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Red),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                // Size info badge
                                val kbSize = (addImgBytes?.size ?: 0) / 1024
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Ukuran: ${kbSize}KB", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Pilih Foto dari Galeri (Max 500KB)", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }

                    OutlinedTextField(value = addNama, onValueChange = { addNama = it }, label = { Text("Nama Produk") }, modifier = Modifier.fillMaxWidth().testTag("add_prod_nama"))
                    
                    CategorySelectionField(
                        value = addKategori,
                        onValueChange = { addKategori = it },
                        productsList = productsList
                    )
                    
                    SatuanSelectionField(
                        value = addSatuan,
                        onValueChange = { addSatuan = it }
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = addHargaJual,
                            onValueChange = { addHargaJual = it },
                            label = { Text("Harga Jual") },
                            modifier = Modifier.weight(1f).testTag("add_prod_harga_jual"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = addHargaModal,
                            onValueChange = { addHargaModal = it },
                            label = { Text("Harga Modal") },
                            modifier = Modifier.weight(1f).testTag("add_prod_harga_modal"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = addStok,
                            onValueChange = { addStok = it },
                            label = { Text("Stok Awal") },
                            modifier = Modifier.weight(1f).testTag("add_prod_stok"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = addMinStok,
                            onValueChange = { addMinStok = it },
                            label = { Text("Stok Minimum") },
                            modifier = Modifier.weight(1f).testTag("add_prod_min_stok"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
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

                    if (isUploadingPhoto) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = OrangePrimary)
                            Text("Mengupload foto ke Fire Storage...", fontSize = 11.sp, color = Color.Gray)
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
                            Toast.makeText(context, "Silakan isi semua data wajib!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val ownerId = currentUserState?.uid ?: "owner-main"
                        val productId = UUID.randomUUID().toString()

                        isUploadingPhoto = true
                        coroutineScope.launch {
                            var uploadedUrl: String? = null
                            val bytes = addImgBytes
                            if (bytes != null) {
                                try {
                                    uploadedUrl = ImageHelper.uploadProductImage(context, ownerId, productId, bytes)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Gagal mengupload foto: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }

                            viewModel.addProductWithBranch(
                                id = productId,
                                nama = addNama,
                                kategori = addKategori,
                                hargaJual = addHargaJual.toDoubleOrNull() ?: 0.0,
                                hargaModal = addHargaModal.toDoubleOrNull() ?: 0.0,
                                stok = addStok.toIntOrNull() ?: 0,
                                stokMinimum = addMinStok.toIntOrNull() ?: 5,
                                barcode = if (addBarcode.isBlank()) null else addBarcode,
                                fotoUrl = uploadedUrl,
                                branchId = "branch-1-$ownerId", // default branch ID of owner
                                satuan = addSatuan
                            )
                            isUploadingPhoto = false
                            showAddProductDialog = false
                            Toast.makeText(context, "Menyimpan Produk", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isUploadingPhoto,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                    modifier = Modifier.testTag("submit_new_prod")
                ) {
                    Text("Daftarkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isUploadingPhoto) showAddProductDialog = false }, enabled = !isUploadingPhoto) { Text("Batal") }
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
        var eKategori by remember { mutableStateOf("") }
        var eHargaJual by remember { mutableStateOf(prod.hargaJual.toString()) }
        var eHargaModal by remember { mutableStateOf(prod.hargaModal.toString()) }
        var eStok by remember { mutableStateOf(prod.stok.toString()) }
        var eMinStok by remember { mutableStateOf(prod.stokMinimum.toString()) }
        var eBarcode by remember { mutableStateOf(prod.barcode ?: "") }
        var eSatuan by remember { mutableStateOf(prod.satuan) }
        
        var eImgBytes by remember { mutableStateOf<ByteArray?>(null) }
        var eImgUri by remember { mutableStateOf<Uri?>(null) }
        var eFotoUrlState by remember { mutableStateOf(prod.fotoUrl) }
        var isEditingUploading by remember { mutableStateOf(false) }

        val editGalleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                eImgUri = uri
                val compressed = ImageHelper.compressImageUri(context, uri)
                if (compressed != null) {
                    eImgBytes = compressed
                } else {
                    Toast.makeText(context, "Gagal memproses foto", Toast.LENGTH_SHORT).show()
                }
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isEditingUploading) editProductItem = null },
            title = { Text("Edit Detail Produk", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Photo Upload / Change / Remove Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { editGalleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (eImgUri != null) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = eImgUri,
                                    contentDescription = "Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                FilledIconButton(
                                    onClick = {
                                        eImgUri = null
                                        eImgBytes = null
                                        eFotoUrlState = null
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Red),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else if (!eFotoUrlState.isNullOrBlank()) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                ProductImage(
                                    fotoUrl = eFotoUrlState,
                                    contentDescription = prod.nama,
                                    modifier = Modifier.fillMaxSize()
                                )
                                FilledIconButton(
                                    onClick = {
                                        eFotoUrlState = null
                                        eImgBytes = null
                                        eImgUri = null
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Red),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Ganti atau Tambah Foto Produk", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }

                    OutlinedTextField(value = eNama, onValueChange = { eNama = it }, label = { Text("Nama Produk") }, modifier = Modifier.fillMaxWidth())
                    
                    CategorySelectionField(
                        value = eKategori,
                        onValueChange = { eKategori = it },
                        productsList = productsList
                    )

                    SatuanSelectionField(
                        value = eSatuan,
                        onValueChange = { eSatuan = it }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = eHargaJual,
                            onValueChange = { eHargaJual = it },
                            label = { Text("Harga Jual") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = eHargaModal,
                            onValueChange = { eHargaModal = it },
                            label = { Text("Harga Modal") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = eStok,
                            onValueChange = { eStok = it },
                            label = { Text("Stok") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = eMinStok,
                            onValueChange = { eMinStok = it },
                            label = { Text("Stok Minimum") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    OutlinedTextField(value = eBarcode, onValueChange = { eBarcode = it }, label = { Text("Barcode (Optional)") }, modifier = Modifier.fillMaxWidth())

                    if (isEditingUploading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = OrangePrimary)
                            Text("Menyimpan perubahan dan foto...", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (eNama.isBlank() || eHargaJual.isBlank() || eHargaModal.isBlank() || eStok.isBlank() || eMinStok.isBlank()) {
                            Toast.makeText(context, "Silakan lengkapi semua parameter wajib!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val ownerId = currentUserState?.uid ?: "owner-main"
                        isEditingUploading = true

                        coroutineScope.launch {
                            var finalFotoUrl = eFotoUrlState
                            val bytes = eImgBytes
                            if (bytes != null) {
                                try {
                                    finalFotoUrl = ImageHelper.uploadProductImage(context, ownerId, prod.id, bytes)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Gagal upload foto: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }

                            val updated = prod.copy(
                                nama = eNama,
                                kategori = eKategori,
                                hargaJual = eHargaJual.toDoubleOrNull() ?: prod.hargaJual,
                                hargaModal = eHargaModal.toDoubleOrNull() ?: prod.hargaModal,
                                stok = eStok.toIntOrNull() ?: prod.stok,
                                stokMinimum = eMinStok.toIntOrNull() ?: prod.stokMinimum,
                                barcode = eBarcode.takeIf { it.isNotBlank() },
                                fotoUrl = finalFotoUrl,
                                satuan = eSatuan
                            )
                            viewModel.editProduct(updated)
                            isEditingUploading = false
                            editProductItem = null
                            Toast.makeText(context, "Menyimpan Produk", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isEditingUploading,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isEditingUploading) editProductItem = null }, enabled = !isEditingUploading) { Text("Batal") }
            }
        )
    }

    // Modal: Bulk Upload Products
    if (showBulkUploadDialog) {
        val destBranches by viewModel.branches.collectAsState()
        var selectedBranch by remember { mutableStateOf<BranchEntity?>(null) }
        
        var excelFileUri by remember { mutableStateOf<Uri?>(null) }
        var excelFileName by remember { mutableStateOf("") }
        var selectedProductPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
        var parsedProducts by remember { mutableStateOf<List<ParsedBulkProduct>>(emptyList()) }
        var embeddedPhotos by remember { mutableStateOf<Map<Int, ByteArray>>(emptyMap()) }
        
        var isUploading by remember { mutableStateOf(false) }
        var uploadStatusText by remember { mutableStateOf("") }
        var uploadProgress by remember { mutableStateOf(0f) }

        // Automatically default select first branch
        LaunchedEffect(destBranches) {
            if (selectedBranch == null && destBranches.isNotEmpty()) {
                selectedBranch = destBranches.first()
            }
        }

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                excelFileUri = uri
                excelFileName = getFileNameFromUri(context, uri)
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val isSpreadsheet = excelFileName.endsWith(".xlsx", ignoreCase = true)
                        val raw = ExcelHelper.parseStream(stream, isSpreadsheet)
                        if (raw.isNotEmpty()) {
                            // Drop headers
                            parsedProducts = raw.drop(1).mapIndexed { index, row ->
                                validateRow(row, index + 2)
                            }
                        } else {
                            Toast.makeText(context, "File kosong atau tidak dapat di-parse", Toast.LENGTH_SHORT).show()
                        }
                    }
                    val isSpreadsheet = excelFileName.endsWith(".xlsx", ignoreCase = true)
                    if (isSpreadsheet) {
                        context.contentResolver.openInputStream(uri)?.use { imgStream ->
                            embeddedPhotos = ExcelHelper.extractImagesFromXlsx(imgStream)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Gagal mengimpor spreadsheet: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val photosPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
        ) { uris: List<Uri>? ->
            if (uris != null) {
                selectedProductPhotos = uris
                Toast.makeText(context, "Berhasil melampirkan ${uris.size} foto produk!", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isUploading) showBulkUploadDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = OrangePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Produk Massal (.xlsx / .csv)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Step 1: Branch Picker
                    Text("1. Pilih Cabang Toko Tujuan", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        var branchExpanded by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { branchExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(selectedBranch?.namaCabang ?: "Pilih Cabang...")
                        }
                        DropdownMenu(expanded = branchExpanded, onDismissRequest = { branchExpanded = false }) {
                            destBranches.forEach { br ->
                                DropdownMenuItem(
                                    text = { Text(br.namaCabang) },
                                    onClick = {
                                        selectedBranch = br
                                        branchExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Step 2: Download Template
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("2. Unduh Template Resmi", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                        TextButton(
                            onClick = { templateSaverLauncher.launch("template_upload_massal_kasirpro.xlsx") },
                            colors = ButtonDefaults.textButtonColors(contentColor = OrangePrimary)
                        ) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download Template")
                        }
                    }

                    // Step 3: Select Spreadsheet File
                    Text("3. Pilih Spreadsheet (.xlsx / .csv)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangePrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.InsertDriveFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (excelFileUri != null) excelFileName else "Pilih File Excel / CSV")
                    }

                    // Step 4: Optional Multiple Photo uploads
                    Text("4. Lampirkan Folder/File Foto (Opsional)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { photosPickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.DarkGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (selectedProductPhotos.isNotEmpty()) "Terpilih ${selectedProductPhotos.size} foto" else "Pilih Beberapa Foto")
                        }
                        if (selectedProductPhotos.isNotEmpty()) {
                            Text("Foto akan dicocokkan otomatis dengan kolom 'Nama File Foto' di spreadsheet.", fontSize = 10.sp, color = Color.LightGray)
                        }
                    }

                    // Step 5: Table Data Preview
                    if (parsedProducts.isNotEmpty()) {
                        val validCount = parsedProducts.count { it.isValid }
                        val invalidCount = parsedProducts.size - validCount

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Preview & Validasi Data (${parsedProducts.size} Baris)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFE8F5E9))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Valid: $validCount", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 12.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFFEAEE))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Error: $invalidCount", fontWeight = FontWeight.Bold, color = Color(0xFFC62828), fontSize = 12.sp)
                            }
                        }

                        // Preview Table
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(6.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            parsedProducts.forEach { item ->
                                val bgColor = if (item.isValid) Color(0xFFE8F5E9) else Color(0xFFFFEAEE)
                                val textColor = if (item.isValid) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                val statusLabel = if (item.isValid) "Baris ${item.rowNum}: OK" else "Baris ${item.rowNum}: ERROR"

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = bgColor)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(statusLabel, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = textColor)
                                        Text("Nama: ${item.nama.ifBlank { "[KOSONG]" }} | Kategori: ${item.kategori.ifBlank { "[KOSONG]" }}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Jual: ${idrFormatter.format(item.hargaJual)} | Stok: ${item.stok}", fontSize = 10.sp, color = Color.DarkGray)
                                        if (!item.isValid && item.errorMessage != null) {
                                            Text("Detail Error: ${item.errorMessage}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Progress Loader Screen
                    if (isUploading) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = uploadProgress,
                                color = OrangePrimary,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            )
                            Text(uploadStatusText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                        }
                    }
                }
            },
            confirmButton = {
                val validProducts = parsedProducts.filter { it.isValid }
                Button(
                    onClick = {
                        val limitCheck = !isPremiumState && (productsList.size + validProducts.size) > 10
                        if (limitCheck) {
                            Toast.makeText(context, "Jumlah total melebihi batas 10 produk untuk akun Gratis. upgrade ke premium!", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        if (selectedBranch == null) {
                            Toast.makeText(context, "Silakan pilih cabang tujuan!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isUploading = true
                        uploadProgress = 0f
                        uploadStatusText = "Memulai bulk upload..."

                        coroutineScope.launch {
                            val ownerId = currentUserState?.uid ?: "owner-main"
                            val targetBranchId = selectedBranch!!.id
                            var successCount = 0

                            validProducts.forEachIndexed { idx, p ->
                                uploadStatusText = "Proses baris ${p.rowNum}: ${p.nama}..."
                                uploadProgress = (idx.toFloat() / validProducts.size)

                                var imageUrl: String? = null
                                
                                // Priority 1: Check embeddedPhotos extracted from XLSX directly
                                val embeddedBytes = embeddedPhotos[p.rowNum]
                                if (embeddedBytes != null) {
                                    uploadStatusText = "Memproses foto: ${p.nama}..."
                                    try {
                                        val pId = UUID.randomUUID().toString()
                                        imageUrl = ImageHelper.uploadProductImage(context, ownerId, pId, embeddedBytes)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                } else {
                                    // Priority 2: Fallback to matched photo name selected from multiple photos
                                    val matchedUri = selectedProductPhotos.find { photoUri ->
                                        val localFilename = getFileNameFromUri(context, photoUri)
                                        localFilename.equals(p.fotoName, ignoreCase = true)
                                    }

                                    if (matchedUri != null) {
                                        uploadStatusText = "Memproses foto: ${p.fotoName}..."
                                        val bytes = ImageHelper.compressImageUri(context, matchedUri)
                                        if (bytes != null) {
                                            try {
                                                val pId = UUID.randomUUID().toString()
                                                imageUrl = ImageHelper.uploadProductImage(context, ownerId, pId, bytes)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                }

                                val singleProdId = UUID.randomUUID().toString()
                                val successResult = viewModel.repository.insertProductWithBranch(
                                    id = singleProdId,
                                    nama = p.nama,
                                    kategori = p.kategori,
                                    hargaJual = p.hargaJual,
                                    hargaModal = p.hargaModal,
                                    stok = p.stok,
                                    stokMinimum = p.stokMinimum,
                                    barcode = p.barcode,
                                    fotoUrl = imageUrl,
                                    branchId = targetBranchId,
                                    satuan = p.satuan
                                )
                                if (successResult) {
                                    successCount++
                                }
                            }

                            isUploading = false
                            showBulkUploadDialog = false
                            Toast.makeText(context, "Sukses mengupload $successCount produk ke Cabang ${selectedBranch!!.namaCabang}!", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = parsedProducts.any { it.isValid } && !isUploading,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("Mulai Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isUploading) showBulkUploadDialog = false }, enabled = !isUploading) { Text("Batal") }
            }
        )
    }
}

data class ParsedBulkProduct(
    val rowNum: Int,
    val rawRow: List<String>,
    val nama: String,
    val kategori: String,
    val hargaJual: Double,
    val hargaModal: Double,
    val stok: Int,
    val stokMinimum: Int,
    val barcode: String?,
    val fotoName: String?,
    val satuan: String,
    val isValid: Boolean,
    val errorMessage: String?
)

fun validateRow(row: List<String>, rowIndex: Int): ParsedBulkProduct {
    val nama = row.getOrNull(0)?.trim() ?: ""
    val kategori = row.getOrNull(1)?.trim() ?: ""
    val rawHargaJual = row.getOrNull(2)?.trim() ?: ""
    val rawHargaModal = row.getOrNull(3)?.trim() ?: ""
    val rawStok = row.getOrNull(4)?.trim() ?: ""
    val rawMinStok = row.getOrNull(5)?.trim() ?: ""
    val barcode = row.getOrNull(6)?.trim()?.takeIf { it.isNotBlank() }
    val fotoName = row.getOrNull(7)?.trim()?.takeIf { it.isNotBlank() }
    val satuan = row.getOrNull(8)?.trim()?.takeIf { it.isNotBlank() } ?: "Pcs"

    var isValid = true
    val errors = mutableListOf<String>()

    if (nama.isBlank()) {
        isValid = false
        errors.add("Nama produk wajib diisi.")
    }
    if (kategori.isBlank()) {
        isValid = false
        errors.add("Kategori wajib diisi.")
    }

    val jPrice = rawHargaJual.toDoubleOrNull()
    if (jPrice == null || jPrice <= 0.0) {
        isValid = false
        errors.add("Harga jual harus angka valid > 0.")
    }

    val mPrice = rawHargaModal.toDoubleOrNull() ?: 0.0

    val startStock = rawStok.toIntOrNull()
    if (startStock == null) {
        isValid = false
        errors.add("Stok awal harus angka.")
    } else if (startStock < 0) {
        isValid = false
        errors.add("Stok awal tidak boleh negatif.")
    }

    val minSt = rawMinStok.toIntOrNull() ?: 5

    return ParsedBulkProduct(
        rowNum = rowIndex,
        rawRow = row,
        nama = nama,
        kategori = kategori,
        hargaJual = jPrice ?: 0.0,
        hargaModal = mPrice,
        stok = startStock ?: 0,
        stokMinimum = minSt,
        barcode = barcode,
        fotoName = fotoName,
        satuan = satuan,
        isValid = isValid,
        errorMessage = if (isValid) null else errors.joinToString("; ")
    )
}

fun getFileNameFromUri(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "unnamed.png"
}

@Composable
fun CategorySelectionField(
    value: String,
    onValueChange: (String) -> Unit,
    productsList: List<ProductEntity>
) {
    val predefined = listOf("Makanan", "Minuman", "Snack", "Rokok", "Sembako", "Kebersihan", "Kesehatan", "Elektronik", "Pakaian", "Alat Tulis", "Lainnya")
    
    // Sort historic by frequency
    val categoryFrequencies = productsList.map { it.kategori }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
    
    val frequentSorted = categoryFrequencies.entries
        .sortedByDescending { it.value }
        .map { it.key }
        .take(5)

    // Combine distinct
    val allOptions = (frequentSorted + predefined).distinct()

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Kategori") },
            modifier = Modifier.fillMaxWidth().testTag("add_prod_kategori"),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            allOptions.forEach { category ->
                val isSelected = value.equals(category, ignoreCase = true)
                val chipColor = if (isSelected) OrangePrimary else Color.LightGray.copy(alpha = 0.2f)
                val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

                Box(
                    modifier = Modifier
                        .background(color = chipColor, shape = RoundedCornerShape(16.dp))
                        .clickable { onValueChange(category) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (frequentSorted.contains(category) && !predefined.contains(category)) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else OrangePrimary,
                                modifier = Modifier.size(12.dp).padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = category,
                            fontSize = 12.sp,
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SatuanSelectionField(
    value: String,
    onValueChange: (String) -> Unit
) {
    val predefinedUnits = listOf("Pcs", "Kg", "Gram", "Liter", "Ml", "Lusin", "Karton", "Pack", "Botol", "Sachet", "Lainnya")

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Satuan") },
            modifier = Modifier.fillMaxWidth().testTag("add_prod_satuan"),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            predefinedUnits.forEach { unit ->
                val isSelected = value.equals(unit, ignoreCase = true)
                val chipColor = if (isSelected) OrangePrimary else Color.LightGray.copy(alpha = 0.2f)
                val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

                Box(
                    modifier = Modifier
                        .background(color = chipColor, shape = RoundedCornerShape(16.dp))
                        .clickable { onValueChange(unit) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = unit,
                        fontSize = 12.sp,
                        color = textColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
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
