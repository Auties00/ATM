-keepattributes *Annotation*

-keep class it.atm.app.data.remote.rest.** { *; }
-keep class it.atm.app.data.local.db.** { *; }

-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
