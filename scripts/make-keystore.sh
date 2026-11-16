#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Membuat keystore release untuk penandatanganan APK dan mencetak nilai secret GitHub.
# Keystore disimpan di secrets/ (sudah di .gitignore) dan TIDAK boleh di-commit.
#
# Pemakaian: scripts/make-keystore.sh [alias]
set -euo pipefail

ALIAS="${1:-snapgent}"
DIR="$(cd "$(dirname "$0")/.." && pwd)/secrets"
KS="${DIR}/snapgent-release.jks"

command -v keytool >/dev/null 2>&1 || { echo "keytool tidak ditemukan. Pasang JDK 17." >&2; exit 1; }
mkdir -p "$DIR"
if [ -e "$KS" ]; then
  echo "Sudah ada: $KS. Hapus manual bila ingin membuat ulang (berhati-hati: APK lama tidak bisa diperbarui)." >&2
  exit 1
fi

read -rsp "Password keystore (min 6 karakter): " STORE_PASS; echo
read -rsp "Password key (boleh sama): " KEY_PASS; echo
[ "${#STORE_PASS}" -ge 6 ] || { echo "Password terlalu pendek." >&2; exit 1; }

keytool -genkeypair -v \
  -keystore "$KS" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
  -dname "CN=Snapgent, O=Snapgent, C=ID"

B64="$(base64 -w0 "$KS" 2>/dev/null || base64 "$KS" | tr -d '\n')"
cat <<MSG

Keystore dibuat di: $KS

Tambahkan secret berikut di GitHub (Settings > Secrets and variables > Actions):
  KEYSTORE_BASE64   = (isi file $KS.b64, yaitu keystore dalam base64)
  KEYSTORE_PASSWORD = (password keystore yang Anda masukkan)
  KEY_ALIAS         = $ALIAS
  KEY_PASSWORD      = (password key yang Anda masukkan)

Atau dengan GitHub CLI (tanpa menampilkan nilai di riwayat shell):
  gh secret set KEYSTORE_BASE64 < "$KS.b64"
  gh secret set KEYSTORE_PASSWORD
  gh secret set KEY_ALIAS --body "$ALIAS"
  gh secret set KEY_PASSWORD

PERINGATAN: simpan cadangan $KS di tempat aman. Hilangnya keystore berarti pembaruan APK tidak bisa dipasang di atas versi lama.
MSG
printf '%s' "$B64" > "$KS.b64"
echo "Versi base64 ditulis ke $KS.b64 (juga di dalam secrets/, tidak di-commit)."
