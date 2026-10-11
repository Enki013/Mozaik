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

