# Proguard rules for Personal Records
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn okio.**

# kotlinx.serialization
-keepclassmembers class * extends kotlinx.serialization.internal.GeneratedSerializer {
    *** descriptor;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
    public static **$Companion Companion;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}

# Retrofit2
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Network & Database Models
-keep class com.personalrecords.app.core.network.model.** { *; }
-keep class com.personalrecords.app.core.database.entity.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**


