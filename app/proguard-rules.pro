# Gson 数据类与 @SerializedName 字段名
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.vps.qing.core.network.ApiEnvelope { *; }

# Retrofit / OkHttp
-dontwarn okio.**
-dontwarn okhttp3.**
-keepattributes Exceptions
-keep class retrofit2.** { *; }
