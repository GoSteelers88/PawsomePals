# Keep classes and interfaces for reCAPTCHA
-keep class com.google.android.recaptcha.** { *; }
-keep interface com.google.android.recaptcha.** { *; }

# Keep SafetyNet classes
-keep class com.google.android.gms.safetynet.SafetyNet { *; }
-keep class com.google.android.gms.safetynet.SafetyNetApi { *; }

# Keep Firebase classes
-keep class com.google.firebase.** { *; }

# Keep Retrofit classes (if you're using Retrofit for network calls)
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Keep Gson classes (if you're using Gson for JSON parsing)
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Keep Coroutines classes
-keep class kotlinx.coroutines.** { *; }

# Keep Hilt classes
-keep class dagger.hilt.** { *; }

# Keep any classes that are referenced by AndroidManifest.xml
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep custom application class
-keep public class io.pawsomepals.app.PawsomePalsApplication { *; }

# Keep ViewModel classes
-keep public class * extends androidx.lifecycle.ViewModel { *; }

# Keep enumeration classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepnames class * implements java.io.Serializable

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Uncomment these if you need to keep line numbers for stack traces
#-keepattributes SourceFile,LineNumberTable
#-renamesourcefileattribute SourceFile