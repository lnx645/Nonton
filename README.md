# Nonton

Aplikasi Android open source untuk menjelajah, memutar, dan mengunduh media dari layanan publik — dibangun dengan **Kotlin + Jetpack Compose** dan ditenagai **NewPipeExtractor**.

Nonton bukan fork NewPipe. Aplikasi ini memakai NewPipeExtractor sebagai library untuk mengambil data (search, trending, metadata, dan URL stream), lalu menyediakan UI, player, penyimpanan lokal, dan unduhan sendiri.

## Fitur

- **Beranda / Trending** per layanan, dengan paginasi tak terbatas
- **Pencarian** dengan filter layanan dan paginasi
- **Pemutaran** video/audio dengan Media3 (ExoPlayer) melalui `MediaSessionService`
- **Background playback** dan **Picture-in-Picture** otomatis saat keluar dari pemutar
- **Pemilihan kualitas** video dan mode audio saja
- **Unduhan** video/audio dengan antrean, progres, notifikasi, dan pembatalan
- **Playlist lokal** (buat, tambah video, hapus item, hapus playlist)
- **Riwayat tontonan** dengan posisi resume
- **Multi-layanan**: YouTube, SoundCloud, PeerTube, Bandcamp, media.ccc.de
- **Tema** terang/gelap/ikut sistem + Material You (Android 12+)
- Antarmuka berbahasa Indonesia

## Stack

| Komponen | Versi / Teknologi |
|---|---|
| Bahasa | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.12.01), Material 3 |
| Build | AGP 8.7.3, Gradle 8.9 |
| SDK | compileSdk/targetSdk 35, minSdk 24 |
| Player | AndroidX Media3 1.5.1 (ExoPlayer + MediaSession) |
| Database | Room 2.6.1 (KSP) |
| HTTP | OkHttp 4.12 |
| Gambar | Coil 2.7.0 |
| Ekstraksi | [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) v0.26.5 (JitPack) |

## Build

Prasyarat:

- JDK 17+ (JDK 21 direkomendasikan)
- Android SDK Platform 35 + Build-Tools 35.0.0
- Koneksi internet untuk mengunduh dependency (termasuk JitPack)

```bash
git clone https://github.com/lnx645/Nonton.git
cd Nonton
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
```

APK debug dihasilkan di `app/build/outputs/apk/debug/app-debug.apk`.

## Arsitektur

Struktur utama ada di `app/src/main/java/id/nonton/app/`:

```
core/        Inisialisasi NewPipe, Downloader OkHttp, settings, model stream,
             formatter, pemetaan error
data/        Room: entities, DAO, database, LibraryRepository
download/    DownloadService (foreground service, MediaStore / penyimpanan publik)
playback/    PlaybackService (Media3 MediaSessionService)
ui/          Compose: home, search, library, playlist, settings, player, components, theme
MainActivity.kt / PlayerActivity.kt / NontonApp.kt
```

Poin desain:

- **Manual DI** lewat `AppContainer` di `NontonApp.kt` — tanpa Hilt.
- **Satu OkHttpClient** dipakai bersama oleh NewPipeExtractor, Coil, dan pengunduh.
- **Pemutaran lewat `MediaSessionService`**, sehingga audio tetap berjalan saat aplikasi di background dan notifikasi media berfungsi.
- **Unduhan memakai foreground service** dengan antrean berbasis Room, sehingga progres tetap jalan ketika layar mati.

## Batasan yang diketahui

- Pemutaran mengutamakan **stream progresif** (video+audio menyatu). Untuk konten yang hanya menyediakan stream terpisah, aplikasi memakai **HLS/DASH adaptif** bila tersedia. Penggabungan DASH video+audio (muxing) belum diimplementasikan.
- URL stream dari YouTube bersifat sementara dan dapat kedaluwarsa; unduhan yang gagal karena tautan kedaluwarsa perlu diulang dari awal.
- YouTube dapat meminta verifikasi bot/captcha pada beberapa jaringan (terutama IP datacenter). Ini batasan layanan, bukan bug aplikasi.
- Layanan pihak ketiga dapat berubah kapan saja dan mematahkan ekstraksi sampai NewPipeExtractor diperbarui.
- Belum ada login/akun, langganan channel, atau sinkronisasi cloud.

## Lisensi

Proyek ini dilisensikan di bawah **GNU General Public License v3.0 atau lebih baru**. Lihat [`LICENSE`](LICENSE).

Karena memakai NewPipeExtractor (GPLv3), distribusi aplikasi ini wajib disertai source code dengan lisensi yang kompatibel.

## Disclaimer

Nonton tidak berafiliasi dengan YouTube, SoundCloud, PeerTube, Bandcamp, atau media.ccc.de. Semua konten adalah milik pemiliknya masing-masing. Gunakan aplikasi ini hanya untuk keperluan yang sah dan patuhi ketentuan layanan serta hukum yang berlaku di wilayahmu.
