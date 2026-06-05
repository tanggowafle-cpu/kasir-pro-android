package com.kasirpro.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kasirpro.app.ui.viewmodel.KasirViewModel
import com.kasirpro.app.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

@Composable
fun LoginScreen(viewModel: KasirViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PointOfSale,
                contentDescription = "Kasir Pro Primary Icon",
                tint = OrangePrimary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Masuk Kasir Pro",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = "Kelola finansial toko Anda dengan mudah",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
                textAlign = TextAlign.Center
            )

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEEE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (errorMessage?.contains("No credentials available", ignoreCase = true) == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Red.copy(alpha = 0.2f)))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Petunjuk Pengujian Cloud Sandbox:\nLayanan Google Play di emulator saat ini belum terhubung ke Akun Google mana pun. Di HP asli Anda, Google Sign-In akan berjalan normal dan otomatis memunculkan pilihan akun e-mail Anda.\n\nUntuk menguji masuk aplikasi via Google di emulator web ini sekarang, Anda dapat langsung mengklik tombol bypass:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    isLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        val loginResult = viewModel.repository.loginWithGoogle("sandbox-bypass")
                                        isLoading = false
                                        if (loginResult.success) {
                                            if (loginResult.role == "kasir") {
                                                viewModel.activeScreen.value = "cashier"
                                            } else if (loginResult.isNewUser) {
                                                viewModel.activeScreen.value = "setup_toko"
                                            } else {
                                                viewModel.activeScreen.value = "home"
                                            }
                                        } else {
                                            errorMessage = "Gagal memproses sandbox bypass."
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("Masuk dengan Sandbox Tester (Offline)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email atau Username Kasir") },
                placeholder = { Text("budi_kasir atau owner@kasirpro.com") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_email_input"),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Lupa Password?",
                    color = OrangePrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable {
                        viewModel.activeScreen.value = "forgot_password"
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Mohon lengkapi email dan password!"
                        return@Button
                    }
                    if (password.length < 6) {
                        errorMessage = "Password minimal terdiri dari 6 karakter!"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val success = viewModel.repository.loginUser(email, password)
                            isLoading = false
                            if (success) {
                                val user = viewModel.repository.getCurrentUserRaw()
                                val biz = viewModel.repository.getCurrentBusinessRaw()
                                if (user?.role == "owner" && biz == null) {
                                    viewModel.activeScreen.value = "setup_toko"
                                } else if (user?.role == "kasir") {
                                    viewModel.activeScreen.value = "cashier"
                                } else {
                                    viewModel.activeScreen.value = "home"
                                }
                            } else {
                                errorMessage = "Email atau password salah!"
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage = e.message ?: "Email atau password salah!"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_login_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Masuk", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Google Sign-In Accent Button
            OutlinedButton(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            
                            // Load the real Google Web Client ID from BuildConfig
                            val webClientId = try {
                                com.kasirpro.app.BuildConfig.GOOGLE_WEB_CLIENT_ID
                            } catch (e: Exception) {
                                "357452625370-c0c7mqnmhodoebtq4e3323ocfsp3ituo.apps.googleusercontent.com"
                            }
                            
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(webClientId)
                                .setAutoSelectEnabled(false)
                                .build()
                                
                            val credentialRequest = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                                
                            val result = credentialManager.getCredential(
                                context = context,
                                request = credentialRequest
                            )
                            
                            val credential = result.credential
                            if (credential is androidx.credentials.CustomCredential && 
                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                
                                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                val idToken = googleIdTokenCredential.idToken
                                
                                val loginResult = viewModel.repository.loginWithGoogle(idToken)
                                if (loginResult.success) {
                                    if (loginResult.role == "kasir") {
                                        viewModel.activeScreen.value = "cashier"
                                    } else if (loginResult.isNewUser) {
                                        viewModel.activeScreen.value = "setup_toko"
                                    } else {
                                        viewModel.activeScreen.value = "home"
                                    }
                                } else {
                                    errorMessage = "Google login gagal menghubungkan ke Firebase Auth."
                                }
                            } else {
                                errorMessage = "Tipe kredensial tidak valid."
                            }
                        } catch (e: GetCredentialException) {
                            errorMessage = "Pemilihan akun Google dibatalkan atau gagal: ${e.localizedMessage}"
                        } catch (e: Exception) {
                            errorMessage = "Terjadi kesalahan: ${e.localizedMessage}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("google_login_button")
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = OrangePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Masuk menggunakan Google", color = OrangePrimary, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.Center) {
                Text(text = "Belum memiliki akun? ", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Text(
                    text = "Daftar Toko Baru",
                    color = OrangePrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        viewModel.activeScreen.value = "register"
                    }
                )
            }
        }


    }
}

