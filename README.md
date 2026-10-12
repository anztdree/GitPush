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

Aplikasi memakai label versi **1.0** (versionCode 39) — versi final. Pembaruan build tetap dirilis lewat tab **Releases**: nomor build internal (versionCode) naik otomatis sehingga install di atas versi lama berjalan mulus tanpa perlu uninstall.

**README.md kini dirender penuh — bukan teks mentah lagi (versionCode 39):** sentuhan akhir tampilan dokumentasi:
- Tombol README di halaman repository (dan penampil file `.md`) tidak lagi menampilkan markdown mentah — kini dirender layaknya website GitHub: **judul bertingkat**, tebal/miring/~~strikethrough~~, **tabel bergaris**, daftar bertingkat, kutipan, blok kode, task list (kotak centang), dan garis pemisah.
- **Gambar ikut tampil** — logo, badge/shield, dan tangkapan layar; URL relatif (mis. `docs/preview.png`) otomatis diarahkan ke `raw.githubusercontent.com`, dan HTML ringan seperti `<img>` ikut dirender.
- **Tautan bisa diklik** dan langsung dibuka di peramban.
- Renderer yang sama dipakai juga untuk catatan release dan isi issue, dengan warna yang mengikuti tema terang/gelap aplikasi.

**"Hapus beberapa item…" — file dan folder bisa dipilih campur (versionCode 38):** penyempurnaan fitur hapus-massal:
- Fitur "Hapus beberapa folder…" kini menjadi **"Hapus beberapa item…"** — daftar di dialog mencakup **file dan folder sekaligus**, lengkap dengan ikon & warna per jenis file (kode hijau, dokumen biru, gambar ungu, dll. seperti di daftar file).
- Centang bebas: satu file saja, beberapa file, satu folder, atau **campuran file + folder** — semuanya dihapus dalam **satu commit**; folder tetap ikut menghapus seluruh isinya.
- Tombol "Pilih semua" menghitung seluruh item di lokasi aktif, dan label tombol merah berubah mengikuti jumlah pilihan ("Hapus 3 item"). Setelah selesai, muncul ringkasan jumlah file yang benar-benar terhapus.

**Perbaikan error 422 & "Kosongkan" kini menghabiskan riwayat juga + hapus beberapa folder sekaligus (versionCode 37):** tindak lanjut dari pemakaian nyata:
- **Error "data invalid 422" saat Kosongkan repository — DIPERBAKI.** Akar masalah: GitHub menolak pembuatan *tree* kosong via endpoint `git/trees` (respons 422 *Invalid tree info*). Solusinya: aplikasi kini memakai **tree kosong bawaan git** (`4b825dc…4904`) yang selalu dikenal git tanpa perlu dibuat — fitur kosongkan kini berjalan mulus. Sudah diuji langsung ke API GitHub sebelum dirilis.
- **Riwayat commit kini ikut terhapus saat Kosongkan** — sesuai permintaan, yang tersisa hanyalah **RELEASE**. Kosongkan membuat *commit orphan* (commit baru tanpa induk) berisi tree kosong, lalu memaksa branch ke commit tersebut: daftar file jadi 0 dan halaman riwayat commit branch tinggal 1 commit. Tag & release tidak tersentuh — aset rilis tetap bisa dilihat dan diunduh seperti biasa (terverifikasi: unduhan arsip source dari tag lama tetap berfungsi).
- **Fitur baru "Hapus beberapa folder…"** — di menu titik-tiga halaman repository (zona merah). Muncul dialog berisi daftar semua folder di lokasi saat ini dengan kotak centang: centang satu, beberapa, atau tekan "Pilih semua", lalu tombol merah menghapus semuanya **dalam satu commit** (bukan satu per satu). Daftar bisa dimuat dari folder mana pun — buka folder dulu, lalu pilih folder-folder di dalamnya yang mau dihapus.

**Kosongkan repository dalam satu ketukan — release tetap aman (versionCode 36):** fitur baru yang sering diminta pengguna penyimpanan:
- **Menu "Kosongkan repository"** di menu titik-tiga halaman repository (dikelompokkan bersama "Hapus repository" di zona merah). Cocok saat Anda ingin mengulang isi repo dari nol tanpa menghapus repository-nya.
- **Semua file hilang dalam SATU commit** — bukan menghapus file satu per satu (yang lama & berisiko kena rate-limit). Aplikasi membuat *tree kosong* lalu commit sekali dan memindahkan branch ke commit tersebut; ratusan file selesai dalam hitungan detik.
- **Riwayat, tag, dan RELEASE tidak tersentuh** — cara kerjanya murni menambah commit baru di atas sejarah lama, sehingga commit lama tetap bisa dilihat, tag tetap menunjuk commit yang sama, dan semua rilis GitHub (beserta aset APK-nya) tetap utuh dan bisa diunduh seperti biasa.
- **Dialog konfirmasi berlapis** — wajib mengetik **KOSONGKAN** terlebih dahulu (tombol merah baru aktif), plus catatan hijau yang menegaskan bahwa rilis tidak akan terhapus. Setelah selesai, daftar file langsung dimuat ulang ke kondisi kosong, commit terakhir & ukuran repo ikut diperbarui.

