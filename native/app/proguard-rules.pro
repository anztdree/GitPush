# Aturan R8 GitPush
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Model data dipakai lewat property langsung — aman; tapi jaga nama untuk debugging
-keep class com.gitpush.app.data.** { *; }
