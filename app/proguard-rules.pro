# 号码查询助手 - ProGuard / R8 规则
# 当前 release 构建 isMinifyEnabled = false，本文件暂不参与混淆；
# 若要开启混淆（isMinifyEnabled = true），以下规则可保证关键能力不被裁掉。

# 四大组件保留
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.TypeConverter
-dontwarn androidx.room.paging.**

# Retrofit / OkHttp / Gson
-keepattributes Signature
-keepattributes *Annotation*, EnclosingMethod
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Kotlin 协程 / 序列化
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlin.coroutines.** { *; }

# 保留 native 方法
-keepclasseswithmembernames class * { native <methods>; }

# 保留 Parcelable / Serializable
-keep class * implements android.os.Parcelable { *; }
-keep class * implements java.io.Serializable { *; }

# libphonenumber / 离线归属地库
-dontwarn com.google.i18n.phonenumbers.**
-keep class com.google.i18n.phonenumbers.** { *; }
