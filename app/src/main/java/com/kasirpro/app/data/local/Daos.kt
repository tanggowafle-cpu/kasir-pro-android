package com.kasirpro.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KasirDao {
    // === Users ===
    @Query("SELECT * FROM users LIMIT 1")
    fun getCurrentUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUserRaw(): UserEntity?

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getUserById(uid: String): UserEntity?

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    fun getUserByIdFlow(uid: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE role = 'kasir' OR role = 'kasir_invited'")
    fun getAllCashiers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM users WHERE uid = :uid")
    suspend fun deleteUser(uid: String)

    @Query("DELETE FROM users")
    suspend fun clearUsers()

    // === Businesses ===
    @Query("SELECT * FROM businesses WHERE ownerId = :ownerId LIMIT 1")
    fun getBusinessByOwner(ownerId: String): Flow<BusinessEntity?>

    @Query("SELECT * FROM businesses LIMIT 1")
    fun getCurrentBusiness(): Flow<BusinessEntity?>

    @Query("SELECT * FROM businesses LIMIT 1")
    suspend fun getCurrentBusinessRaw(): BusinessEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusiness(business: BusinessEntity)

    // === Branches ===
    @Query("SELECT * FROM branches")
    fun getAllBranches(): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches WHERE id = :id LIMIT 1")
    suspend fun getBranchById(id: String): BranchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranch(branch: BranchEntity)

    @Query("UPDATE branches SET namaCabang = :nama, alamat = :alamat WHERE id = :id")
    suspend fun updateBranch(id: String, nama: String, alamat: String)

    @Query("DELETE FROM branches WHERE id = :id")
    suspend fun deleteBranch(id: String)

    // === Products ===
    @Query("SELECT * FROM products ORDER BY nama ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products")
    suspend fun getAllProductsRaw(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE stok <= stokMinimum AND isActive = 1")
    fun getLowStockProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: String)

    // === Transactions ===
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isOfflinePending = 1")
    suspend fun getOfflinePendingTransactions(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET isOfflinePending = 0 WHERE id = :id")
    suspend fun markTransactionSynced(id: String)

    // === Debts ===
    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    fun getAllDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts")
    suspend fun getAllDebtsRaw(): List<DebtEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtEntity)

    @Query("UPDATE debts SET status = :status WHERE id = :id")
    suspend fun updateDebtStatus(id: String, status: String)

    // === Customers ===
    @Query("SELECT * FROM customers ORDER BY nama ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers")
    suspend fun getAllCustomersRaw(): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    // === Stock History ===
    @Query("SELECT * FROM stock_history ORDER BY createdAt DESC")
    fun getStockHistory(): Flow<List<StockHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockHistory(history: StockHistoryEntity)

    // === Promos ===
    @Query("SELECT * FROM promos ORDER BY createdAt DESC")
    fun getAllPromos(): Flow<List<PromoEntity>>

    @Query("SELECT * FROM promos")
    suspend fun getAllPromosRaw(): List<PromoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPromo(promo: PromoEntity)

    @Query("UPDATE promos SET isActive = :isActive WHERE id = :id")
    suspend fun updatePromoStatus(id: String, isActive: Boolean)
}
