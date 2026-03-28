# Keep the modern Xposed entry point reachable after shrinking.
-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep class * extends io.github.libxposed.api.XposedModule { *; }

-keep class * implements io.github.libxposed.api.XposedInterface$Hooker { *; }
