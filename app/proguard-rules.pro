# Gson использует отражение для чтения локальных JSON-файлов.
# Для текущего приложения минификация release отключена. Если её включить,
# сохраните модели данных и их поля от удаления/переименования.
-keep class com.logos.navigator.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
