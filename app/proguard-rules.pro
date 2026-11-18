# ProGuard / R8 rules for LiteTube
-keepattributes JavascriptInterface, *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebBridge entrypoints invoked reflectively from Chromium JavascriptInterface
-keep class com.kaz.litetube.playback.WebBridge {
    @android.webkit.JavascriptInterface <methods>;
}
