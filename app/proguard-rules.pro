-keep class com.logos.navigator.data.model.** { *; }
-keep class com.logos.navigator.data.database.entity.** { *; }
-keep class com.logos.navigator.data.repository.FavoritesExport { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod

-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
