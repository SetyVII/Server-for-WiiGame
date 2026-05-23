# ProGuard rules for TFG Motion Controller

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class * { @dagger.hilt.android.HiltAndroidApp *; }

# Keep ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }
