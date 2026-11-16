# SPDX-License-Identifier: GPL-3.0-or-later
# Jembatan JavaScript (WebView) harus tetap utuh agar shim dapat memanggil Kotlin.
-keepattributes JavascriptInterface
-keepclassmembers class app.snapgent.webhost.ExtensionHost {
    @android.webkit.JavascriptInterface <methods>;
}

# kotlinx.serialization (model konfigurasi)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.snapgent.**$$serializer { *; }
-keepclassmembers class app.snapgent.** { *** Companion; }
-keepclasseswithmembers class app.snapgent.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Dependensi yang memakai reflektif / opsional di JVM
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn java.lang.management.**
-dontwarn org.bouncycastle.**
-dontwarn com.jcraft.jsch.**