**Redesain "Modern Light" — bukan sekadar ganti warna (versionCode 35):** keluhan "tampilan masih terasa jadul, upgrade tidak terasa" dijawab dengan mengubah bahasa desain light secara menyeluruh, bukan hanya menyetel warna:
- **Kartu tanpa garis pinggir** — di tema terang semua kartu (beranda, profil, pengaturan, dialog) kini putih bersih **tanpa bingkai abu-abu** di atas kanvas `#F3F5F9`, ala Files by Google / GitHub mobile; bingkai tipis hanya tersisa di tema gelap. Ini pembeda terbesar antara tampilan lama dan baru.
- **Bayangan lembut berlapis** — kartu repository, kolom pencarian, dan tile layar masuk mendapat bayangan halus keabu-biruan (bukan hitam pekat) sehingga elemen utama tampak "melayang" dengan kedalaman modern.
- **Navigasi bawah melayang (floating pill)** — NavigationBar polos yang menempel di tepi diganti pil membulat dengan bayangan, berjarak dari tepi layar; bilah navigasi sistem Android dibuat **transparan** sehingga pil tampak benar-benar mengambang. Indikator item aktif: pil hijau di belakang ikon yang membesar saat dipilih.
- **Sudut lebih membulat di seluruh aplikasi** — skala shape global naik (komponen kecil 8dp → kartu 20dp → lembar 28dp) untuk kesan modern yang lembut.
- **Detail lain** — strip gradien tipis di atas kartu repository dihapus (bersih), ikon keadaan kosong kini dalam "medali" hijau, kolom pencarian jadi pil putih melayang, jarak antar kartu lebih lega.

**Tema terang kini bawaan & tampil lebih modern (versionCode 34):** aplikasi dirasakan "jadul" karena selama ini berjalan dengan tema gelap sebagai default dan sebagian warna aksen diambil dari palet GitHub versi dark bahkan saat tema terang aktif. Sekarang:
- **Terang = tema default** — pemasangan baru dan pembaruan ini langsung tampil terang; pilihan "Gelap" dan "Sistem" tetap tersedia di Pengaturan dan kini dihormati penuh (sekali migrasi otomatis mengangkat pengaturan gelap bawaan versi lama ke terang, sekali saja, tanpa pernah mengubah pilihan eksplisit Anda lagi).
- **Palet aksen ikut tema** — hijau, biru, ungu, kuning, merah, dan abu-abu aksen kini punya padanan versi terang (mis. hijau `#1A7F37`, biru `#0969DA`, abu `#656D76` ala GitHub light) sehingga ikon, bintang, tautan, dan tag selalu kontras dan tidak lagi tampak "pucat" di atas putih; tema gelap memakai palet dark yang sama seperti sebelumnya.
- **Skema terang dirapikan total** — latar kanvas lembut `#F6F8FA`, kartu putih bersih, garis batas `#D1D9E0`, wadah aksen hijau/biru/ungu/merah yang halus, plus warna inversi — tata letak yang sama terasa jauh lebih segar dan modern.
- **Bilah status & navigasi ikut tema** — dulu selalu gelap permanen dari tema XML (mode terang terasa setengah jadi); kini ikon bilah dan warnanya menyesuaikan tema aktif secara otomatis, dan layar pembuka (splash) ikut terang.

**Beranda lebih bersih, navigasi folder lebih mudah, dan menu titik-tiga tampil di tempat yang benar (versionCode 33):** tiga perbaikan dari masukan pemakaian nyata:
- **Chip filter di bawah pencarian Beranda DIHAPUS** — deretan tombol "Repo saya", "Semua GitHub", "Semua", "Publik", "Privat" dinilai hanya menambah kebisingan. Sekarang cukup satu kolom pencarian yang langsung menyaring repository Anda. Pencarian global GitHub tidak hilang: ia kini muncul **secara kontekstual** — hanya saat pencarian lokal tidak menemukan apa pun, muncul satu tombol *"Cari "kata-kata" di seluruh GitHub"*, dan saat mode global aktif ada banner tipis dengan tombol kembali ke repo saya.
- **Baris ".." (folder atas) di daftar file repository** — membuka folder di dalam repo kini selalu disodori baris pertama berupa ".. — Folder atas" ala file manager klasik; sekali ketuk langsung naik ke folder sebelumnya, tak perlu lagi mengandalkan breadcrumb atau tombol kembali di pojok layar.
- **Semua pop-up menu titik-tiga kini muncul tepat di bawah tombolnya** — dulu menu repository (pojok kanan atas halaman repo), menu branch, dan menu titik-tiga pada setiap baris file/folder meletakkan pop-up di pojok kiri layar karena popup mengikat ke baris penuh, bukan ke tombolnya. Ketiga menu (plus menu kartu repository di Beranda yang sudah benar) kini ter-anchoring ke tombol masing-masing.

