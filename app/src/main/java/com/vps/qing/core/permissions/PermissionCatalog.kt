package com.vps.qing.core.permissions

import android.Manifest
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 权限分组：必要 = 应用基础运行所需；可选 = 由用户按场景自行选择。 */
enum class PermissionGroup { REQUIRED, OPTIONAL }

/**
 * 一条权限声明（v1.4.17 权限合规体系）。
 *
 * 尊重知情权：每条都写清「用途 / 不开启会怎样 / 是否上传数据」，且 [defaultEnabled]
 * 一律为 false——应用首次启动时所有权限都是关闭状态，只有用户在独立的权限管理页
 * 明确同意后才写入开关并（对运行时权限）发起系统申请。
 */
data class PermissionSpec(
    val id: String,
    val label: String,
    val purpose: String,
    val deniedImpact: String,
    val group: PermissionGroup,
    /** 需要向系统申请的运行时权限；为空 = 系统安装期权限或纯应用内功能开关。 */
    val androidPermissions: List<String> = emptyList(),
    val defaultEnabled: Boolean = false
)

object PermissionCatalog {
    const val NETWORK = "network"
    const val STORAGE = "storage"
    const val NOTIFICATIONS = "notifications"
    const val LOCATION = "location"
    const val EXTERNAL_APPS = "external_apps"
    const val CLIPBOARD = "clipboard"
    const val CAMERA = "camera"

    /** 全部 7 项；「定位」在需求里出现两次，已去重。 */
    val ALL: List<PermissionSpec> = listOf(
        PermissionSpec(
            id = NETWORK,
            label = "本地网络权限",
            purpose = "访问互联网与局域网，连接站点接口获取商品、订单、公告等数据",
            deniedImpact = "无法连接站点，应用所有在线功能不可用（系统安装期自动授予，属基础运行条件）",
            group = PermissionGroup.REQUIRED,
            androidPermissions = listOf(Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE)
        ),
        PermissionSpec(
            id = STORAGE,
            label = "存储 / 相册读写",
            purpose = "从相册选择自定义背景图、读取本机图片",
            deniedImpact = "不能使用自定义背景图；应用仍可用，且读取范围仅限你主动选择的图片",
            group = PermissionGroup.REQUIRED,
            androidPermissions = listOf(
                // Android 13+ 用细粒度媒体权限；以下版本用传统存储读取权限（Manifest 里限定 maxSdk 32）
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ),
        PermissionSpec(
            id = NOTIFICATIONS,
            label = "通知权限",
            purpose = "支付到账、待支付提醒、工单回复、新公告、续费到期、账号安全提醒",
            deniedImpact = "不会收到任何推送；所有消息仍可在应用内查看，不影响购买与支付",
            group = PermissionGroup.OPTIONAL,
            androidPermissions = listOf(Manifest.permission.POST_NOTIFICATIONS)
        ),
        PermissionSpec(
            id = LOCATION,
            label = "定位权限",
            purpose = "按所在地区推荐更近的机房与线路（仅在本机使用，不上传坐标）",
            deniedImpact = "不提供地区化推荐，选购与下单流程完全不受影响",
            group = PermissionGroup.OPTIONAL,
            androidPermissions = listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ),
        PermissionSpec(
            id = EXTERNAL_APPS,
            label = "打开外部应用权限",
            purpose = "支付时拉起支付宝 / 微信 / 浏览器等外部应用完成付款",
            deniedImpact = "支付仅在应用内二维码与内置页面完成，不会自动跳转其它应用",
            group = PermissionGroup.OPTIONAL
        ),
        PermissionSpec(
            id = CLIPBOARD,
            label = "读取 / 写入剪切板",
            purpose = "一键复制订单号、支付链接、工单号；从剪切板粘贴验证码",
            deniedImpact = "不提供复制/粘贴快捷入口，你仍可手动长按复制",
            group = PermissionGroup.OPTIONAL
        ),
        PermissionSpec(
            id = CAMERA,
            label = "使用摄像头",
            purpose = "扫描站点二维码（如支付码、登录二维码）",
            deniedImpact = "不提供扫码入口，不影响任何购买、支付与管理功能",
            group = PermissionGroup.OPTIONAL,
            androidPermissions = listOf(Manifest.permission.CAMERA)
        )
    )

    val REQUIRED: List<PermissionSpec> = ALL.filter { it.group == PermissionGroup.REQUIRED }
    val OPTIONAL: List<PermissionSpec> = ALL.filter { it.group == PermissionGroup.OPTIONAL }

    fun byId(id: String): PermissionSpec? = ALL.firstOrNull { it.id == id }

    /**
     * 按系统版本过滤出「当前真正需要申请」的运行时权限：
     *  - 通知权限仅 Android 13+；
     *  - READ_MEDIA_IMAGES 仅 Android 13+，旧版改用 READ_EXTERNAL_STORAGE；
     *  - 安装期权限（INTERNET / ACCESS_NETWORK_STATE）不参与申请。
     */
    fun runtimePermissions(spec: PermissionSpec): List<String> {
        val installTime = setOf(Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE)
        return spec.androidPermissions.filter { p ->
            when (p) {
                Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_IMAGES ->
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && p !in installTime
                Manifest.permission.READ_EXTERNAL_STORAGE ->
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && p !in installTime
                else -> p !in installTime
            }
        }
    }

    /** 勾选的开关对应的实际系统权限（去重），供一次 RequestMultiplePermissions 使用。 */
    fun runtimePermissionsFor(enabledIds: Set<String>): List<String> =
        ALL.filter { it.id in enabledIds }.flatMap { runtimePermissions(it) }.distinct()
}

/**
 * 权限开关的持久化（默认全部关闭）。
 *
 * 这是「知情权」的唯一事实来源：只有这里为 true，App 才会在对应功能里使用
 * 该权限（例如拉起外部应用、读写剪切板、发通知），系统是否授权另由运行时检查。
 */
class PermissionPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("qingvps_permissions", Context.MODE_PRIVATE)

    private fun readMap(): Map<String, Boolean> =
        PermissionCatalog.ALL.associate { it.id to sp.getBoolean("perm_${it.id}", it.defaultEnabled) }

    private val _enabled = MutableStateFlow(readMap())
    val enabled: StateFlow<Map<String, Boolean>> = _enabled.asStateFlow()

    private val _onboardingDone = MutableStateFlow(sp.getBoolean("onboarding_done", false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun isEnabled(id: String): Boolean = _enabled.value[id] ?: false

    fun setEnabled(id: String, value: Boolean) {
        sp.edit().putBoolean("perm_$id", value).apply()
        _enabled.value = _enabled.value + (id to value)
    }

    fun setEnabledAll(values: Map<String, Boolean>) {
        val edit = sp.edit()
        values.forEach { (id, v) -> edit.putBoolean("perm_$id", v) }
        edit.apply()
        _enabled.value = readMap()
    }

    fun enabledIds(): Set<String> = _enabled.value.filterValues { it }.keys

    /** 首次引导完成（无论是否开启可选权限，都尊重用户选择，不再重复弹出整页）。 */
    fun completeOnboarding() {
        sp.edit().putBoolean("onboarding_done", true).apply()
        _onboardingDone.value = true
    }
}
