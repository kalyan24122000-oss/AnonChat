# ProGuard rules for AnonChat
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.anonchat.app.data.model.** { *; }
-keep class io.socket.** { *; }
-keep class okhttp3.** { *; }
