# Aturan R8 GitPush
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Model data dipakai lewat property langsung — aman; tapi jaga nama untuk debugging
-keep class com.gitpush.app.data.** { *; }

# Markwon (render markdown) — AAR-nya tidak membawa consumer rules sendiri;
# modul image juga mereferensi kelas svg/gif opsional yang tidak dipakai → dontwarn
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**
