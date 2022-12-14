
# Remove all the injar/outjar/libraryjar junk, the android ant script takes care of this

#-dontpreverify
-repackageclasses ''
-allowaccessmodification
#-useuniqueclassmembernames
-forceprocessing
# code/simplification/advanced,code/removal/advanced,class/merging/vertical,class/marking/final,!code/simplification/arithmetic
#-optimizations !code/simplification/arithmetic
-optimizationpasses 13
-keepattributes *Annotation*

-printmapping proguard.map

-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# DEBUG. please KEEP ALL THE NAMES.
#-keepnames class ** { *; }

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers class * implements android.os.Parcelable {
    static android.os.Parcelable$Creator CREATOR;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

-keep public enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# click methods from XML
-keepclassmembers class * {
    public void *Clicked(android.view.View);
}

# Classes with native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Fragments
-keep public class * extends android.support.v4.app.Fragment
-keep public class * extends android.app.Fragment

-dontwarn android.support.v4.**
#-keep class android.support.v4.** { *; }
#-keep interface android.support.v4.app.** { *; }
#-keep public class * extends android.support.v4.**

-keep public class android.transition.Transition
-keep public class android.app.Notification

# Remove logging

-assumenosideeffects class android.util.Log {
    public static *** e(...);
    public static *** w(...);
    public static *** d(...);
    public static *** v(...);
    public static *** wtf(...);
}
#    public static *** i(...);

# Roman Popov 19.02.2017 changed
#-assumenosideeffects class app.common.L {
-assumenosideeffects class app.common.debug.L {
    public static void d(...);
    public static void e(...);
    public static void w(...);
}

#-dontwarn android.support.**
-keep class javax.**
#-keep class org.*
-keep class com.android.vending.billing.**
#-keep class org.xbill.DNS.**

# Roman Popov 19.02.2017 removed
#-keep class sun.net.spi.nameservice.NameService { *; }

# This dnsjava class uses old Sun API
-dontnote org.xbill.DNS.spi.DNSJavaNameServiceDescriptor
-dontwarn org.xbill.DNS.spi.DNSJavaNameServiceDescriptor

#-libraryjars ./libs/dnsjava-2.1.6.jar
