# LiMusic's release build shrinks unused library code but renames nothing: Tauri finds plugin
# commands, their argument classes and the JNI entry points by name, at runtime.
-dontobfuscate
-keep class com.limusic.desktop.** { *; }
-keep class app.tauri.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.fasterxml.jackson.**
-dontwarn app.tauri.**
