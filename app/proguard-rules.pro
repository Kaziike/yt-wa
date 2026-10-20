# Proguard rules for YouTubeLite
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepclassmembers class com.brave.youtubelite.** { *; }
