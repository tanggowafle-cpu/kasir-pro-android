package com.kasirpro.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.InputStream
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kasirpro.app.ui.theme.OrangePrimary

object ImageHelper {

    private val downloadUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Extracts relative storage path (e.g. products/{ownerId}/{productId}/photo.jpg) from URL/path.
     */
    fun extractStoragePath(url: String): String? {
        if (url.startsWith("products/")) return url
        if (url.contains("products%2F")) {
            try {
                val idx = url.indexOf("products%2F")
                val endIdx = url.indexOf("?", idx)
                val sub = if (endIdx != -1) url.substring(idx, endIdx) else url.substring(idx)
                return java.net.URLDecoder.decode(sub, "UTF-8")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    /**
     * Resolves a Firebase Storage path or download URL to a cached/latest download URL.
     */
    suspend fun getDownloadUrl(path: String): String {
        if (path.isBlank()) return ""
        if (!path.startsWith("products/")) {
            return path
        }
        val cached = downloadUrlCache[path]
        if (cached != null) return cached

        return try {
            val storage = try {
                FirebaseStorage.getInstance("gs://kasir-pro-3b58b.firebasestorage.app")
            } catch (e: Exception) {
                try {
                    FirebaseStorage.getInstance("gs://kasir-pro-3b58b.appspot.com")
                } catch (e2: Exception) {
                    FirebaseStorage.getInstance()
                }
            }
            val ref = storage.reference.child(path)
            val url = ref.downloadUrl.await().toString()
            downloadUrlCache[path] = url
            url
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Clear cash-ed URLs on Logout.
     */
    fun clearCache() {
        downloadUrlCache.clear()
    }

    /**
     * Loads a Uri to a Bitmap, downsamples it to a maximum dimension of 1024px to conserve memory,
     * and compresses it to a JPEG byte array under 500KB (512,000 bytes) progressively.
     */
    fun compressImageUri(context: Context, uri: Uri): ByteArray? {
        var inputStream: InputStream? = null
        try {
            // Step 1: Decode bounds first
            inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Step 2: Determine downsampling ratio
            val maxDimension = 1024
            var scale = 1
            if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                val maxDim = Math.max(options.outWidth, options.outHeight)
                scale = Math.ceil(maxDim.toDouble() / maxDimension.toDouble()).toInt()
            }

            // Step 3: Decode full scale downsampled Bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions) ?: return null
            inputStream?.close()

            // Step 4: Iteratively compress JPEG stream until size < 500KB
            var quality = 90
            var bytes: ByteArray
            do {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                bytes = outputStream.toByteArray()
                quality -= 10
            } while (bytes.size > 500 * 1024 && quality > 10)

            bitmap.recycle()
            return bytes
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            inputStream?.close()
        }
    }

    /**
     * Saves the photo locally in the app's internal storage as a backup/fallback,
     * returning the local file URI string.
     */
    fun saveImageLocally(context: Context, productId: String, bytes: ByteArray): String? {
        return try {
            val directory = java.io.File(context.filesDir, "product_photos")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = java.io.File(directory, "prod-$productId.jpg")
            file.writeBytes(bytes)
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Uploads the JPEG byte array directly to Firebase Storage and returns the public relative storage path.
     * Always saves a local copy in the app's internal storage for super-fast offline display.
     */
    suspend fun uploadProductImage(context: Context, ownerId: String, productId: String, bytes: ByteArray): String {
        val path = "products/$ownerId/$productId/photo.jpg"
        try {
            val storage = try {
                FirebaseStorage.getInstance("gs://kasir-pro-3b58b.firebasestorage.app")
            } catch (e: Exception) {
                try {
                    FirebaseStorage.getInstance("gs://kasir-pro-3b58b.appspot.com")
                } catch (e2: Exception) {
                    FirebaseStorage.getInstance()
                }
            }
            val ref = storage.reference.child(path)
            ref.putBytes(bytes).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Always save locally as well for fast local display
        saveImageLocally(context, productId, bytes)
        // Always return the standard relative Firebase path so that it synchronises across all devices and remains recoverable
        return path
    }

    /**
     * Uploads the shop logo to Firebase Storage and keeps a local copy.
     */
    suspend fun uploadShopLogo(context: Context, ownerId: String, bytes: ByteArray): String {
        val path = "logos/$ownerId/logo.jpg"
        try {
            val storage = try {
                FirebaseStorage.getInstance("gs://kasir-pro-3b58b.firebasestorage.app")
            } catch (e: Exception) {
                try {
                    FirebaseStorage.getInstance("gs://kasir-pro-3b58b.appspot.com")
                } catch (e2: Exception) {
                    FirebaseStorage.getInstance()
                }
            }
            val ref = storage.reference.child(path)
            ref.putBytes(bytes).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val directory = java.io.File(context.filesDir, "logo_photos")
            if (!directory.exists()) directory.mkdirs()
            val file = java.io.File(directory, "logo-$ownerId.jpg")
            file.writeBytes(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return path
    }
    
    /**
     * Deletes the product image from Firebase Storage if it exists.
     */
    suspend fun deleteProductImage(ownerId: String, productId: String) {
        try {
            FirebaseStorage.getInstance().reference
                .child("products/$ownerId/$productId/photo.jpg")
                .delete()
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun ProductImage(
    fotoUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    defaultIcon: ImageVector = Icons.Default.Fastfood
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var resolvedUrl by remember(fotoUrl) { mutableStateOf<String?>(null) }
    var isLoading by remember(fotoUrl) { mutableStateOf(false) }

    LaunchedEffect(fotoUrl) {
        if (!fotoUrl.isNullOrBlank()) {
            val targetPath = ImageHelper.extractStoragePath(fotoUrl)
            
            // Try to see if there is a local copy of this product image
            var localFileExists = false
            var localUriStr: String? = null
            
            if (targetPath != null) {
                val parts = targetPath.split("/")
                if (parts.size >= 3) {
                    val productId = parts[2]
                    val localFile = java.io.File(context.filesDir, "product_photos/prod-$productId.jpg")
                    if (localFile.exists()) {
                        localFileExists = true
                        localUriStr = android.net.Uri.fromFile(localFile).toString()
                    }
                }
            } else if (fotoUrl.startsWith("file://")) {
                try {
                    val uri = android.net.Uri.parse(fotoUrl)
                    val localFile = uri.path?.let { java.io.File(it) }
                    if (localFile != null && localFile.exists()) {
                        localFileExists = true
                        localUriStr = fotoUrl
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (fotoUrl.contains("prod-") && fotoUrl.endsWith(".jpg")) {
                try {
                    val idStart = fotoUrl.indexOf("prod-")
                    val idEnd = fotoUrl.indexOf(".jpg")
                    if (idStart != -1 && idEnd != -1 && idEnd > idStart) {
                        val productId = fotoUrl.substring(idStart + 5, idEnd)
                        val localFile = java.io.File(context.filesDir, "product_photos/prod-$productId.jpg")
                        if (localFile.exists()) {
                            localFileExists = true
                            localUriStr = android.net.Uri.fromFile(localFile).toString()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (localFileExists && localUriStr != null) {
                resolvedUrl = localUriStr
            } else {
                // If not found locally, load from Firebase Storage if it's a relative path, or load string URL directly
                if (targetPath != null) {
                    isLoading = true
                    val remoteUrl = ImageHelper.getDownloadUrl(targetPath)
                    if (remoteUrl.isNotBlank()) {
                        resolvedUrl = remoteUrl
                    } else {
                        resolvedUrl = null
                    }
                    isLoading = false
                } else if (fotoUrl.startsWith("http://") || fotoUrl.startsWith("https://")) {
                    resolvedUrl = fotoUrl
                } else if (fotoUrl.startsWith("file://")) {
                    // Try to guess by extracting productId from local path
                    var fallbackPath: String? = null
                    try {
                        val idStart = fotoUrl.indexOf("prod-")
                        val idEnd = fotoUrl.indexOf(".jpg")
                        if (idStart != -1 && idEnd != -1 && idEnd > idStart) {
                            val productId = fotoUrl.substring(idStart + 5, idEnd)
                            fallbackPath = "products/owner-uid/$productId/photo.jpg"
                        }
                    } catch (e: Exception) {}
                    
                    if (fallbackPath != null) {
                        isLoading = true
                        val remoteUrl = ImageHelper.getDownloadUrl(fallbackPath)
                        resolvedUrl = if (remoteUrl.isNotBlank()) remoteUrl else null
                        isLoading = false
                    } else {
                        resolvedUrl = null
                    }
                } else {
                    resolvedUrl = null
                }
            }
        } else {
            resolvedUrl = null
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = OrangePrimary,
                strokeWidth = 2.dp
            )
        } else if (!resolvedUrl.isNullOrBlank()) {
            AsyncImage(
                model = resolvedUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    resolvedUrl = null
                }
            )
        } else {
            Icon(
                imageVector = defaultIcon,
                contentDescription = null,
                tint = OrangePrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ShopLogoImage(
    logoUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    defaultIcon: ImageVector = Icons.Default.Fastfood
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var resolvedUrl by remember(logoUrl) { mutableStateOf<String?>(null) }
    var isLoading by remember(logoUrl) { mutableStateOf(false) }

    LaunchedEffect(logoUrl) {
        if (!logoUrl.isNullOrBlank()) {
            if (logoUrl.startsWith("logos/")) {
                val parts = logoUrl.split("/")
                val ownerId = if (parts.size >= 2) parts[1] else "owner-main"
                val localFile = java.io.File(context.filesDir, "logo_photos/logo-$ownerId.jpg")
                if (localFile.exists()) {
                    resolvedUrl = android.net.Uri.fromFile(localFile).toString()
                } else {
                    isLoading = true
                    try {
                        val storage = try {
                            FirebaseStorage.getInstance("gs://kasir-pro-3b58b.firebasestorage.app")
                        } catch (e: Exception) {
                            try {
                                FirebaseStorage.getInstance("gs://kasir-pro-3b58b.appspot.com")
                            } catch (e2: Exception) {
                                FirebaseStorage.getInstance()
                            }
                        }
                        val ref = storage.reference.child(logoUrl)
                        val url = ref.downloadUrl.await().toString()
                        resolvedUrl = url
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            } else {
                resolvedUrl = logoUrl
            }
        } else {
            resolvedUrl = null
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = OrangePrimary, strokeWidth = 2.dp)
        } else if (!resolvedUrl.isNullOrBlank()) {
            AsyncImage(
                model = resolvedUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    resolvedUrl = null
                }
            )
        } else {
            Icon(
                imageVector = defaultIcon,
                contentDescription = null,
                tint = OrangePrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
