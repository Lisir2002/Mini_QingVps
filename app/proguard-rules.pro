# ============================================================================
# QingVPS R8 / ProGuard 规则（v1.4.27 开启混淆与资源压缩）
# 原则：第三方库用官方 consumer rules + 保守 keep；本 App 只精确 keep 反射点。
# ============================================================================

# ---- 通用属性保留（Gson / Retrofit / 注解依赖）----
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-dontwarn sun.misc.**

# ---- Gson ----
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# @SerializedName / @Expose 字段由属性注解保留；保留被注解的成员本身
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}

# ---- Retrofit / OkHttp / Okio ----
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
# Retrofit 接口的动态代理与返回类型签名
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ---- 本 App：Gson 直接反序列化的顶层信封（字段名映射）----
-keep class com.vps.qing.core.network.ApiEnvelope { *; }
-keep class com.vps.qing.data.api.CaptchaRefreshResponse { *; }

# ---- 枚举 valueOf：ServicePanel 由导航 token 字符串反查（valueOf("HOSTS")），
#      必须连枚举常量名一起保留，否则常量被改成 b/c/d 后 valueOf 抛 IllegalArgumentException。
#      用 -keep enum { *; } 整类保留（含常量字段），不能只 keep 方法。----
-keep enum com.vps.qing.feature.profile.ServicePanel { *; }

# ---- Android 组件 / Compose（保守保险；AGP 默认规则已覆盖大部分）----
-keep class com.vps.qing.MainActivity { *; }
-keep class * extends android.app.Application
-keep class * extends androidx.lifecycle.ViewModel { *; }
# Compose runtime 通过反射访问 @Composable 元数据
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---- Kotlin ----
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ---- Coil / 图片加载 ----
-dontwarn coil.**
-dontwarn androidx.palette.**
