package com.kasirpro.app.data.local

import androidx.room.*

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val nama: String,
    val email: String,
    val role: String, // "owner" or "kasir"
    val ownerId: String?,
    val assignedBranchId: String?,
    val subscriptionStatus: String, // "free" or "premium"
    val subscriptionStartDate: Long?,
    val subscriptionEndDate: Long?,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long? = null
)

@Entity(tableName = "businesses")
data class BusinessEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val namaBisnis: String,
    val logoUrl: String?,
    val alamat: String? = null,
    val noTelpon: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val namaCabang: String,
    val alamat: String,
    val kasirIdsCsv: String, // Comma separated kasir IDs
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val branchId: String,
    val nama: String,
    val kategori: String,
    val hargaJual: Double,
    val hargaModal: Double,
    val stok: Int,
    val stokMinimum: Int,
    val barcode: String?,
    val fotoUrl: String?,
    val varianRaw: String? = "", // JSON payload string containing variant definitions
    val satuan: String = "Pcs",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val branchId: String,
    val kasirId: String,
    val kasirNama: String,
    val itemsRaw: String, // JSON payload representing list of transaction items
    val subtotal: Double,
    val diskonTotal: Double,
    val kodePromo: String?,
    val total: Double,
    val metodeBayar: String, // e.g. "Tunai", "QRIS", "Transfer", split represented as JSON or comma separated
    val bayarNominal: Double,
    val kembalian: Double,
    val status: String, // "lunas" or "dp"
    val pelangganId: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val isOfflinePending: Boolean = false // Track if transaction was made offline and needs sync
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val branchId: String,
    val pelangganId: String,
    val pelangganNama: String,
    val jumlah: Double,
    val transaksiId: String,
    val status: String, // "lunas" or "belum"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val nama: String,
    val nomorHp: String,
    val totalPoin: Int = 0,
    val totalTransaksi: Int = 0,
    val alamat: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stock_history")
data class StockHistoryEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val businessId: String,
    val tipe: String, // "masuk", "keluar", "opname"
    val jumlah: Int,
    val stokSebelum: Int,
    val stokSesudah: Int,
    val keterangan: String?,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "promos")
data class PromoEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val nama: String,
    val tipe: String, // "diskon_persen" or "diskon_nominal"
    val nilai: Double,
    val minTransaksi: Double,
    val kode: String,
    val isActive: Boolean = true,
    val berlakuSampai: Long,
    val createdAt: Long = System.currentTimeMillis()
)
