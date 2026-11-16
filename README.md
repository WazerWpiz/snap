# Snapgent Android

Port Android (APK) dari [herdodp/snapgent-connector](https://github.com/herdodp/snapgent-connector).
Aplikasi ini menjalankan situs-situs chat AI di dalam WebView dan menyambungkannya ke
server MCP (workspace bawaan + server remote HTTP), tanpa Android Studio dan tanpa PC penghubung.

- **Lisensi:** GPL-3.0-or-later. Lihat `LICENSE`.
- **Atribusi:** kode ekstensi di `vendor/snapgent-extension/` (dan logika tool workspace, parser perintah,
  serta protokol MCP) berasal dari `herdodp/snapgent-connector`. Folder `vendor/` disalin apa adanya dan
  **tidak diubah**; seluruh adaptasi Android ada di modul Kotlin.

## Struktur folder

```
snap/
├── app/                      # Aplikasi (Compose UI, DI, workspace SAF, service, tab)
│   ├── src/main/java/app/snapgent/android/
│   │   ├── AppContainer.kt       # wiring manual semua komponen
│   │   ├── Approvals.kt          # gerbang persetujuan tool (dialog)
│   │   ├── Tabs.kt               # multi-tab provider (WebView per tab)
│   │   ├── WorkspaceManager.kt   # folder privat / SAF
│   │   ├── SafWorkspaceFs.kt     # implementasi WorkspaceFs di atas DocumentFile
│   │   ├── service/SessionService.kt  # foreground service opsional
│   │   └── ui/                   # AppRoot, ChatScreen, McpScreen, SettingsScreen, LogsScreen
│   ├── src/main/res/             # string, tema, ikon adaptif
│   └── proguard-rules.pro
├── webview-host/             # WebView provider, shim chrome.*, bridge JS <-> Kotlin
│   └── src/main/assets/snapgent-android/shim.js
├── mcp-core/                 # model tool, registry, kebijakan izin, router pesan, parser perintah (+ tes)
├── tools-workspace/          # 28 tool workspace (port vscode_mcp.py), JGit, ShellRunner (+ tes)
│   └── src/main/resources/workspace-tools.json
├── mcp-remote-client/        # klien MCP Streamable HTTP (JSON & SSE) (+ tes MockWebServer)
├── settings/                 # AppSettings, DataStore, impor/ekspor JSON (+ tes)
├── vendor/
│   ├── snapgent-extension/   # salinan upstream (manifest, core, providers) - JANGAN diedit
│   └── LICENSE               # lisensi upstream
├── scripts/
│   ├── make-keystore.sh          # membuat keystore release + daftar secret GitHub
│   └── gen-workspace-schemas.py  # regenerasi workspace-tools.json dari vscode_mcp.py
├── .github/workflows/android.yml # CI: lint, test, assembleDebug, assembleRelease, rilis tag
├── gradle/libs.versions.toml     # version catalog
├── gradlew, gradlew.bat, gradle/wrapper/
└── settings.gradle.kts, build.gradle.kts, gradle.properties
```

## Fitur

- Pilih provider (ChatGPT, Claude, Gemini, Kimi, GLM, Arena, Meta AI, Ptero, Grok, Mistral, Qwen, DeepSeek)
  dan buka beberapa tab sekaligus. Tab yang tidak tampil otomatis dijeda (`onPause`) agar hemat memori & baterai.
- Workspace: folder privat aplikasi, atau folder pilihan lewat Storage Access Framework (indikator di bar atas).
- Manajer MCP: server workspace bawaan (aktif/nonaktif), tambah/hapus/toggle server remote HTTP, tes koneksi.
- Pengaturan: tema (sistem/terang/gelap), kategori tool, konfirmasi per kategori, override per tool
  (`allow` / `confirm` / `deny`), auto-kirim hasil, batas ukuran respons, editor system prompt tambahan,
  foreground service opsional.
- Konfirmasi manusia untuk kategori tulis, hapus, eksekusi, dan git-write (dialog di atas layar).
- Log viewer, ekspor/impor konfigurasi JSON (termasuk data `chrome.storage` milik core, mis. `zsCustomPrompt`).

## Menyiapkan secret untuk build release (opsional)

Tanpa secret, build tetap berjalan dan release ditandatangani **debug keystore** (APK bisa dipasang,
tetapi tidak cocok untuk distribusi).

1. Di mesin dengan JDK 17: `scripts/make-keystore.sh` (membuat `secrets/snapgent-release.jks`, tidak di-commit).
2. Tambahkan secret di **Settings > Secrets and variables > Actions** repo GitHub Anda:

| Secret | Isi |
|---|---|
| `KEYSTORE_BASE64` | isi `secrets/snapgent-release.jks.b64` |
| `KEYSTORE_PASSWORD` | password keystore |
| `KEY_ALIAS` | alias key (default `snapgent`) |
| `KEY_PASSWORD` | password key |

Atau dengan GitHub CLI: `gh secret set KEYSTORE_BASE64 < secrets/snapgent-release.jks.b64`.

**Jangan** commit `*.jks`, `keystore.properties`, atau folder `secrets/`. `.gitignore` sudah mengecualikannya.
Simpan cadangan keystore di tempat aman; kehilangannya berarti pembaruan tidak bisa dipasang di atas APK lama.

## Menjalankan build

CI berjalan otomatis pada:

- **push** ke branch mana pun,
- **pull request**,
- **workflow_dispatch** (tombol "Run workflow" di tab Actions),
- **tag** berawalan `v` (mis. `v0.2.0`).

Langkah CI: validasi wrapper → `lintDebug` → `test testDebugUnitTest` → `assembleDebug` → `assembleRelease`.
Hasil APK diunggah sebagai artifact `snapgent-apk-<run>` (retensi 14 hari). Untuk tag `v*`, `versionName`
diambil dari tag (tanpa `v`) dan APK dipublikasikan sebagai GitHub Release.

Build lokal (bila punya JDK 17 dan Android SDK): `./gradlew assembleDebug`.

## Memasang APK

1. Unduh `app-debug.apk` atau `app-release.apk` dari artifact atau halaman Release.
2. Di Android, izinkan pemasangan dari sumber tidak dikenal untuk browser/pengelola file Anda.
3. Buka APK dan pasang. Minimal Android 8.0 (API 26). Android System WebView harus versi terbaru
   (perbarui lewat Play Store).

Pengaturan dilakukan sepenuhnya di dalam aplikasi (tab **Pengaturan** dan **MCP**). Tidak ada file konfigurasi manual.

## Keterbatasan

- **stdio MCP tidak didukung di Android.** Upstream menjalankan server stdio sebagai proses lokal dan
  bridge WebSocket di `127.0.0.1:17613`. Android tidak menjalankan proses seperti itu bagi aplikasi biasa,
  sehingga hanya **server MCP HTTP (Streamable HTTP, JSON atau SSE)** yang bisa ditambahkan sebagai remote.
  Server stdio harus dijalankan di tempat lain dan diekspos sebagai HTTP.
- **Tidak ada bridge WebSocket.** Komunikasi ekstensi ↔ bridge diganti dengan shim `chrome.runtime` /
  `chrome.storage` di WebView (`shim.js`). Logika `background.js` (heartbeat, status, daftar tool) disederhanakan.
- **Workspace SAF tidak mendukung git** (JGit dan `run_command` membutuhkan path file lokal) dan
  `run_project_script` / `run_tests`. Tool baca/tulis/edit/hapus tetap bekerja.
  Folder privat aplikasi mendukung semua tool, termasuk git, meski perintah shell terbatas pada
  apa yang tersedia di Android.
- **Selektor DOM provider bisa rusak** kapan saja ketika situs berubah. Ini sama dengan upstream.
  Dengan vendor baru, cukup ganti isi `vendor/snapgent-extension/`.
- **Login provider** dilakukan langsung di WebView; sesi disimpan di penyimpanan WebView aplikasi.
- **Unggah berkas dari situs (input file)** belum didukung: `onShowFileChooser` belum dipasang, sehingga
  tombol lampirkan gambar di situs AI belum membuka pemilih berkas. Perbaikan butuh plumbing
  activity-result; menunggu uji di perangkat nyata.
- **Penyimpanan kunci/token MCP** disimpan di DataStore (teks biasa dalam sandbox aplikasi). Jangan memasukkan
  token berhak akses penuh tanpa perlu.
- **Tidak ada tes instrumentasi (UI/WebView).** Tes unit mencakup parser perintah, router, tool workspace,
  klien MCP remote (MockWebServer), dan konfigurasi.
- **targetSdk 35** karena AGP 8.9.1 memakai compileSdk 35 secara resmi. Naikkan bila AGP diperbarui.
- **Build release tidak ditandatangani keystore resmi** sampai secret diisi (lihat di atas).

## Asumsi yang diambil

- Bahasa antarmuka: Indonesia.
- Provider yang diaktifkan: semua entri `content_scripts` di `manifest.json` upstream v3.9.8.
  Daftar provider dibaca otomatis dari manifest saat aplikasi berjalan.
- Workspace bawaan (id `vscode`) aktif secara default dan memakai folder privat sampai pengguna memilih SAF.
- Kategori default yang meminta konfirmasi: write, delete, exec, git_write, remote.
- Batas ukuran respons default 60.000 karakter; batas tunggu persetujuan 90 detik (tolak bila habis).

## Status verifikasi

- Build terakhir di CI (run `43798706136`, commit `4b04b84`): **hijau** untuk `lintDebug`, `test testDebugUnitTest`,
  `assembleDebug`, dan `assembleRelease`. APK diunggah sebagai artifact.
- Commit berikutnya diverifikasi **penuh** di host arm64 (JDK 17, Android SDK 35): `test`/`testDebugUnitTest`
  semua modul lolos, `lintDebug` hijau, dan `assembleDebug` + `assembleRelease` menghasilkan APK
  (`app-debug.apk` 12,2 MB; `app-release.apk` 2,2 MB setelah R8/resource shrinking, ditandatangani
  debug keystore karena `KEYSTORE_BASE64` kosong). AAPT2 bawaan SDK yang hanya biner x86_64 dijalankan
  lewat `qemu-x86_64` + sysroot glibc amd64; AGP diarahkan ke biner itu lewat properti Gradle
  `android.aapt2FromMavenOverride`. Detail resipi ada di catatan skill host, bukan bagian dari repo.
- Rilis tag `v*` (langkah GitHub Release) belum diuji end-to-end.
- Perilaku runtime di perangkat (WebView per provider, login, SAF) belum diuji di perangkat nyata.

## Lisensi

GPL-3.0-or-later. Lihat `LICENSE`. Upstream: herdodp/snapgent-connector (GPL-3.0).
