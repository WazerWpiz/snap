// SPDX-License-Identifier: GPL-3.0-or-later
// Root build: hanya mendaftarkan plugin. Konfigurasi tiap modul ada di build.gradle.kts masing-masing.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
