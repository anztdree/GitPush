<img width="1024" height="1024" alt="image" src="https://github.com/user-attachments/assets/965bbddb-856a-4f1c-bbe9-d97df1ef6317" />


# GitPush 📲

**Aplikasi Android murni native (Kotlin + Jetpack Compose) — file manager + penyimpanan awan di atas repository GitHub.** Pengganti aplikasi GitHub resmi dengan identitas utama: **file manager yang bisa mengelola file repository layaknya penyimpanan awan** — buka, unduh, pindah, rename, edit, hapus, ZIP, plus **upload massal banyak file & folder dalam 1 commit**.

<p>
  <img src="https://img.shields.io/badge/version-1.0-blue" alt="version" />
  <img src="https://img.shields.io/badge/platform-Android%209%2B-green" alt="platform" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Inter-orange" alt="compose" />
  <img src="https://img.shields.io/badge/ukuran%20APK-~4%20MB-success" alt="size" />
</p>

> 100% native — tanpa WebView, tanpa wrapper. Tampilan modern, semua proses tampil progresnya.

---

## ⬇️ Unduh APK

**APK selalu ada di halaman [Releases](https://github.com/anztdree/GitPush/releases/latest)** (panel kanan halaman repo → bagian *Releases*):

**[⬇️ GitPush-v1.0.apk — Releases](https://github.com/anztdree/GitPush/releases/latest)**

1. Buka [halaman Releases](https://github.com/anztdree/GitPush/releases/latest) → unduh `GitPush-v1.0.apk`
2. Buka file → izinkan *Install dari sumber tidak dikenal*
3. Login menggunakan **Personal Access Token (PAT)** GitHub (scope `repo`)

> 📌 Catatan: APK **tidak** diletakkan sebagai file biasa di daftar file repo — APK selalu tersimpan rapi di tab **Releases** agar repo tetap ramping.

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
| **File manager repository** | Browse folder + breadcrumb, **status update terakhir per file/folder ala website GitHub**, ikon berwarna per tipe file, ukuran riil termasuk objek LFS |
| **Kelola file** | Buka (teks/gambar/markdown/biner), **edit**, buat, **rename**, **pindah file/folder**, hapus — semua lewat menu baris |
| **Upload massal** | Banyak file dan/atau seluruh folder → **1 commit** (blobs → tree → commit → update ref); file **>95 MB otomatis via Git LFS**; tahan retry (objek yang sudah terunggah tidak diulang) |
| **Download** | File tunggal (streaming, aman 100 MB+), folder (ZIP), seluruh repository (ZIP dengan isi LFS asli) → `Download/GitPush` |
| **Aksi repo ala GitHub** | **Star/Unstar**, **Fork**, **Watch/Unwatch**, unduh ZIP, statistik star/fork/issue/watcher, salin URL, buka di browser |
| **Kelola repository** | Buat repository, **edit repository** (nama, deskripsi, situs web, privat/publik), hapus (konfirmasi ketik nama), **buat & hapus branch**, ganti branch |
| **Issues** | Daftar terbuka/ditutup, buat issue, baca detail, **komentar**, tutup/buka ulang |
| **Pull request** | Daftar PR, lihat head→base, **gabungkan (merge) PR** dengan konfirmasi |
| **Releases** | Daftar rilis repository + catatan rilis + jumlah aset |
| **Pencarian global** | Cari repository **di seluruh GitHub** (bukan hanya milik sendiri) — langsung bisa dibuka |
| **Profil & akun** | **Edit profil publik** (nama, bio, perusahaan, lokasi, situs, email), daftar **pengikut/mengikuti + tombol ikuti**, **organisasi**, **umpan aktivitas** ala GitHub |
| **Gist** | Lihat, buat, baca isi, salin, hapus gist — catatan cepat lintas perangkat |
| **Pengaturan lengkap** | Kelola **email akun**, **kunci SSH**, lihat **scope PAT** aktif, tema, pesan commit default, riwayat aktivitas |
| **Notifikasi** | Notifikasi GitHub dengan filter & tandai dibaca (satu per satu / semua) |
| **Desain & tipografi** | **Font Inter Variable** (4 bobot), skala huruf rapi (letterSpacing negatif utk judul), header seksi konsisten, ikon berwarna per kategori file, tema gelap/terang/ikuti sistem, responsif HP & tablet |
| **Progres semua proses** | Upload (per-byte, kecepatan + ETA, batal), unduh, ZIP, pindah, rename, hapus — semua tampil dialog progres |

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

Aplikasi memakai label versi **1.0** (versionCode 28) — versi final. Pembaruan build tetap dirilis lewat tab **Releases**: nomor build internal (versionCode) naik otomatis sehingga install di atas versi lama berjalan mulus tanpa perlu uninstall.

**Unggah dirancang ulang total ala aplikasi cloud (versionCode 28):** desain lama yang salah — tab **Unggah** terpisah yang membuang file ke root — **DIHAPUS**. Sekarang unggahan adalah aksi **di dalam folder yang sedang dibuka**: jelajahi repository → masuk ke folder tujuan → ketuk tombol hijau **"Unggah di sini"** → pilih file/folder dari HP (SAF multi-pilih, pindai pohon folder, atau File Manager bawaan) → file masuk **TEPAT di folder itu** dalam 1 commit. Sheet unggahan menampilkan folder tujuan secara eksplisit, daftar file yang dipilih, pesan commit opsional, plus aksi **Folder baru** dan **File baru** di folder tersebut. Selama mengunggah, **chip progres** melayang di bawah layar di halaman mana pun (bisa terus menjelajah) — ketuk untuk membuka panel progres lengkap: persentase, kecepatan, ETA, log langsung, batal, dan *Coba Lagi* file gagal. Daftar folder otomatis **menyegarkan diri** begitu unggahan selesai.

**Logo seragam (versionCode 27):** splash screen, layar masuk, dan semua titik branding kini memakai **logo yang sama persis dengan ikon launcher** (satu aset dari `brand/icon-raw.png`) — bukan lagi ikon generik.

**Rilis final + lampiran sekali jalan (versionCode 26):** saat **membuat rilis** kini bisa langsung **menambahkan file lampiran** — pilih satu/beberapa file di dialog *Buat rilis*; begitu rilis jadi, file diunggah sebagai aset SEKETIKA dengan progres (file gagal dilaporkan, rilis tetap terbuat). Batas penyimpanan repository 2 GB juga **dihapus total**: Beranda, halaman repository, dan Upload kini hanya menampilkan penyimpanan **terpakai** — tanpa bar kuota, persentase, atau blokir unggahan.

**Kelola aset rilis (versionCode 25):** bagian Releases kini juga bisa **mengunggah file sebagai aset rilis** (tombol *Tambah file* pada setiap kartu rilis — pilih satu atau banyak file sekaligus, unggah streaming dengan progres) dan **menghapus aset** (ikon hapus per baris aset, dengan konfirmasi). Mengunggah file dengan nama yang sama otomatis **mengganti** aset lama — pas untuk memperbarui APK di rilis langsung dari aplikasi.

**Andalkan unggahan (versionCode 24):** akar keluhan "upload sering gagal — 403 lalu commit tak jadi" tuntas dirombak: (1) **pacing global konstan** — semua request pembuat-konten kini berjarak aman ±66 req/menit sejak detik pertama dan jeda itu dibagi rata semua worker (dulu mulai tanpa jeda, lalu longgar sampai nol → burst → 403 berulang); (2) **circuit breaker** — begitu GitHub menghukum 403, SEMUA worker berhenti menghormati masa hukuman (patuh `Retry-After`, 20–120 dtk bertingkat) sehingga blokir tak lagi diperpanjang; (3) **fase commit diperkuat** — ref/tree/commit/ref kini ikut retry sabar 10 percobaan dengan backoff eksponensial s/d 60 dtk (dulu GET polos tanpa retry — satu 403 di detik terakhir membuang seluruh unggahan); (4) **"Coba Lagi" file gagal** — file yang tetap gagal TIDAK hilang: tetap tersimpan, tombol *Coba lagi N file gagal* mengunggahnya ulang dalam 1 commit dan otomatis menyatu dengan file yang sudah berhasil (blob yang sudah sampai tak dikirim dua kali).

**Rombakan tampilan v-baru:** hero gradien hijau dengan sapaan pengguna + ringkasan penyimpanan di Beranda, kartu ringkasan repository bergradien dengan statistik & 4 aksi cepat (star/fork/pantau/ZIP), baris file bergaya kartu modern dengan tag LFS, skeleton loading berdenyut, layar masuk baru dengan sorotan fitur, warna & tema lebih dalam ala GitHub modern, serta tombol "+ Repo Baru" mengapung.

**Rilis lengkap:** bagian Releases kini menampilkan catatan rilis sebagai MARKDOWN yang tersusun rapi (judul, daftar, kode, tautan), daftar **aset lampiran lengkap dengan ukuran + jumlah unduhan + tombol unduh** (bisa satu-satu maupun "Unduh semua"), serta tombol **Edit, Hapus, dan Buat rilis** langsung dari aplikasi — edit judul, catatan Markdown, tandai prarilis/draf.

**Ringkasan penyimpanan diperbaiki:** angka besar di Beranda benar-benar total semua repo, dilengkapi info repo terbesar (nama + ukuran) — tanpa bar kuota.

**Perbaikan penting unduh Git LFS:** sebelumnya mengunduh file LFS justru menyimpan teks pointer ±137 B (bukan isi aslinya) karena API satu file melaporkan ukuran ASLI, bukan ukuran pointer — deteksi kini memeriksa isi blob, jadi file 100 MB+ terunduh utuh; ZIP folder/repo pun makin tahan (probe pointer hingga 400 file).

Sorotan build saat ini: **font Inter + tipografi baru**, **aksi repo ala GitHub (star/fork/watch)**, **issues & pull request**, **releases**, **pencarian global**, **edit repository + branch CRUD**, **pengaturan akun lengkap (profil, email, kunci SSH, scope PAT)**, **gist**, dan tata letak layar yang lebih efisien.

---

Dibuat dengan Kotlin + Jetpack Compose · GitHub REST API + Git Data API + Git LFS
