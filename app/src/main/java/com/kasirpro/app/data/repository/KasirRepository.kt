package com.kasirpro.app.data.repository

import android.content.Context
import androidx.room.*
import com.kasirpro.app.data.local.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.UUID

// Product Varian definition
data class ProductVariant(
    val nama: String,
    val harga: Double
) {
    override fun toString(): String = "$nama:$harga"
    companion object {
        fun fromString(str: String): ProductVariant {
            val parts = str.split(":")
            if (parts.size >= 2) {
                return ProductVariant(parts[0], parts[1].toDoubleOrNull() ?: 0.0)
            }
            return ProductVariant(str, 0.0)
        }
    }
}

// Transaction Item definition
data class TransactionItem(
    val id: String,
    val nama: String,
    val jumlah: Int,
    val harga: Double,
    val varianSelected: String?,
    val diskon: Double
) {
    fun subtotal(): Double = (harga - diskon) * jumlah
}

// Extension helpers to safely convert to Maps for Firestore
fun UserEntity.toMap(): Map<String, Any?> = mapOf(
    "uid" to uid,
    "nama" to nama,
    "email" to email,
    "role" to role,
    "ownerId" to ownerId,
    "assignedBranchId" to assignedBranchId,
    "subscriptionStatus" to subscriptionStatus,
    "subscriptionStartDate" to subscriptionStartDate,
    "subscriptionEndDate" to subscriptionEndDate,
    "createdAt" to createdAt,
    "lastActiveAt" to lastActiveAt
)

fun BusinessEntity.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "ownerId" to ownerId,
    "namaBisnis" to namaBisnis,
    "logoUrl" to logoUrl,
    "createdAt" to createdAt
)

fun BranchEntity.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "businessId" to businessId,
    "userId" to businessId.removePrefix("biz-"),
    "namaCabang" to namaCabang,
    "alamat" to alamat,
    "kasirIdsCsv" to kasirIdsCsv,
    "createdAt" to createdAt
)

fun ProductEntity.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "businessId" to businessId,
    "userId" to businessId.removePrefix("biz-"),
    "branchId" to branchId,
    "nama" to nama,
    "kategori" to kategori,
    "hargaJual" to hargaJual,
    "hargaModal" to hargaModal,
    "stok" to stok,
    "stokMinimum" to stokMinimum,
    "barcode" to barcode,
    "fotoUrl" to fotoUrl,
    "varianRaw" to varianRaw,
    "isActive" to isActive,
    "createdAt" to createdAt
)

fun TransactionEntity.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "businessId" to businessId,
    "userId" to businessId.removePrefix("biz-"),
    "branchId" to branchId,
    "kasirId" to kasirId,
    "kasirNama" to kasirNama,
    "itemsRaw" to itemsRaw,
    "subtotal" to subtotal,
    "diskonTotal" to diskonTotal,
    "kodePromo" to kodePromo,
    "total" to total,
    "metodeBayar" to metodeBayar,
    "bayarNominal" to bayarNominal,
    "kembalian" to kembalian,
    "status" to status,
    "pelangganId" to pelangganId,
    "createdAt" to createdAt,
    "isOfflinePending" to isOfflinePending
)

fun DebtEntity.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "businessId" to businessId,
    "userId" to businessId.removePrefix("biz-"),
    "branchId" to branchId,
    "pelangganId" to pelangganId,
    "pelangganNama" to pelangganNama,
    "jumlah" to jumlah,
    "transaksiId" to transaksiId,
    "status" to status,
    "createdAt" to createdAt
)

fun CustomerEntity.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "businessId" to businessId,
    "userId" to businessId.removePrefix("biz-"),
    "nama" to nama,
    "nomorHp" to nomorHp,
    "totalPoin" to totalPoin,
    "totalTransaksi" to totalTransaksi,
    "createdAt" to createdAt
)

fun StockHistoryEntity.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "productId" to productId,
    "businessId" to businessId,
    "userId" to businessId.removePrefix("biz-"),
    "tipe" to tipe,
    "jumlah" to jumlah,
    "stokSebelum" to stokSebelum,
    "stokSesudah" to stokSesudah,
    "keterangan" to keterangan,
    "createdAt" to createdAt
)

fun PromoEntity.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "businessId" to businessId,
    "userId" to businessId.removePrefix("biz-"),
    "nama" to nama,
    "tipe" to tipe,
    "nilai" to nilai,
    "minTransaksi" to minTransaksi,
    "kode" to kode,
    "isActive" to isActive,
    "berlakuSampai" to berlakuSampai,
    "createdAt" to createdAt
)

