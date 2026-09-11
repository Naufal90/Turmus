# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.offlinep2p.**$$serializer { *; }
-keepclassmembers class com.offlinep2p.** {
    *** Companion;
}
-keepclasseswithmembers class com.offlinep2p.** {
    kotlinx.serialization.KSerializer serializer(...);
}
