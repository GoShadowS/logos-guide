# Минификация release выключена до проверки на устройстве (см. docs/BUILD_APK_RU.md, раздел 14).
# Правила ниже уже покрывают Gson-модели, сущности резервной копии и DTO экспорта.
# Gson читает модели и резервные копии через отражение: поля нельзя удалять и переименовывать.
-keep class com.logos.navigator.data.model.** { *; }
-keep class com.logos.navigator.data.database.entity.** { *; }
-keep class com.logos.navigator.data.repository.FavoritesExport { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod

# Аннотации статического анализа не нужны во время выполнения.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
