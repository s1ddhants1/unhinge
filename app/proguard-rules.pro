# Keep LibXposed module entry points.
-keep public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
    public void on*(...);
}
-keep class io.github.libxposed.service.XposedProvider { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn io.github.libxposed.api.**
# Keep settings UI and serialized companion models
-keep class io.github.s1ddhants1.unhinge.ui.** { *; }
-keep class io.github.s1ddhants1.unhinge.model.** { *; }
-keepclassmembers class io.github.s1ddhants1.unhinge.model.** { *; }
