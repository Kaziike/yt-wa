# ProGuard / R8 rules for LiteTube
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebBridge entrypoints invoked reflectively from Chromium JavascriptInterface
-keep class com.brave.youtubelite.playback.WebBridge {
    @android.webkit.JavascriptInterface <methods>;
}
