# GitPush 📲

**Aplikasi Android murni native (Kotlin + Jetpack Compose) untuk mengelola repository GitHub dari HP** — lengkap dengan fitur andalan yang tidak dimiliki aplikasi GitHub resmi: **upload massal banyak file & folder dalam satu commit**.

<p>
  <img src="https://img.shields.io/badge/version-1.0-blue" alt="version" />
  <img src="https://img.shields.io/badge/platform-Android%209%2B-green" alt="platform" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-orange" alt="compose" />
  <img src="https://img.shields.io/badge/ukuran%20APK-~3%20MB-success" alt="size" />
</p>

> Tampilan modern ala aplikasi GitHub resmi + **bulk upload** ala GitHub Desktop/web. Tanpa WebView, tanpa wrapper — 100% Kotlin.

---

## ⬇️ Unduh APK

Unduh langsung dari halaman Release:

**[GitPush-v1.0.apk → Releases](https://github.com/anztdree/GitPush/releases/latest)**

1. Unduh `GitPush-v1.0.apk`
2. Buka file → izinkan *Install dari sumber tidak dikenal*
3. Login menggunakan **Personal Access Token (PAT)** GitHub (scope `repo`)

## ✨ Fitur

| Kategori | Detail |
|---|---|
| **Upload massal** | Pilih banyak file dan/atau seluruh folder → semua masuk **1 commit** (Git Data API: blobs → tree → commit → update ref) |
| **Tanpa batas praktis** | Ratusan file per commit dengan throttle adaptif; file **>95 MB otomatis lewat Git LFS**; progres per-byte per tahap (persiapan → unggah → commit → selesai) dengan kecepatan + ETA + batal kapan saja |
| **File Manager bawaan** | Browser penyimpanan internal full akses (bukan SAF terbatas) — pilih file per-checklist, pilih folder, multi-pilih tekan-lama, path relatif rapi |
| **Kelola file** | Lihat (teks/gambar/markdown/biner), edit, buat, rename, hapus file |
| **Download** | File tunggal, folder (ZIP), atau seluruh repository (ZIP) — tersimpan di `Download/GitPush` |
| **Repository** | Buat repository baru, browse + breadcrumb + ganti branch, riwayat commit |
| **Notifikasi** | Notifikasi GitHub dengan filter & tandai dibaca |
| **Lainnya** | Tema gelap/terang, responsif (HP & tablet), pencarian repo, pesan commit default bisa diatur |

## 🔨 Bangun dari Sumber

**Kebutuhan:** Android Studio, atau JDK 17+ + Android SDK 36.

```bash
git clone https://github.com/anztdree/GitPush.git
cd GitPush/native
./gradlew assembleRelease
# hasil: app/build/outputs/apk/release/app-release.apk
```

Atau buka folder `native/` di Android Studio → Run ▶️.

### Ikon aplikasi

Ikon launcher digenerate dari sumber `brand/icon-raw.png`:

```bash
node scripts/gen-android-icons.mjs   # → native/app/src/main/res
```

## 🗂️ Struktur Proyek

```
├── native/                          # 📱 Aplikasi Android native (SUMBER UTAMA)
│   └── app/src/main/
│       ├── java/com/gitpush/app/
│       │   ├── data/                #   GitHubApi (OkHttp), Models, Prefs
│       │   └── ui/                  #   MainActivity + 5 tab: Home, Repo,
│       │                            #   Upload, Notifikasi, Profil
│       │                            #   + FileBrowser, FileScreens, Settings
│       ├── res/                     #   ikon launcher semua density + tema
│       └── AndroidManifest.xml
├── brand/                           # sumber ikon (icon-raw.png)
├── scripts/                         # generator ikon launcher Android
├── worklog.md                       # jurnal pengembangan
└── README.md
```

## 🛠️ Teknologi

- **Kotlin** + **Jetpack Compose** (Material 3) — UI deklaratif, gelap/terang, responsif
- **OkHttp** — REST API GitHub v3: Contents, Git Data (blobs/trees/commits), Notifications, Zipball
- **Git LFS API** — file >95 MB otomatis via LFS (streaming sha256, hemat RAM)
- `java.io.File` + SAF + `MANAGE_EXTERNAL_STORAGE` — file manager bawaan full akses
- Min SDK 29 (Android 10) · Target SDK 36 · APK ±3 MB (R8 minified)

## 📝 Catatan

- Login hanya menggunakan **PAT** (scope `repo` minimal). Token disimpan lokal di perangkat (SharedPreferences) dan hanya dikirim ke api.github.com.
- File >95 MB diunggah otomatis melalui **Git LFS** (butuh LFS aktif di repository tujuan; GitHub memberi kuota LFS tersendiri).
- Batas mutlak GitHub: file >100 MB tidak mungkin via Git Data API — GitPush otomatis memakai LFS untuk melewatinya.
- APK di-release di-sign dengan debug key — cocok untuk pemakaian pribadi; untuk Play Store gunakan signing key sendiri.

## 📄 Lisensi

Proyek pribadi — bebas digunakan dan dimodifikasi untuk keperluan sendiri.
