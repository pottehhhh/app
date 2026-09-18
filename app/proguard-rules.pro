# WEIRDNET ProGuard / R8 rules

# Keep WireGuard native tunnel classes (JNI callbacks resolve by name/signature).
-keep class com.wireguard.android.backend.** { *; }
-keep class com.wireguard.config.** { *; }
-keepclassmembers class com.wireguard.android.backend.GoBackend$* { *; }

# Room entities keep their fields for reflection-free codegen; nothing extra needed
# since we use KSP-generated code, but keep migrations resilient:
-keep class ir.weirdnet.client.data.db.** { *; }

# Kotlin coroutines / serialization metadata
-dontwarn kotlinx.coroutines.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*

# ML Kit barcode scanning
-keep class com.google.mlkit.vision.barcode.** { *; }

# Keep our own model classes intact (used with reflection-free manual mapping,
# but safe to keep names for diagnostics/logging clarity).
-keep class ir.weirdnet.client.data.model.** { *; }

# --- Xray-core integration (2dust/AndroidLibXrayLite + heiher/hev-socks5-tunnel) ---
# Keep gomobile-generated bindings: their method signatures are called from
# Kotlin by name (XrayCoreBridge.kt) and via Go's own JNI reflection internally.
-keep class libv2ray.** { *; }
-keepclassmembers class libv2ray.** { *; }

# Keep the hand-written JNI shim for hev-socks5-tunnel: its `external fun`
# declarations must keep their exact names/signatures for the native .so's
# JNI symbol table to bind against at runtime.
-keep class hev.htproxy.** { *; }
-keepclasseswithmembernames class hev.htproxy.** {
    native <methods>;
}