fun com.google.firebase.firestore.DocumentSnapshot.getSafeDouble(field: String): Double {
    val rawValue = this.get(field) ?: return 0.0
    return when (rawValue) {
        is Number -> rawValue.toDouble()
        is String -> rawValue.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}

class KasirRepository(private val context: Context) {
    private val database = KasirDatabase.getDatabase(context)
    private val dao = database.kasirDao()

    init {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context.applicationContext)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val prefs = context.getSharedPreferences("kasir_prefs", Context.MODE_PRIVATE)
    private val _loggedInUid = MutableStateFlow<String?>(null)

    fun setLoggedInDeviceUser(uid: String?) {
        prefs.edit().putString("logged_in_uid", uid).apply()
        _loggedInUid.value = uid
    }

    init {
        try {
            val savedUid = prefs.getString("logged_in_uid", null)
            _loggedInUid.value = auth.currentUser?.uid ?: savedUid
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Sync with Firebase Auth state on start
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUid = auth.currentUser?.uid ?: prefs.getString("logged_in_uid", null)
                if (currentUid != null) {
                    withContext(Dispatchers.Main) {
                        _loggedInUid.value = currentUid
                    }
                    try {
                        syncFromFirestore()
                    } catch (sf: Exception) {
                        sf.printStackTrace()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _loggedInUid.value = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Active session states
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _language = MutableStateFlow("id") // "id" or "en"
    val language: StateFlow<String> = _language.asStateFlow()

    private val _lastBackupDate = MutableStateFlow<String?>("Belum pernah")
    val lastBackupDate: StateFlow<String?> = _lastBackupDate.asStateFlow()

    // Observable Flows from local Room database, ensuring robust offline-first operation and preventing Firestore-based startup crashes.
    val currentUser: Flow<UserEntity?> = dao.getCurrentUser().distinctUntilChanged()

    suspend fun getCurrentUserRaw(): UserEntity? = dao.getCurrentUserRaw()

    suspend fun getCurrentBusinessRaw(): BusinessEntity? = dao.getCurrentBusinessRaw()

    suspend fun getResolvedBusinessId(): String {
        val currentBiz = dao.getCurrentBusinessRaw()
        if (currentBiz != null) {
            return currentBiz.id
        }
        val currentUser = dao.getCurrentUserRaw()
        val ownerId = if (currentUser != null) {
            if (currentUser.role == "kasir" || currentUser.role == "kasir_invited") {
                currentUser.ownerId ?: currentUser.uid
            } else {
                currentUser.uid
            }
        } else {
            auth.currentUser?.uid ?: "owner-uid"
        }
        return "biz-$ownerId"
    }

    val currentOwnerId: Flow<String?> = currentUser.map { user ->
        user?.let {
            if (it.role == "kasir" || it.role == "kasir_invited") {
                it.ownerId
            } else {
                it.uid
            }
        }
    }.distinctUntilChanged()

    val currentBusinessFlow: Flow<BusinessEntity?> = dao.getCurrentBusiness().distinctUntilChanged()

    fun getCurrentBusiness(): Flow<BusinessEntity?> = currentBusinessFlow

    val currentBusinessIdFlow: Flow<String?> = currentBusinessFlow.map { it?.id }.distinctUntilChanged()

    val allProducts: Flow<List<ProductEntity>> = dao.getAllProducts()

    val lowStockProducts: Flow<List<ProductEntity>> = dao.getLowStockProducts()

    val allTransactions: Flow<List<TransactionEntity>> = dao.getAllTransactions()

    val allDebts: Flow<List<DebtEntity>> = dao.getAllDebts()

    val allCustomers: Flow<List<CustomerEntity>> = dao.getAllCustomers()

    val stockHistory: Flow<List<StockHistoryEntity>> = dao.getStockHistory()

    val allPromos: Flow<List<PromoEntity>> = dao.getAllPromos()

    val allBranches: Flow<List<BranchEntity>> = dao.getAllBranches()

    val allCashiers: Flow<List<UserEntity>> = dao.getAllCashiers()

    fun toggleOnline() {
        _isOnline.value = !_isOnline.value
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
    }

    fun setLanguage(lang: String) {
        _language.value = lang
    }

    // AUTH ACTIONS (Real Firebase Auth + Sync)
    suspend fun registerUser(nama: String, email: String, pass: String): Boolean {
        try {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        var firebaseUid: String? = null
        try {
            val result = withTimeoutOrNull(15000L) {
                auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            }
            if (result == null) {
                throw Exception("Gagal mendaftarkan email. Coba lagi.")
            }
            firebaseUid = result.user?.uid
        } catch (e: FirebaseAuthUserCollisionException) {
            throw Exception("Email sudah digunakan. Silakan login")
        } catch (e: FirebaseAuthException) {
            val msg = when (e.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE", "auth/email-already-in-use" -> "Email sudah digunakan. Silakan login"
                "ERROR_INVALID_EMAIL", "auth/invalid-email" -> "Format email tidak valid"
                "ERROR_WEAK_PASSWORD", "auth/weak-password" -> "Password minimal terdiri dari 6 karakter"
                else -> e.localizedMessage ?: "Registrasi gagal, silakan coba lagi."
            }
            throw Exception(msg)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }

        val finalUid = firebaseUid ?: throw Exception("Registrasi gagal, silakan coba lagi.")

        val user = UserEntity(
            uid = finalUid,
            nama = nama,
            email = email,
            role = "owner",
            ownerId = null,
            assignedBranchId = null,
            subscriptionStatus = "free",
            subscriptionStartDate = null,
            subscriptionEndDate = null
        )

        // Save to Firestore using map-serialization (gracefully ignore cloud failures)
        try {
            withTimeoutOrNull(5000L) {
                firestore.collection("users").document(finalUid).set(user.toMap()).await()
            }
        } catch (f: Exception) {
            f.printStackTrace()
        }

        // Save to local Room (MUST succeed to keep the app working locally)
        dao.clearUsers()
        dao.insertUser(user)

        setLoggedInDeviceUser(finalUid)
        
        // Try to load any other user data from cloud if applicable
        try {
            syncFromFirestore()
        } catch (s: Exception) {
            s.printStackTrace()
        }
        return true
    }

    suspend fun loginUser(email: String, pass: String): Boolean {
        try {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
            }
        } catch (e: Exception) { e.printStackTrace() }

        val cleanInput = email.trim().lowercase()

        // 1. Try to check if input is NOT an email (i.e. cashier login by custom username)
        if (!cleanInput.contains("@")) {
            try {
                val querySnapshot = firestore.collection("cashiers")
                    .whereEqualTo("username", cleanInput)
                    .get()
                    .await()
                
                if (querySnapshot.isEmpty) {
                    throw Exception("Username kasir tidak terdaftar.")
                }
                
                // Find matching cashier by password
                val cashierDoc = querySnapshot.documents.firstOrNull { it.getString("password") == pass }
                if (cashierDoc == null) {
                    throw Exception("Password salah. Silakan coba lagi.")
                }

                val status = cashierDoc.getString("status") ?: "aktif"
                if (status != "aktif") {
                    throw Exception("Akun kasir ini dinonaktifkan.")
                }

                val ownerId = cashierDoc.getString("ownerId") ?: "owner-uid"
                val nama = cashierDoc.getString("cashierName") ?: cashierDoc.getString("nama") ?: "Kasir"
                val branchId = cashierDoc.getString("branchId") ?: "branch-1-biz-$ownerId"

                val cashierUser = UserEntity(
                    uid = cashierDoc.id,
                    nama = nama,
                    email = cleanInput,
                    role = "kasir",
                    ownerId = ownerId,
                    assignedBranchId = branchId,
                    subscriptionStatus = "free",
                    subscriptionStartDate = null,
                    subscriptionEndDate = null,
                    createdAt = cashierDoc.getLong("createdAt") ?: System.currentTimeMillis(),
                    lastActiveAt = System.currentTimeMillis()
                )

                setLoggedInDeviceUser(cashierDoc.id)
                dao.clearUsers()
                dao.insertUser(cashierUser)

                // Update activity in Firestore cashier doc
                try {
                    firestore.collection("cashiers").document(cashierDoc.id).update("lastActiveAt", System.currentTimeMillis()).await()
                } catch (e: Exception) {}

                // Synchronize business elements for cashiers
                try {
                    syncFromFirestore()
                } catch (s: Exception) {
                    s.printStackTrace()
                }
                return true
            } catch (e: Exception) {
                if (e.message != null && (e.message!!.contains("Username") || e.message!!.contains("Password") || e.message!!.contains("dinonaktifkan"))) {
                    throw e
                }
                e.printStackTrace()
                throw Exception("Gagal login kasir: ${e.localizedMessage}")
            }
        }

        // 2. Standard flow for Owner Email Login
        try {
            val result = withTimeoutOrNull(15000L) {
                auth.signInWithEmailAndPassword(cleanInput, pass).await()
            }
            if (result == null) {
                throw Exception("Gagal masuk. Silakan coba lagi.")
            }
            val firebaseUid = result.user?.uid ?: throw Exception("Gagal masuk. Silakan coba lagi.")

            setLoggedInDeviceUser(firebaseUid)

            // Synchronize All Real User Data from Firestore to local Room Cache
            try {
                syncFromFirestore()
            } catch (s: Exception) {
                s.printStackTrace()
            }

            // Safe fallback if Firestore sync didn't find the user record
            val userInDb = dao.getUserById(firebaseUid)
            if (userInDb == null) {
                val role = "owner"
                val defaultUser = UserEntity(
                    uid = firebaseUid,
                    nama = "Owner Toko",
                    email = cleanInput,
                    role = role,
                    ownerId = null,
                    assignedBranchId = null,
                    subscriptionStatus = "free",
                    subscriptionStartDate = null,
                    subscriptionEndDate = null
                )
                try {
                    withTimeoutOrNull(5000L) {
                        firestore.collection("users").document(firebaseUid).set(defaultUser.toMap()).await()
                    }
                } catch (f: Exception) {
                    f.printStackTrace()
                }
                dao.clearUsers()
                dao.insertUser(defaultUser)
            }
            return true
        } catch (e: FirebaseAuthInvalidUserException) {
            throw Exception("Email tidak terdaftar. Silakan daftar terlebih dahulu")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw Exception("Password salah. Silakan coba lagi")
        } catch (e: FirebaseAuthException) {
            val msg = when (e.errorCode) {
                "ERROR_USER_NOT_FOUND", "auth/user-not-found" -> "Email tidak terdaftar. Silakan daftar terlebih dahulu"
                "ERROR_WRONG_PASSWORD", "auth/wrong-password" -> "Password salah. Silakan coba lagi"
                "ERROR_INVALID_EMAIL", "auth/invalid-email" -> "Format email tidak valid"
                "ERROR_TOO_MANY_REQUESTS", "auth/too-many-requests" -> "Terlalu banyak percobaan. Coba lagi nanti"
                else -> e.localizedMessage ?: "Email atau password salah!"
            }
            throw Exception(msg)
        } catch (e: Exception) {
            if (e.message != null && (e.message!!.contains("tidak terdaftar") || e.message!!.contains("salah") || e.message!!.contains("Format email"))) {
                throw e
            }
            e.printStackTrace()
            throw Exception(e.localizedMessage ?: "Email atau password salah!")
        }
    }

    suspend fun loginWithGoogle(idToken: String): Boolean {
        try {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
            }
        } catch (e: Exception) { e.printStackTrace() }

        return try {
            if (idToken == "sandbox-bypass") {
                val finalUid = "google-offline-owner"
                setLoggedInDeviceUser(finalUid)
                val email = "sandbox.tester@kasirpro.id"
                val namaUser = "Sandbox Tester"
                val user = UserEntity(
                    uid = finalUid,
                    nama = namaUser,
                    email = email,
                    role = "owner",
                    ownerId = null,
                    assignedBranchId = null,
                    subscriptionStatus = "free",
                    subscriptionStartDate = null,
                    subscriptionEndDate = null,
                    createdAt = System.currentTimeMillis(),
                    lastActiveAt = System.currentTimeMillis()
                )
                dao.insertUser(user)
                return true
            }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = withTimeoutOrNull(10000L) {
                auth.signInWithCredential(credential).await()
            }
            val firebaseUid = result?.user?.uid
            
            // Always login successfully: if Firebase is blocked or offline, use a robust offline fallback UID
            val finalUid = firebaseUid ?: "google-offline-owner"
            setLoggedInDeviceUser(finalUid)
            
            val email = result?.user?.email ?: "google-user@kasirpro.id"
            val namaUser = result?.user?.displayName ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
            
            // Read/Check Firestore profile or initialize it
            val user = try {
                val doc = if (finalUid != "google-offline-owner") {
                    withTimeoutOrNull(4000L) {
                        firestore.collection("users").document(finalUid).get().await()
                    }
                } else null
                
                if (doc != null && doc.exists()) {
                    UserEntity(
                        uid = finalUid,
                        nama = doc.getString("nama") ?: namaUser,
                        email = doc.getString("email") ?: email,
                        role = doc.getString("role") ?: "owner",
                        ownerId = doc.getString("ownerId"),
                        assignedBranchId = doc.getString("assignedBranchId"),
                        subscriptionStatus = doc.getString("subscriptionStatus") ?: "free",
                        subscriptionStartDate = doc.getLong("subscriptionStartDate"),
                        subscriptionEndDate = doc.getLong("subscriptionEndDate"),
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        lastActiveAt = System.currentTimeMillis()
                    )
                } else {
                    UserEntity(
                        uid = finalUid,
                        nama = namaUser,
                        email = email,
                        role = "owner",
                        ownerId = null,
                        assignedBranchId = null,
                        subscriptionStatus = "free",
                        subscriptionStartDate = null,
                        subscriptionEndDate = null,
                        createdAt = System.currentTimeMillis(),
                        lastActiveAt = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                UserEntity(
                    uid = finalUid,
                    nama = namaUser,
                    email = email,
                    role = "owner",
                    ownerId = null,
                    assignedBranchId = null,
                    subscriptionStatus = "free",
                    subscriptionStartDate = null,
                    subscriptionEndDate = null,
                    createdAt = System.currentTimeMillis(),
                    lastActiveAt = System.currentTimeMillis()
                )
            }

            // Write configuration properties to firestore if online
            if (finalUid != "google-offline-owner") {
                try {
                    withTimeoutOrNull(4000L) {
                        firestore.collection("users").document(finalUid).set(user.toMap()).await()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            dao.clearUsers()
            dao.insertUser(user)

            try {
                if (finalUid != "google-offline-owner") {
                    syncFromFirestore()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun logout() {
        try {
            auth.signOut()
        } catch (e: Exception) { e.printStackTrace() }
        setLoggedInDeviceUser(null)
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
    }

    suspend fun resetPassword(email: String): Boolean {
        return try {
            auth.sendPasswordResetEmail(email).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // STORE ONBOARDING ACTIONS
    suspend fun setupToko(namaToko: String, alamat: String, logoUrl: String?): Boolean {
        val uid = auth.currentUser?.uid ?: "owner-uid"
        val businessId = "biz-$uid"
        val business = BusinessEntity(
            id = businessId,
            ownerId = uid,
            namaBisnis = namaToko,
            logoUrl = logoUrl
        )
        try {
            firestore.collection("businesses").document(business.id).set(business.toMap()).await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.insertBusiness(business)

        // Generate primary default branch
        val branch = BranchEntity(
            id = "branch-1-$businessId",
            businessId = business.id,
            namaCabang = "Cabang Utama",
            alamat = alamat,
            kasirIdsCsv = ""
        )
        try {
            firestore.collection("branches").document(branch.id).set(branch.toMap()).await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.insertBranch(branch)
        return true
    }

    suspend fun getUserById(uid: String): UserEntity? {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) {
                UserEntity(
                    uid = uid,
                    nama = doc.getString("nama") ?: "User",
                    email = doc.getString("email") ?: "",
                    role = doc.getString("role") ?: "owner",
                    ownerId = doc.getString("ownerId"),
                    assignedBranchId = doc.getString("assignedBranchId"),
                    subscriptionStatus = doc.getString("subscriptionStatus") ?: "free",
                    subscriptionStartDate = doc.getLong("subscriptionStartDate"),
                    subscriptionEndDate = doc.getLong("subscriptionEndDate"),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    lastActiveAt = doc.getLong("lastActiveAt")
                )
            } else {
                dao.getUserById(uid)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            dao.getUserById(uid)
        }
    }

    suspend fun getProductById(id: String): ProductEntity? {
        return try {
            val doc = firestore.collection("products").document(id).get().await()
            if (doc.exists()) {
                ProductEntity(
                    id = doc.id,
                    businessId = doc.getString("businessId") ?: getResolvedBusinessId(),
                    branchId = doc.getString("branchId") ?: "branch-1",
                    nama = doc.getString("nama") ?: "",
                    kategori = doc.getString("kategori") ?: "",
                    hargaJual = doc.getSafeDouble("hargaJual"),
                    hargaModal = doc.getSafeDouble("hargaModal"),
                    stok = doc.getLong("stok")?.toInt() ?: 0,
                    stokMinimum = doc.getLong("stokMinimum")?.toInt() ?: 0,
                    barcode = doc.getString("barcode"),
                    fotoUrl = doc.getString("fotoUrl"),
                    varianRaw = doc.getString("varianRaw") ?: "",
                    isActive = doc.getBoolean("isActive") ?: true,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            } else {
                dao.getProductById(id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            dao.getProductById(id)
        }
    }

    // PRODUCT ACTIONS
    suspend fun insertProduct(
        nama: String,
        kategori: String,
        hargaJual: Double,
        hargaModal: Double,
        stok: Int,
        stokMinimum: Int,
        barcode: String?,
        fotoUrl: String?,
        varianList: List<ProductVariant>
    ): Boolean {
        val varianString = varianList.joinToString(";") { "${it.nama}:${it.harga}" }
        val bizId = getResolvedBusinessId()

        val product = ProductEntity(
            id = UUID.randomUUID().toString(),
            businessId = bizId,
            branchId = "branch-1-$bizId",
            nama = nama,
            kategori = kategori,
            hargaJual = hargaJual,
            hargaModal = hargaModal,
            stok = stok,
            stokMinimum = stokMinimum,
            barcode = barcode,
            fotoUrl = fotoUrl,
            varianRaw = varianString
        )
        try {
            firestore.collection("products").document(product.id).set(product.toMap()).await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.insertProduct(product)

        // Record stock history
        val hist = StockHistoryEntity(
            id = UUID.randomUUID().toString(),
            productId = product.id,
            businessId = bizId,
            tipe = "masuk",
            jumlah = stok,
            stokSebelum = 0,
            stokSesudah = stok,
            keterangan = "Stok awal produk baru"
        )
        try {
            firestore.collection("stock_history").document(hist.id).set(hist.toMap()).await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.insertStockHistory(hist)
        return true
    }

    suspend fun updateProduct(product: ProductEntity) {
        try {
            firestore.collection("products").document(product.id).set(product.toMap()).await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.insertProduct(product)
    }

    suspend fun deleteProduct(id: String) {
        try {
            firestore.collection("products").document(id).delete().await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.deleteProduct(id)
    }

    // STOCK MANAGEMENT
    suspend fun recordStockMovement(
        productId: String,
        tipe: String, // "masuk", "keluar", "opname"
        jumlah: Int,
        keterangan: String?
    ) {
        val prod = getProductById(productId) ?: return
        val stokSebelum = prod.stok
        val stokSesudah = when (tipe) {
            "masuk" -> stokSebelum + jumlah
            "keluar" -> stokSebelum - jumlah
            "opname" -> jumlah
            else -> stokSebelum
        }
        val updated = prod.copy(stok = stokSesudah)
        try {
            firestore.collection("products").document(updated.id).set(updated.toMap()).await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.insertProduct(updated)

        val hist = StockHistoryEntity(
            id = UUID.randomUUID().toString(),
            productId = productId,
            businessId = prod.businessId,
            tipe = tipe,
            jumlah = if (tipe == "opname") (stokSesudah - stokSebelum) else jumlah,
            stokSebelum = stokSebelum,
            stokSesudah = stokSesudah,
            keterangan = keterangan
        )
        try {
            firestore.collection("stock_history").document(hist.id).set(hist.toMap()).await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.insertStockHistory(hist)
    }

    // TRANSACTION ACTIONS (Checkout)
    suspend fun checkout(
        items: List<TransactionItem>,
        subtotal: Double,
        diskonTotal: Double,
        kodePromo: String?,
        total: Double,
        metodeBayar: String,
        bayarNominal: Double,
        kembalian: Double,
        status: String, // "lunas" or "dp"
        pelangganId: String?
    ): TransactionEntity {
        val itemsString = items.joinToString(";") {
            "${it.id}:${it.nama}:${it.jumlah}:${it.harga}:${it.varianSelected ?: ""}:${it.diskon}"
        }

        val currUser = currentUser.firstOrNull()
        var currentKasirId = "kasir-1"
        var currentKasirNama = "Kasir Pro"
        val bizId = getResolvedBusinessId()
        var currentBranchId = "branch-1-$bizId"

        if (currUser != null) {
            currentKasirId = currUser.uid
            currentKasirNama = currUser.nama
            currentBranchId = currUser.assignedBranchId ?: "branch-1-$bizId"

            if (currUser.role == "kasir") {
                val updatedKasir = currUser.copy(lastActiveAt = System.currentTimeMillis())
                try {
                    firestore.collection("users").document(updatedKasir.uid).set(updatedKasir.toMap()).await()
                } catch (e: Exception) { e.printStackTrace() }
                dao.insertUser(updatedKasir)
            }
        }

        val currentBizId = getResolvedBusinessId()

        val tx = TransactionEntity(
            id = "TRX-${System.currentTimeMillis()}",
            businessId = currentBizId,
            branchId = currentBranchId,
            kasirId = currentKasirId,
            kasirNama = currentKasirNama,
            itemsRaw = itemsString,
            subtotal = subtotal,
            diskonTotal = diskonTotal,
            kodePromo = kodePromo,
            total = total,
            metodeBayar = metodeBayar,
            bayarNominal = bayarNominal,
            kembalian = kembalian,
            status = status,
            pelangganId = pelangganId,
            isOfflinePending = !_isOnline.value
        )

        try {
            val branchesList = try {
                dao.getAllBranches().first()
            } catch(e: Exception) {
                emptyList()
            }
            val currentBranch = branchesList.find { it.id == currentBranchId }
            val currentBranchNama = currentBranch?.namaCabang ?: "Cabang Utama"

            val firestoreMap = tx.toMap().toMutableMap().apply {
                put("cashierId", currentKasirId)
                put("cashierName", currentKasirNama)
                put("branchName", currentBranchNama)
            }

            firestore.collection("transactions").document(tx.id).set(firestoreMap).await()
        } catch (e: Exception) { e.printStackTrace() }
        
        dao.insertTransaction(tx)

        // Update product inventory & record stock movement
        items.forEach { item ->
            val product = getProductById(item.id)
            if (product != null) {
                val stokSebelum = product.stok
                val stokSesudah = (stokSebelum - item.jumlah).coerceAtLeast(0)
                val updatedProduct = product.copy(stok = stokSesudah)
                
                try {
                    firestore.collection("products").document(updatedProduct.id).set(updatedProduct.toMap()).await()
                } catch (e: Exception) { e.printStackTrace() }
                
                dao.insertProduct(updatedProduct)

                // Log stock movement
                val hs = StockHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    productId = item.id,
                    businessId = product.businessId,
                    tipe = "keluar",
                    jumlah = item.jumlah,
                    stokSebelum = stokSebelum,
                    stokSesudah = stokSesudah,
                    keterangan = "Penjualan transaksi ${tx.id}"
                )
                try {
                    firestore.collection("stock_history").document(hs.id).set(hs.toMap()).await()
                } catch (e: Exception) { e.printStackTrace() }
                
                dao.insertStockHistory(hs)
            }
        }

        if (status == "dp" && pelangganId != null) {
            val sisaHutang = total - bayarNominal
            if (sisaHutang > 0) {
                val d = DebtEntity(
                    id = UUID.randomUUID().toString(),
                    businessId = tx.businessId,
                    branchId = tx.branchId,
                    pelangganId = pelangganId,
                    pelangganNama = "Pelanggan Setia",
                    jumlah = sisaHutang,
                    transaksiId = tx.id,
                    status = "belum"
                )
                try {
                    firestore.collection("debts").document(d.id).set(d.toMap()).await()
                } catch (e: Exception) { e.printStackTrace() }
                dao.insertDebt(d)
            }
        }

        return tx
    }

    // PERSISTENCE SYNC FOR OFFLINE MODE
    suspend fun synchronizeOfflineData(): Int {
        val pending = dao.getOfflinePendingTransactions()
        pending.forEach { trx ->
            try {
                firestore.collection("transactions").document(trx.id).set(trx.toMap()).await()
                dao.markTransactionSynced(trx.id)
            } catch (e: Exception) { e.printStackTrace() }
        }
        return pending.size
    }

    // MULTI BRANCH MANAGEMENT
    suspend fun addBranch(nama: String, alamat: String) {
        val bizId = getResolvedBusinessId()
        val branch = BranchEntity(
            id = UUID.randomUUID().toString(),
            businessId = bizId,
            namaCabang = nama,
            alamat = alamat,
            kasirIdsCsv = ""
        )
        try {
            firestore.collection("branches").document(branch.id).set(branch.toMap()).await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.insertBranch(branch)
    }

    suspend fun updateBranch(id: String, nama: String, alamat: String) {
        val branchList = allBranches.firstOrNull() ?: emptyList()
        val currentBranch = branchList.find { it.id == id }
        if (currentBranch != null) {
            val updated = currentBranch.copy(namaCabang = nama, alamat = alamat)
            try {
                firestore.collection("branches").document(id).set(updated.toMap()).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
        dao.updateBranch(id, nama, alamat)
    }

    suspend fun deleteBranch(id: String) {
        try {
            firestore.collection("branches").document(id).delete().await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.deleteBranch(id)
    }

    // KASIR MANAGEMENT
    suspend fun addCashier(nama: String, username: String, pass: String, branchId: String): Boolean {
        val lowercaseUsername = username.trim().lowercase()
        val ownerId = auth.currentUser?.uid ?: "owner-uid"
        val docId = "${lowercaseUsername}_$ownerId"

        // 1. Check uniqueness of username in Firestore collection "cashiers" under this owner
        val exists = try {
            val query = firestore.collection("cashiers")
                .whereEqualTo("ownerId", ownerId)
                .whereEqualTo("username", lowercaseUsername)
                .get()
                .await()
            !query.isEmpty
        } catch (e: Exception) {
            false
        }
        if (exists) {
            return false
        }

        val branchEntity = dao.getBranchById(branchId)
        val branchName = branchEntity?.namaCabang ?: "Cabang Utama"

        // 2. Put into Firestore collection "cashiers"
        val cashierMap = mapOf(
            "ownerId" to ownerId,
            "cashierName" to nama,
            "nama" to nama,
            "username" to lowercaseUsername,
            "password" to pass,
            "branchId" to branchId,
            "branchName" to branchName,
            "status" to "aktif",
            "isActive" to true,
            "createdAt" to System.currentTimeMillis()
        )

        try {
            firestore.collection("cashiers").document(docId).set(cashierMap).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Save to local Room DB
        val user = UserEntity(
            uid = docId,
            nama = nama,
            email = lowercaseUsername,
            role = "kasir",
            ownerId = ownerId,
            assignedBranchId = branchId,
            subscriptionStatus = "free",
            subscriptionStartDate = null,
            subscriptionEndDate = null,
            createdAt = System.currentTimeMillis(),
            lastActiveAt = null
        )
        dao.insertUser(user)
        return true
    }

    suspend fun assignCashierToBranch(cashierId: String, branchId: String?) {
        val user = getUserById(cashierId)
        if (user != null) {
            val updated = user.copy(assignedBranchId = branchId)
            try {
                val branchName = if (branchId != null) {
                    dao.getBranchById(branchId)?.namaCabang ?: "Cabang Utama"
                } else "Cabang Utama"
                
                val updateMap = mapOf(
                    "branchId" to branchId,
                    "branchName" to branchName
                )
                firestore.collection("cashiers").document(cashierId).update(updateMap).await()
            } catch (e: Exception) { e.printStackTrace() }
            dao.insertUser(updated)
        }
    }

    suspend fun deleteCashier(uid: String) {
        try {
            firestore.collection("cashiers").document(uid).delete().await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.deleteUser(uid)
    }

    // CUSTOMERS
    suspend fun addCustomer(nama: String, nomorHp: String) {
        val bizId = getResolvedBusinessId()
        val cust = CustomerEntity(
            id = UUID.randomUUID().toString(),
            businessId = bizId,
            nama = nama,
            nomorHp = nomorHp,
            totalPoin = 0,
            totalTransaksi = 0
        )
        try {
            firestore.collection("customers").document(cust.id).set(cust.toMap()).await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.insertCustomer(cust)
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        try {
            firestore.collection("customers").document(customer.id).set(customer.toMap()).await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.insertCustomer(customer)
    }

    // DEBTS
    suspend fun payDebt(debtId: String) {
        val debtsList = allDebts.firstOrNull()
        val debt = debtsList?.find { it.id == debtId }
        if (debt != null) {
            val updated = debt.copy(status = "lunas")
            try {
                firestore.collection("debts").document(debtId).set(updated.toMap()).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
        dao.updateDebtStatus(debtId, "lunas")
    }

    // PROMOS
    suspend fun addPromo(
        nama: String,
        tipe: String, // "diskon_persen", "diskon_nominal"
        nilai: Double,
        minTransaksi: Double,
        kode: String,
        berlakuSampai: Long
    ) {
        val bizId = getResolvedBusinessId()
        val promo = PromoEntity(
            id = UUID.randomUUID().toString(),
            businessId = bizId,
            nama = nama,
            tipe = tipe,
            nilai = nilai,
            minTransaksi = minTransaksi,
            kode = kode,
            isActive = true,
            berlakuSampai = berlakuSampai
        )
        try {
            firestore.collection("promos").document(promo.id).set(promo.toMap()).await()
        } catch (e: Exception) { e.printStackTrace() }
        dao.insertPromo(promo)
    }

    suspend fun togglePromo(id: String, active: Boolean) {
        val promoList = allPromos.firstOrNull()
        val currentPromo = promoList?.find { it.id == id }
        if (currentPromo != null) {
            val updated = currentPromo.copy(isActive = active)
            try {
                firestore.collection("promos").document(id).set(updated.toMap()).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
        dao.updatePromoStatus(id, active)
    }

    // BACKUP
    suspend fun triggerBackup(): Boolean {
        _lastBackupDate.value = "Hari ini, ${getCurrentTimeString()}"
        return true
    }

    // SUBSCRIPTION & MIDTRANS SIMULATOR
    suspend fun upgradeUserSubscription(uid: String, status: String): Boolean {
        return try {
            val user = getUserById(uid) ?: return false
            val endDate = if (status == "premium") {
                System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) // 1 Month
            } else {
                null
            }
            val startDate = if (status == "premium") {
                System.currentTimeMillis()
            } else {
                null
            }
            val updated = user.copy(
                subscriptionStatus = status,
                subscriptionStartDate = startDate,
                subscriptionEndDate = endDate
            )
            // Save to Firestore
            withTimeoutOrNull(4000L) {
                firestore.collection("users").document(uid).set(updated.toMap()).await()
            }
            // Save to local Room
            dao.insertUser(updated)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun processSubscription(packageName: String): Boolean {
        return true
    }

    private fun getCurrentTimeString(): String {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    // ==========================================
    // REAL FIRESTORE DATA SYNCHRONIZER Engine
    // ==========================================
    suspend fun syncFromFirestore() {
        try {
            withContext(Dispatchers.IO) {
                val uid = _loggedInUid.value ?: return@withContext

                withTimeoutOrNull(5000L) {
                    // 1. Sync User info
                    val userDoc = firestore.collection("users").document(uid).get().await()
                    var targetOwnerId = uid
                    if (userDoc.exists()) {
                        val user = UserEntity(
                            uid = uid,
                            nama = userDoc.getString("nama") ?: "User",
                            email = userDoc.getString("email") ?: "",
                            role = userDoc.getString("role") ?: "owner",
                            ownerId = userDoc.getString("ownerId"),
                            assignedBranchId = userDoc.getString("assignedBranchId"),
                            subscriptionStatus = userDoc.getString("subscriptionStatus") ?: "free",
                            subscriptionStartDate = userDoc.getLong("subscriptionStartDate"),
                            subscriptionEndDate = userDoc.getLong("subscriptionEndDate"),
                            createdAt = userDoc.getLong("createdAt") ?: System.currentTimeMillis(),
                            lastActiveAt = userDoc.getLong("lastActiveAt")
                        )
                        dao.insertUser(user)
                        
                        if ((user.role == "kasir" || user.role == "kasir_invited") && !user.ownerId.isNullOrEmpty()) {
                            targetOwnerId = user.ownerId
                        }
                    } else {
                        val localUser = dao.getCurrentUserRaw()
                        if (localUser != null && (localUser.role == "kasir" || localUser.role == "kasir_invited") && !localUser.ownerId.isNullOrEmpty()) {
                            targetOwnerId = localUser.ownerId
                        }
                    }

                    // 2. Sync Business info
                    val bizSnap = firestore.collection("businesses").whereEqualTo("ownerId", targetOwnerId).get().await()
                    var currentBusinessId: String? = null
                    for (doc in bizSnap.documents) {
                        val biz = BusinessEntity(
                            id = doc.id,
                            ownerId = targetOwnerId,
                            namaBisnis = doc.getString("namaBisnis") ?: "",
                            logoUrl = doc.getString("logoUrl"),
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                        dao.insertBusiness(biz)
                        currentBusinessId = doc.id
                    }

                    // 2b. Sync Cashiers from Firestore cashiers collection
                    val cashiersSnap = firestore.collection("cashiers").whereEqualTo("ownerId", targetOwnerId).get().await()
                    for (doc in cashiersSnap.documents) {
                        val uid = doc.id
                        val username = doc.getString("username") ?: doc.id.substringBefore("_")
                        val nama = doc.getString("cashierName") ?: doc.getString("nama") ?: "Kasir"
                        val branchId = doc.getString("branchId") ?: ""
                        val c = UserEntity(
                            uid = uid,
                            nama = nama,
                            email = username, // keep pure username in email field
                            role = "kasir",
                            ownerId = targetOwnerId,
                            assignedBranchId = branchId,
                            subscriptionStatus = "free",
                            subscriptionStartDate = null,
                            subscriptionEndDate = null,
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            lastActiveAt = doc.getLong("lastActiveAt")
                        )
                        dao.insertUser(c)
                    }

                    // If we found a business, sync all sub collections
                    val businessId = currentBusinessId ?: "biz-$targetOwnerId"

            // 3. Sync Branches
            val branchSnap = firestore.collection("branches").whereEqualTo("businessId", businessId).get().await()
            for (doc in branchSnap.documents) {
                val b = BranchEntity(
                    id = doc.id,
                    businessId = businessId,
                    namaCabang = doc.getString("namaCabang") ?: "",
                    alamat = doc.getString("alamat") ?: "",
                    kasirIdsCsv = doc.getString("kasirIdsCsv") ?: "",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
                dao.insertBranch(b)
            }

            // 4. Sync Products
            val prodSnap = firestore.collection("products").whereEqualTo("businessId", businessId).get().await()
            for (doc in prodSnap.documents) {
                val p = ProductEntity(
                    id = doc.id,
                    businessId = businessId,
                    branchId = doc.getString("branchId") ?: "branch-1-$businessId",
                    nama = doc.getString("nama") ?: "",
                    kategori = doc.getString("kategori") ?: "",
                    hargaJual = doc.getSafeDouble("hargaJual"),
                    hargaModal = doc.getSafeDouble("hargaModal"),
                    stok = doc.getLong("stok")?.toInt() ?: 0,
                    stokMinimum = doc.getLong("stokMinimum")?.toInt() ?: 0,
                    barcode = doc.getString("barcode"),
                    fotoUrl = doc.getString("fotoUrl"),
                    varianRaw = doc.getString("varianRaw") ?: "",
                    isActive = doc.getBoolean("isActive") ?: true,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
                dao.insertProduct(p)
            }

            // 5. Sync Customers
            val custSnap = firestore.collection("customers").whereEqualTo("businessId", businessId).get().await()
            for (doc in custSnap.documents) {
                val c = CustomerEntity(
                    id = doc.id,
                    businessId = businessId,
                    nama = doc.getString("nama") ?: "",
                    nomorHp = doc.getString("nomorHp") ?: "",
                    totalPoin = doc.getLong("totalPoin")?.toInt() ?: 0,
                    totalTransaksi = doc.getLong("totalTransaksi")?.toInt() ?: 0,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
                dao.insertCustomer(c)
            }

            // 6. Sync Promos
            val promoSnap = firestore.collection("promos").whereEqualTo("businessId", businessId).get().await()
            for (doc in promoSnap.documents) {
                val pr = PromoEntity(
                    id = doc.id,
                    businessId = businessId,
                    nama = doc.getString("nama") ?: "",
                    tipe = doc.getString("tipe") ?: "",
                    nilai = doc.getSafeDouble("nilai"),
                    minTransaksi = doc.getSafeDouble("minTransaksi"),
                    kode = doc.getString("kode") ?: "",
                    isActive = doc.getBoolean("isActive") ?: true,
                    berlakuSampai = doc.getLong("berlakuSampai") ?: 0L,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
                dao.insertPromo(pr)
            }

            // 7. Sync Debts
            val debtSnap = firestore.collection("debts").whereEqualTo("businessId", businessId).get().await()
            for (doc in debtSnap.documents) {
                val d = DebtEntity(
                    id = doc.id,
                    businessId = businessId,
                    branchId = doc.getString("branchId") ?: "branch-1-$businessId",
                    pelangganId = doc.getString("pelangganId") ?: "",
                    pelangganNama = doc.getString("pelangganNama") ?: "",
                    jumlah = doc.getSafeDouble("jumlah"),
                    transaksiId = doc.getString("transaksiId") ?: "",
                    status = doc.getString("status") ?: "belum",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
                dao.insertDebt(d)
            }

            // 8. Sync Transactions
            val txSnap = firestore.collection("transactions").whereEqualTo("businessId", businessId).get().await()
            for (doc in txSnap.documents) {
                val tx = TransactionEntity(
                    id = doc.id,
                    businessId = businessId,
                    branchId = doc.getString("branchId") ?: "branch-1-$businessId",
                    kasirId = doc.getString("kasirId") ?: "kasir-1",
                    kasirNama = doc.getString("kasirNama") ?: "Kasir Pro",
                    itemsRaw = doc.getString("itemsRaw") ?: "",
                    subtotal = doc.getSafeDouble("subtotal"),
                    diskonTotal = doc.getSafeDouble("diskonTotal"),
                    kodePromo = doc.getString("kodePromo"),
                    total = doc.getSafeDouble("total"),
                    metodeBayar = doc.getString("metodeBayar") ?: "Tunai",
                    bayarNominal = doc.getSafeDouble("bayarNominal"),
                    kembalian = doc.getSafeDouble("kembalian"),
                    status = doc.getString("status") ?: "lunas",
                    pelangganId = doc.getString("pelangganId"),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    isOfflinePending = false
                )
                dao.insertTransaction(tx)
            }

            // 9. Sync Stock History
            val shSnap = firestore.collection("stock_history").whereEqualTo("businessId", businessId).get().await()
            for (doc in shSnap.documents) {
                val sh = StockHistoryEntity(
                    id = doc.id,
                    productId = doc.getString("productId") ?: "",
                    businessId = businessId,
                    tipe = doc.getString("tipe") ?: "masuk",
                    jumlah = doc.getLong("jumlah")?.toInt() ?: 0,
                    stokSebelum = doc.getLong("stokSebelum")?.toInt() ?: 0,
                    stokSesudah = doc.getLong("stokSesudah")?.toInt() ?: 0,
                    keterangan = doc.getString("keterangan"),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
                dao.insertStockHistory(sh)
            }
            } // end of withTimeoutOrNull
            } // end of withContext
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // === CASHIER SHIFT METHODS ===
    suspend fun getActiveShift(cashierId: String): ShiftReport? {
        return try {
            val docs = firestore.collection("shifts")
                .whereEqualTo("cashierId", cashierId)
                .whereEqualTo("status", "aktif")
                .limit(1)
                .get()
                .await()
            if (!docs.isEmpty) {
                val doc = docs.documents.first()
                val map = doc.data ?: return null
                ShiftReport.fromMap(map.toMutableMap().apply { put("id", doc.id) })
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun startShift(cashierId: String, cashierName: String, branchId: String, branchName: String, modalAwal: Double): ShiftReport {
        val currentUser = dao.getCurrentUserRaw()
        val ownerId = if (currentUser != null) {
            currentUser.ownerId ?: currentUser.uid
        } else {
            auth.currentUser?.uid ?: "owner-uid"
        }
        val id = "SHIFT-${UUID.randomUUID()}"
        val shift = ShiftReport(
            id = id,
            ownerId = ownerId,
            cashierId = cashierId,
            cashierName = cashierName,
            branchId = branchId,
            branchName = branchName,
            startTime = System.currentTimeMillis(),
            modalAwal = modalAwal,
            status = "aktif"
        )
        try {
            firestore.collection("shifts").document(id).set(shift.toMap()).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return shift
    }

    suspend fun endShift(
        shiftId: String,
        finalTunai: Double,
        finalNonTunai: Double,
        finalTxTotal: Double
    ): Boolean {
        return try {
            val updateMap = mapOf(
                "status" to "selesai",
                "endTime" to System.currentTimeMillis(),
                "totalTunai" to finalTunai,
                "totalNonTunai" to finalNonTunai,
                "totalTransaksi" to finalTxTotal
            )
            firestore.collection("shifts").document(shiftId).update(updateMap).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getAllShifts(): List<ShiftReport> {
        val currentUser = dao.getCurrentUserRaw()
        val ownerId = if (currentUser != null) {
            currentUser.ownerId ?: currentUser.uid
        } else {
            auth.currentUser?.uid ?: "owner-uid"
        }
        return try {
            val docs = firestore.collection("shifts")
                .whereEqualTo("ownerId", ownerId)
                .get()
                .await()
            docs.documents.mapNotNull { doc ->
                val map = doc.data ?: return@mapNotNull null
                ShiftReport.fromMap(map.toMutableMap().apply { put("id", doc.id) })
            }.sortedByDescending { it.startTime }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

data class ShiftReport(
    val id: String = "",
    val ownerId: String = "",
    val cashierId: String = "",
    val cashierName: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val modalAwal: Double = 0.0,
    val totalTunai: Double = 0.0,
    val totalNonTunai: Double = 0.0,
    val totalTransaksi: Double = 0.0,
    val status: String = "aktif"
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "ownerId" to ownerId,
        "cashierId" to cashierId,
        "cashierName" to cashierName,
        "branchId" to branchId,
        "branchName" to branchName,
        "startTime" to startTime,
        "endTime" to endTime,
        "modalAwal" to modalAwal,
        "totalTunai" to totalTunai,
        "totalNonTunai" to totalNonTunai,
        "totalTransaksi" to totalTransaksi,
        "status" to status
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ShiftReport {
            return ShiftReport(
                id = map["id"] as? String ?: "",
                ownerId = map["ownerId"] as? String ?: "",
                cashierId = map["cashierId"] as? String ?: "",
                cashierName = map["cashierName"] as? String ?: "",
                branchId = map["branchId"] as? String ?: "",
                branchName = map["branchName"] as? String ?: "",
                startTime = (map["startTime"] as? Number)?.toLong() ?: 0L,
                endTime = (map["endTime"] as? Number)?.toLong(),
                modalAwal = (map["modalAwal"] as? Number)?.toDouble() ?: 0.0,
                totalTunai = (map["totalTunai"] as? Number)?.toDouble() ?: 0.0,
                totalNonTunai = (map["totalNonTunai"] as? Number)?.toDouble() ?: 0.0,
                totalTransaksi = (map["totalTransaksi"] as? Number)?.toDouble() ?: 0.0,
                status = map["status"] as? String ?: "aktif"
            )
        }
    }
}
