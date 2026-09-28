<img width="1024" height="1024" alt="image" src="https://github.com/user-attachments/assets/965bbddb-856a-4f1c-bbe9-d97df1ef6317" />


# GitPush 📲

**Aplikasi Android murni native (Kotlin + Jetpack Compose) — file manager + penyimpanan awan di atas repository GitHub.** Pengganti aplikasi GitHub resmi dengan identitas utama: **file manager yang bisa mengelola file repository layaknya penyimpanan awan** — buka, unduh, pindah, rename, edit, hapus, ZIP, plus **upload massal banyak file & folder dalam 1 commit**.

<p>
  <img src="https://img.shields.io/badge/version-1.8-blue" alt="version" />
  <img src="https://img.shields.io/badge/platform-Android%209%2B-green" alt="platform" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-orange" alt="compose" />
  <img src="https://img.shields.io/badge/ukuran%20APK-~3%20MB-success" alt="size" />
</p>

> 100% native — tanpa WebView, tanpa wrapper. Tampilan modern, semua proses tampil progresnya.

---

## ⬇️ Unduh APK

**APK selalu ada di halaman [Releases](https://github.com/anztdree/GitPush/releases/latest)** (panel kanan halaman repo → bagian *Releases*):

**[⬇️ GitPush-v1.8.apk — Releases](https://github.com/anztdree/GitPush/releases/latest)**

1. Buka [halaman Releases](https://github.com/anztdree/GitPush/releases/latest) → unduh `GitPush-v1.8.apk`
2. Buka file → izinkan *Install dari sumber tidak dikenal*
3. Login menggunakan **Personal Access Token (PAT)** GitHub (scope `repo`)

> 📌 Catatan: APK **tidak** diletakkan sebagai file biasa di daftar file repo — semua versi (v1.0 s.d. v1.8) tersimpan rapi di tab **Releases** agar repo tetap ramping.

## 📁 Struktur Repository (kode sumber lengkap tersedia)

| Lokasi | Isi |
|---|---|
| `native/` | **Kode sumber Android lengkap** — proyek Gradle siap build (Kotlin + Jetpack Compose, package `com.gitpush.app`) |
| `native/app/src/main/java/com/gitpush/app/` | Seluruh kode aplikasi: `MainActivity.kt`, `ui/` (layar & komponen), `data/` (GitHub API, model, store) |
| `brand/` | Aset logo & ikon aplikasi |
| `scripts/` | Skrip utilitas (generator ikon Android, dsb.) |

## ✨ Fitur

| Kategori | Detail |
|---|---|
| **File manager repository** | Browse folder + breadcrumb, **status update terakhir per file/folder ala website GitHub** (waktu diperbarui terakhir), ikon berwarna per tipe file, ukuran riil termasuk objek LFS |
| **Kelola file** | Buka (teks/gambar/markdown/biner), **edit**, buat, **rename**, **pindah file/folder**, hapus — semua lewat menu baris |
| **Upload massal** | Banyak file dan/atau seluruh folder → **1 commit** (blobs → tree → commit → update ref); file **>95 MB otomatis via Git LFS** |
| **Progres semua proses** | Upload (per-byte, kecepatan + ETA, batal kapan saja), unduh, ZIP, pindah, rename, hapus — **semua tampil dialog progres** |
| **Download** | File tunggal (file besar streaming tanpa 0 KB), folder (ZIP), atau seluruh repository (ZIP, objek LFS diisi konten asli) → tersimpan di `Download/GitPush` |
| **Riwayat & README** | Tombol riwayat commit (layar penuh) + tombol README dengan render markdown |
| **Repository** | Buat repository baru, hapus repository (konfirmasi ketik nama), ganti branch, kuota riil 2 GB (termasuk LFS) |
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

## 📜 Versi

| Versi | Sorotan |
|---|---|
| **v1.8** | Baris file: hanya waktu update terakhir (pesan commit dihapus — bikin waktu kepotong) |
| **v1.7** | Status update terakhir per file/folder ala website GitHub |
| **v1.6** | Fix upload file besar (LFS) — respons API terpotong 800 karakter |
| **v1.5** | Dialog progres untuk semua proses + riwayat commit & README jadi tombol + pindah file/folder |
| **v1.4** | Fix unduhan 0 KB (streaming blob mentah) |
| **v1.3** | Hapus repository |
| **v1.2** | Navigasi folder & ukuran file riil |
| **v1.1** | Folder manager & kuota 2 GB |
| **v1.0** | Rilis pertama — upload massal 1 commit |

---

Dibuat dengan Kotlin + Jetpack Compose · GitHub REST API + Git Data API + Git LFS
