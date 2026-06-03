# Panduan Menghubungkan Firebase SHA-1 & Google Sign-In (Kasir Pro)

Daftar isi berisi petunjuk langkah-demi-langkah cara mendapatkan sidik jari SHA-1 dari debug keystore dan mendaftarkannya ke Firebase Console untuk memperbaiki autentikasi Google Sign-In pada perangkat riil (real device).

---

## 1. Cara Cepat & Otomatis: Menggunakan Gradle Task (`signingReport`)
Metode ini adalah cara paling mudah, cepat, dan bekerja secara lintas platform (Windows, macOS, Linux) tanpa perlu mencari file `.keystore` secara manual atau mengonfigurasi `keytool`.

1. Tarik kode proyek kasir ke mesin komputer pengembang lokal Anda atau buka terminal di Android Studio.
2. Jalankan perintah berikut di Terminal root proyek:
   - **Di macOS / Linux:**
     ```bash
     ./gradlew signingReport
     ```
   - **Di Windows (Command Prompt):**
     ```cmd
     gradlew signingReport
     ```
   - **Di Windows (PowerShell):**
     ```powershell
     ./gradlew signingReport
     ```
3. Tunggu hingga proses build selesai. Di bagian output terminal, carilah konfigurasi bertuliskan **`Variant: debug`** atau **`Config: debug`**.
4. Anda akan melihat baris mirip seperti berikut:
   ```text
   Variant: debugAndroidTest
   Config: debug
   Store: /Users/username/.android/debug.keystore
   Alias: AndroidDebugKey
   MD5:  XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
   SHA-1: AA:BB:CC:DD:EE:FF:11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD
   SHA-256: 11:22:33:44:55:... (panjang)
   Valid until: Tuesday, June 1, 2036
   ```
5. **Salin nilai SHA-1** tersebut.

---

## 2. Cara Manual: Menggunakan JDK Keytool
Jika Anda ingin mengekstrak sidik jari SHA-1 langsung dari file keystore global Android di komputer Anda, Anda bisa menggunakan utilitas bawaan JDK `keytool`.

### macOS / Linux:
Buka Terminal dan jalankan perintah:
```bash
keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android
```

### Windows:
Buka Command Prompt atau PowerShell, jalankan perintah:
```cmd
keytool -list -v -alias androiddebugkey -keystore "%USERPROFILE%\.android\debug.keystore" -storepass android
```

> **Catatan:** Jika perintah `keytool` tidak dikenali, pastikan direktori `bin` dari Java Development Kit (JDK) Anda sudah didaftarkan di dalam PATH Environment Variables komputer Anda, atau gunakan terminal bawaan Android Studio.

---

## 3. Cara Menambahkan SHA-1 ke Setelan Project Firebase Anda

Setelah berhasil menyalin nilai SHA-1 dari langkah di atas, ikuti langkah berikut untuk memasukkannya ke Firebase:

1. Buka **[Firebase Console](https://console.firebase.google.com/)**.
2. Masuk ke proyek Firebase Anda (**Kasir Pro**).
3. Klik ikon roda gigi ⚙️ di samping **Project Overview** pada panel sisi kiri, lalu pilih **Project settings (Setelan proyek)**.
4. Pada tab **General (Umum)**, gulir ke arah bawah hingga Anda menemukan bagian **Your apps (Aplikasi Anda)**.
5. Klik pada aplikasi Android Anda (misalnya: `com.kasirpro.app` / `com.aistudio.kasirpro_app`).
6. Di bawah detail aplikasi, klik tombol **Add fingerprint (Tambahkan sidik jari)**.
7. Tempelkan baris **SHA-1** yang sudah Anda salin sebelumnya ke kolom yang tersedia.
8. Klik **Save (Simpan)**.

---

## 4. Langkah Akhir (Opsional tapi Direkomendasikan)
Agar integrasi Google Sign-In ter-update secara sempurna di dalam aplikasi Android Anda:
1. Unduh file konfigurasi terbaru **`google-services.json`** dari halaman Project Settings yang sama.
2. Salin dan gantikan file `app/google-services.json` lama di dalam folder `app/` proyek Anda dengan yang baru diunduh.
3. Lakukan **Rebuild Project** di Android Studio untuk memuat perubahan sidik jari yang baru.

Kini autentikasi Google Sign-In pada Hp/perangkat riil Anda akan berfungsi dengan normal tanpa hambatan!
