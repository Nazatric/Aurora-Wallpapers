# kotlinx.serialization: keep generated serializers for DTOs.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.auroro.wallpapers.**$$serializer { *; }
-keepclassmembers class com.auroro.wallpapers.** { *** Companion; }
-keepclasseswithmembers class com.auroro.wallpapers.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit service interfaces are accessed reflectively (via java.lang.reflect.Proxy).
-keep,allowobfuscation interface com.auroro.wallpapers.core.network.**Api
-keepattributes Exceptions
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