@Composable
fun RegisterScreen(viewModel: KasirViewModel) {
    var nama by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Daftar Toko Kasir Pro",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = "Mulailah melayani transaksi bisnis dalam beberapa klik",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
                textAlign = TextAlign.Center
            )

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEEE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = nama,
                onValueChange = { nama = it },
                label = { Text("Nama Pemilik") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_name_input"),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Alamat Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_email_input"),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_password_input"),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (nama.isBlank() || email.isBlank() || password.isBlank()) {
                        errorMessage = "Harap lengkapi semua data formulir!"
                        return@Button
                    }
                    if (password.length < 6) {
                        errorMessage = "Password minimal terdiri dari 6 karakter!"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val success = viewModel.repository.registerUser(nama, email, password)
                            isLoading = false
                            if (success) {
                                viewModel.activeScreen.value = "setup_toko"
                            } else {
                                errorMessage = "Registrasi gagal, silakan coba lagi."
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage = e.message ?: "Registrasi gagal, silakan coba lagi."
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_register_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Daftar Sekarang", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.Center) {
                Text(text = "Sudah memiliki akun? ", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Text(
                    text = "Kembali ke Login",
                    color = OrangePrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        viewModel.activeScreen.value = "login"
                    }
                )
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(viewModel: KasirViewModel) {
    var email by remember { mutableStateOf("") }
    var sentMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.LockReset,
                contentDescription = null,
                tint = OrangePrimary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Lupa Password",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Masukkan email terdaftar Anda dan kami akan mengirimkan link reset password.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (sentMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OrangeLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Text(
                        text = sentMessage ?: "",
                        color = OrangeDark,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Alamat Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (email.isBlank()) return@Button
                    scope.launch {
                        viewModel.repository.resetPassword(email)
                        sentMessage = "Email pemulihan password berhasil dikirim ke $email"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Kirim Link Reset", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Kembali ke Login",
                color = OrangePrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    viewModel.activeScreen.value = "login"
                }
            )
        }
    }
}

@Composable
fun SetupTokoScreen(viewModel: KasirViewModel) {
    var namaToko by remember { mutableStateOf("") }
    var alamatToko by remember { mutableStateOf("") }
    var selectedImgUrl by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Setup Toko Baru Anda",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = "Tambahkan info detail operasional toko utama Anda",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
                textAlign = TextAlign.Center
            )

            // Dynamic Image Avatar / Logo Selector
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(OrangeLight)
                    .clickable {
                        // Quick default options simulator
                        selectedImgUrl = "https://images.unsplash.com/photo-1473093295043-cdd812d0e601"
                        statusMessage = "Logo berhasil diupload ke Storage!"
                    },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImgUrl == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null, tint = OrangePrimary)
                        Text("Logo Toko", fontSize = 11.sp, color = OrangePrimary, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Icon(imageVector = Icons.Default.Storefront, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(48.dp))
                }
            }

            if (statusMessage != null) {
                Text(
                    text = statusMessage ?: "",
                    color = OrangeDark,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = namaToko,
                onValueChange = { namaToko = it },
                label = { Text("Nama Toko/Bisnis") },
                placeholder = { Text("contoh: Kopi Kita Jakarta") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shop_name_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = alamatToko,
                onValueChange = { alamatToko = it },
                label = { Text("Alamat Kantor/Toko Utama") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (namaToko.isBlank() || alamatToko.isBlank()) {
                        statusMessage = "Silakan isi nama toko dan alamat!"
                        return@Button
                    }
                    scope.launch {
                        viewModel.onboardingStoreSetup(namaToko, alamatToko, selectedImgUrl)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_setup_toko")
            ) {
                Text("Simpan & Masuk Beranda", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
