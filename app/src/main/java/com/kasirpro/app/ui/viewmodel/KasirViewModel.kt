package com.kasirpro.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kasirpro.app.data.local.*
import com.kasirpro.app.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class KasirViewModel(application: Application) : AndroidViewModel(application) {
    val repository = KasirRepository(application)

    init {
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000L) // Wait slightly after startup
            while (true) {
                try {
                    repository.synchronizeOfflineData()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                kotlinx.coroutines.delay(30000L) // Loop every 30 seconds
            }
        }
    }

    // UI Session flags
    val currentUser = repository.currentUser.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )
    
    val currentBusiness = repository.getCurrentBusiness().stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    val products = repository.allProducts.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val lowStockProducts = repository.lowStockProducts.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val transactions = repository.allTransactions.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val debts = repository.allDebts.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val customers = repository.allCustomers.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val stockHistory = repository.stockHistory.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val promos = repository.allPromos.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val branches = repository.allBranches.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val cashiers = repository.allCashiers.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val isOnline = repository.isOnline
    val isDarkMode = repository.isDarkMode
    val language = repository.language
    val lastBackupDate = repository.lastBackupDate

    // Cashier Session Shift definitions
    val activeShift = MutableStateFlow<ShiftReport?>(null)
    val allShifts = MutableStateFlow<List<ShiftReport>>(emptyList())

    // Navigation and Pop-Up dialog states
    val activeScreen = MutableStateFlow("splash") // splash, onboarding, login, register, forgot_password, setup_toko, home, cashier, manage, settings, premium_pricing
    val showLimitPopup = MutableStateFlow<String?>(null) // Contains message of limit reached

    // Core Kasir Cart state
    val cartItems = MutableStateFlow<List<TransactionItem>>(emptyList())
    val selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val appliedPromo = MutableStateFlow<PromoEntity?>(null)
    val splitPaymentEnabled = MutableStateFlow(false)
    val splitPayments = MutableStateFlow<Map<String, Double>>(mapOf("Tunai" to 0.0)) // Payment method to amount map
    val transactionStatus = MutableStateFlow("lunas") // "lunas" or "dp"
    val cashAmountPaid = MutableStateFlow(0.0)
    val selectedPaymentMethod = MutableStateFlow("Tunai") // "Tunai" or "QRIS"

    // Current digital receipt
    val activeReceipt = MutableStateFlow<TransactionEntity?>(null)

    // Dialog & Simulation triggers
    val showScanBarcodeDialog = MutableStateFlow(false)
    val showVarianDialog = MutableStateFlow<ProductEntity?>(null)

    fun loadActiveShift() {
        val curr = currentUser.value
        if (curr != null && curr.role == "kasir") {
            viewModelScope.launch {
                activeShift.value = repository.getActiveShift(curr.uid)
            }
        } else {
            activeShift.value = null
        }
    }

    fun startShift(modalAwal: Double) {
        val curr = currentUser.value ?: return
        viewModelScope.launch {
            val bizId = repository.getResolvedBusinessId()
            val branchId = curr.assignedBranchId ?: "branch-1-$bizId"
            val branchName = branches.value.find { it.id == branchId }?.namaCabang ?: "Cabang Utama"
            val shift = repository.startShift(
                cashierId = curr.uid,
                cashierName = curr.nama,
                branchId = branchId,
                branchName = branchName,
                modalAwal = modalAwal
            )
            activeShift.value = shift
        }
    }

    fun endShift(onEnded: (ShiftReport) -> Unit) {
        val shift = activeShift.value ?: return
        viewModelScope.launch {
            val startTime = shift.startTime
            val endTime = System.currentTimeMillis()
            val cashierId = shift.cashierId
            val branchId = shift.branchId

            val shiftTxs = transactions.value.filter { tx ->
                tx.kasirId == cashierId &&
                tx.branchId == branchId &&
                tx.createdAt in startTime..endTime
            }

            val totalTunai = shiftTxs.filter { it.metodeBayar.contains("Tunai", ignoreCase = true) }.sumOf { it.total }
            val totalNonTunai = shiftTxs.filter { !it.metodeBayar.contains("Tunai", ignoreCase = true) }.sumOf { it.total }
            val totalTransaksi = shiftTxs.sumOf { it.total }
            
            val success = repository.endShift(
                shiftId = shift.id,
                finalTunai = totalTunai,
                finalNonTunai = totalNonTunai,
                finalTxTotal = totalTransaksi
            )
            if (success) {
                val updatedReport = shift.copy(
                    endTime = endTime,
                    totalTunai = totalTunai,
                    totalNonTunai = totalNonTunai,
                    totalTransaksi = totalTransaksi,
                    status = "selesai"
                )
                activeShift.value = null
                onEnded(updatedReport)
            }
        }
    }

    fun loadAllShifts() {
        viewModelScope.launch {
            allShifts.value = repository.getAllShifts()
        }
    }

    init {
        // Automatically sync latest state from Cloud Firestore on start up
        viewModelScope.launch {
            repository.syncFromFirestore()
        }
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    if (user.role == "kasir") {
                        loadActiveShift()
                    } else if (user.role == "owner") {
                        loadAllShifts()
                    }
                } else {
                    activeShift.value = null
                    allShifts.value = emptyList()
                }
            }
        }
    }

    // CART ACTIONS
    fun addToCart(product: ProductEntity, selectedVariant: ProductVariant? = null) {
        val current = cartItems.value.toMutableList()
        val variantName = selectedVariant?.nama
        val finalPrice = selectedVariant?.harga ?: product.hargaJual
        
        val existingIndex = current.indexOfFirst { 
            it.id == product.id && it.varianSelected == variantName 
        }

        if (existingIndex >= 0) {
            val item = current[existingIndex]
            current[existingIndex] = item.copy(jumlah = item.jumlah + 1)
        } else {
            current.add(
                TransactionItem(
                    id = product.id,
                    nama = product.nama,
                    jumlah = 1,
                    harga = finalPrice,
                    varianSelected = variantName,
                    diskon = 0.0,
                    satuan = product.satuan
                )
            )
        }
        cartItems.value = current
    }

    fun updateCartQuantity(item: TransactionItem, newQty: Int) {
        if (newQty <= 0) {
            cartItems.value = cartItems.value.filterNot { 
                it.id == item.id && it.varianSelected == item.varianSelected 
            }
        } else {
            cartItems.value = cartItems.value.map {
                if (it.id == item.id && it.varianSelected == item.varianSelected) {
                    it.copy(jumlah = newQty)
                } else it
            }
        }
    }

    fun applyCartItemDiscount(item: TransactionItem, discountAmount: Double) {
        cartItems.value = cartItems.value.map {
            if (it.id == item.id && it.varianSelected == item.varianSelected) {
                it.copy(diskon = discountAmount)
            } else it
        }
    }

    fun clearCart() {
        cartItems.value = emptyList()
        selectedCustomer.value = null
        appliedPromo.value = null
        cashAmountPaid.value = 0.0
        splitPayments.value = mapOf("Tunai" to 0.0)
        splitPaymentEnabled.value = false
        transactionStatus.value = "lunas"
        selectedPaymentMethod.value = "Tunai"
    }

    // CHECKOUT PROCESS
    fun processCheckout(customDiscountPrice: Double = 0.0) {
        val user = currentUser.value
        val isPremium = user?.subscriptionStatus == "premium"

        // Rule limit validation for Free Tier (Max 50 transactions per month)
        if (!isPremium && transactions.value.size >= 50) {
            showLimitPopup.value = "Batas 50 transaksi bulanan untuk akun Gratis telah tercapai. Upgrade ke Premium untuk transaksi tanpa batas!"
            return
        }

        viewModelScope.launch {
            val items = cartItems.value
            val subtotal = items.sumOf { (it.harga - it.diskon) * it.jumlah }
            val promoDisc = appliedPromo.value?.let { p ->
                if (p.tipe == "diskon_persen") {
                    subtotal * (p.nilai / 100.0)
                } else {
                    p.nilai
                }
            } ?: 0.0
            val diskonTotal = promoDisc + customDiscountPrice
            val total = (subtotal - diskonTotal).coerceAtLeast(0.0)

            val method = if (splitPaymentEnabled.value) {
                splitPayments.value.filter { it.value > 0 }.map { "${it.key}:Rp${it.value}" }.joinToString(", ")
            } else {
                selectedPaymentMethod.value
            }

            val paid = if (splitPaymentEnabled.value) {
                splitPayments.value.values.sum()
            } else if (selectedPaymentMethod.value == "QRIS") {
                total
            } else {
                cashAmountPaid.value
            }

            val change = (paid - total).coerceAtLeast(0.0)

            // Post transaction to Room database and reduce stock inventories
            val tx = repository.checkout(
                items = items,
                subtotal = subtotal,
                diskonTotal = diskonTotal,
                kodePromo = appliedPromo.value?.kode,
                total = total,
                metodeBayar = method,
                bayarNominal = paid,
                kembalian = change,
                status = transactionStatus.value,
                pelangganId = selectedCustomer.value?.id
            )

            // Handle Customer Loyalty Point Update Simulation (1 point per Rp10.000)
            selectedCustomer.value?.let { cust ->
                val addedPoints = (total / 10000).toInt()
                val updatedCustomer = cust.copy(
                    totalPoin = cust.totalPoin + addedPoints,
                    totalTransaksi = cust.totalTransaksi + 1
                )
                repository.updateCustomer(updatedCustomer)
            }

            activeReceipt.value = tx
            clearCart()
        }
    }

    // BUSINESS & STORE ONBOARDING SETUP
    fun onboardingStoreSetup(nama: String, alamat: String, logoUrl: String?) {
        viewModelScope.launch {
            repository.setupToko(nama, alamat, logoUrl)
            activeScreen.value = "home"
        }
    }

    // PRODUCT SAVE ACTION WITH FREE TIER VALIDATION
    fun addProduct(
        nama: String,
        kategori: String,
        hargaJual: Double,
        hargaModal: Double,
        stok: Int,
        stokMinimum: Int,
        barcode: String?,
        fotoUrl: String?,
        varianList: List<ProductVariant>,
        satuan: String = "Pcs"
    ) {
        val user = currentUser.value
        val isPremium = user?.subscriptionStatus == "premium"

        // Constraint Free Tier check: max 10 products
        if (!isPremium && products.value.size >= 10) {
            showLimitPopup.value = "Batas maksimal 10 produk untuk akun Gratis tercapai. Upgrade ke Premium untuk menambah produk tanpa batas!"
            return
        }

        viewModelScope.launch {
            repository.insertProduct(nama, kategori, hargaJual, hargaModal, stok, stokMinimum, barcode, fotoUrl, varianList, satuan)
        }
    }

    fun addProductWithBranch(
        id: String = UUID.randomUUID().toString(),
        nama: String,
        kategori: String,
        hargaJual: Double,
        hargaModal: Double,
        stok: Int,
        stokMinimum: Int,
        barcode: String?,
        fotoUrl: String?,
        branchId: String,
        satuan: String = "Pcs",
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val success = repository.insertProductWithBranch(
                id = id,
                nama = nama,
                kategori = kategori,
                hargaJual = hargaJual,
                hargaModal = hargaModal,
                stok = stok,
                stokMinimum = stokMinimum,
                barcode = barcode,
                fotoUrl = fotoUrl,
                branchId = branchId,
                satuan = satuan
            )
            onComplete(success)
        }
    }

    fun editProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.updateProduct(product)
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            repository.deleteProduct(productId)
        }
    }

    // ADD STOCK TRACKING
    fun updateProductStock(productId: String, type: String, amount: Int, note: String?) {
        viewModelScope.launch {
            repository.recordStockMovement(productId, type, amount, note)
        }
    }

    // PROMOS
    fun addPromo(nama: String, tipe: String, nilai: Double, minTx: Double, kode: String, durationDays: Int) {
        val isPremium = currentUser.value?.subscriptionStatus == "premium"
        if (!isPremium) {
            showLimitPopup.value = "Fitur Promo & Voucher Kupon hanya untuk pengguna Premium. Upgrade sekarang!"
            return
        }
        viewModelScope.launch {
            val berlakuSampai = System.currentTimeMillis() + (durationDays * 24L * 60 * 60 * 1000)
            repository.addPromo(nama, tipe, nilai, minTx, kode, berlakuSampai)
        }
    }

    fun togglePromo(id: String, active: Boolean) {
        val isPremium = currentUser.value?.subscriptionStatus == "premium"
        if (!isPremium) {
            showLimitPopup.value = "Fitur Promo & Voucher Kupon hanya untuk pengguna Premium. Upgrade sekarang!"
            return
        }
        viewModelScope.launch {
            repository.togglePromo(id, active)
        }
    }

    // BRANCHES WITH FREE TIER VALIDATION
    fun addBranch(nama: String, alamat: String) {
        val isPremium = currentUser.value?.subscriptionStatus == "premium"
        if (!isPremium) {
            showLimitPopup.value = "Fitur Multi-Cabang hanya untuk pengguna Premium. Upgrade sekarang!"
            return
        }
        viewModelScope.launch {
            repository.addBranch(nama, alamat)
        }
    }

    fun editBranch(branchId: String, nama: String, alamat: String) {
        viewModelScope.launch {
            repository.updateBranch(branchId, nama, alamat)
        }
    }

    fun removeBranch(branchId: String) {
        viewModelScope.launch {
            repository.deleteBranch(branchId)
        }
    }

    // CASHIER MANAGEMENT METHODS
    fun addCashier(nama: String, username: String, pass: String, branchId: String, onResult: (Boolean, String) -> Unit) {
        val isPremium = currentUser.value?.subscriptionStatus == "premium"
        if (!isPremium) {
            showLimitPopup.value = "Fitur Manajemen Kasir hanya untuk pengguna Premium. Upgrade sekarang!"
            onResult(false, "Fitur Premium")
            return
        }
        viewModelScope.launch {
            val success = repository.addCashier(nama, username, pass, branchId)
            if (success) {
                onResult(true, "Akun kasir berhasil dibuat!")
            } else {
                onResult(false, "Username kasir sudah digunakan atau ada kesalahan jaringan!")
            }
        }
    }

    fun assignCashierToBranch(cashierId: String, branchId: String?) {
        viewModelScope.launch {
            repository.assignCashierToBranch(cashierId, branchId)
        }
    }

    fun deleteCashier(uid: String) {
        viewModelScope.launch {
            repository.deleteCashier(uid)
        }
    }

    // CUSTOMERS WITH FREE TIER VALIDATION
    fun addCustomer(nama: String, hp: String, alamat: String? = null) {
        val isPremium = currentUser.value?.subscriptionStatus == "premium"
        if (!isPremium) {
            showLimitPopup.value = "Fitur Database Pelanggan & Loyalty Poin hanya untuk pengguna Premium!"
            return
        }
        viewModelScope.launch {
            repository.addCustomer(nama, hp, alamat)
        }
    }

    // DEBTS WITH PREMIUM CHECKS
    fun settleDebt(debtId: String) {
        viewModelScope.launch {
            repository.payDebt(debtId)
        }
    }

    // MANUAL BACKUP
    fun runBackup() {
        val isPremium = currentUser.value?.subscriptionStatus == "premium"
        if (!isPremium) {
            showLimitPopup.value = "Backup data otomatis & manual ke cloud hanya didukung untuk tipe akun Premium!"
            return
        }
        viewModelScope.launch {
            repository.triggerBackup()
        }
    }

    // SUB UPGRADE SIMULATION
    fun upgradeToPremium(isYearly: Boolean = false) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            repository.upgradeUserSubscription(user.uid, "premium", isYearly)
            activeScreen.value = "home"
        }
    }

    fun downgradeToFree() {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            repository.upgradeUserSubscription(user.uid, "free")
        }
    }

    // SECTIONS CONTROL
    fun toggleOnlineMode() {
        val isPremium = currentUser.value?.subscriptionStatus == "premium"
        if (!isPremium) {
            showLimitPopup.value = "Mode Offline & Sinkronisasi hanya tersedia bagi pengguna Premium!"
            return
        }
        repository.toggleOnline()
        if (isOnline.value) {
            // Synchronize pending transactions
            viewModelScope.launch {
                repository.synchronizeOfflineData()
            }
        }
    }

    // DATA FORCE REFRESH FROM CLOUD FIRESTORE
    fun populateDemoDataset() {
        viewModelScope.launch {
            repository.syncFromFirestore()
        }
    }

    fun setDarkMode(enabled: Boolean) {
        repository.setDarkMode(enabled)
    }

    fun setLanguage(lang: String) {
        repository.setLanguage(lang)
    }

    fun updateBusinessProfile(namaBisnis: String, alamat: String?, noTelpon: String?, logoUrl: String?, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateBusinessProfile(namaBisnis, alamat, noTelpon, logoUrl)
            onComplete()
        }
    }

    fun getOwnerVerificationCode(): String {
        return repository.getOwnerVerificationCode()
    }

    fun saveOwnerVerificationCode(code: String) {
        repository.saveOwnerVerificationCode(code)
    }

    fun correctTransaction(correctedTx: TransactionEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.correctTransaction(correctedTx)
            onComplete()
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}
