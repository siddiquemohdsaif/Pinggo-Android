# Native entry points are resolved by name from the bundled native views and call pipeline.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Gson creates persisted websocket envelopes and API models reflectively.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