**Ikon repository kini berbeda dari ikon folder (versionCode 32):** sebelumnya kartu repository di Beranda memakai ikon folder yang sama persis dengan folder di dalam repo — dua konsep berbeda terlihat identik. Sekarang **repository = ikon buku terbuka** (gaya GitHub) di tiga tempat: badge kartu repository di Beranda, panel ringkasan di halaman repository, dan ikon aktivitas "repository" di Profil (plus ikon keadaan kosong "Belum ada repository"). Folder & file di dalam repository tetap memakai ikon folder kuning yang lama — sekali pandang langsung terbedakan mana "gudang" dan mana "isinya".

**Unggahan tidak lagi "hampir selesai lalu mengulang dari awal" (versionCode 31):** akar keluhan "progress hampir 100% tapi proses mengulang lagi dengan kecepatan rendah" ditemukan dan dituntaskan di tiga titik:
- **Pengawas koneksi kini tahu apa yang terjadi**: selama paket dikirim, setiap byte melapor ke watchdog — dan setelah paket terkirim penuh, fase **menunggu server memproses** (index-pack bisa puluhan detik untuk paket besar) memakai batas longgar khusus. Dulu kedua fase ini tidak pernah melapor, sehingga watchdog mengira koneksi mati dan **memutus tepat saat progres hampir 100%** → kirim diulang → jatuh ke jalur lambat.
- **Laporan server dihormati**: konfirmasi resmi "unpack ok" dari server git kini **final** — verifikasi tambahan lewat protokol git + REST API hanya pelengkap dan tidak pernah membatalkan unggahan yang sudah diterima (dulu data REST yang basi membuat unggahan sukses dianggap gagal).
- **Lanjut, bukan mengulang**: bila pengiriman paket benar-benar gagal di tengah, aplikasi **memeriksa ulang isi repo dulu** — file yang ternyata sudah diterima server dilewati, dan paket dibangun ulang **hanya untuk yang kurang** (dasar commit terbaru). Jalur API pun sama: file yang sudah ada tidak dikirim ulang, dan pembanding progres diganti agar bar tetap jujur sampai 100%.

**Unggah secepat terminal — paket git (versionCode 30):** keluhan "unggah 1 MB atau ratusan file terasa lama, padahal via terminal sangat cepat" akhirnya dijawab di akar masalahnya. Unggahan kini mencoba **jalur paket git** (protokol smart-HTTP yang sama dipakai `git push` di terminal): **semua file dikirim dalam SATU koneksi** sebagai packfile biner terkompresi zlib — tanpa request per-file, tanpa jeda anti-limit, tanpa base64. Ratusan file kecil selesai dalam hitungan detik, bukan menit. Detail pentingnya:
- **Deteksi "up-to-date" ala git**: setiap file dihitung sha blob-nya di HP; file yang isinya **sudah ada di repo tidak dikirim ulang sama sekali** (dilewati dengan catatan jelas di log) — unggah ulang folder yang sama nyaris instan.
- **Isi sama = hemat kirim**: file berisi byte yang sama dengan objek lain di repo cukup dirujuk sha-nya, tanpa mengirim isi.
- **Fallback otomatis & transparan**: bila jalur paket tidak tersedia (izin token, jaringan tidak stabil), proses beralih sendiri ke jalur API blob lama yang terbukti andal — log menjelaskan alasan peralihannya; tidak ada unggahan yang gagal total karena jalur.
- **File besar (>95 MB) tetap otomatis via Git LFS** (pointer ikut dalam commit), dan **file 150 MB pun jalan** — aplikasi **tanpa batas jumlah file** (500 file kecil? silakan) dan tanpa batas ukuran selain aturan GitHub sendiri.
- **Log unggahan dibaca jelas**: tahap bernomor (ANALISIS → CEK ISI REPO → UNGGAH → COMMIT), per-file dengan jalurnya (paket/LFS/blob/identik), serta **ringkasan akhir** rapi: berapa file baru, identik, LFS, gagal, jalur yang dipakai, durasi, dan kecepatan.

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
