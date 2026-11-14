package com.vps.qing.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 全 App 统一的「邮箱 / 短信验证码」发送冷却（v1.4.18）。
 *
 * 站点 `POST /email/code` 是**全局 60 秒限频**：不区分 action（verify/update）、
 * 不区分新旧邮箱、也不区分从哪个页面发起。因此冷却必须满足：
 *  - 任意一处发码成功后，全局所有「获取验证码」按钮立即禁用并进入倒计时；
 *  - 跨页面、跨步骤（验证原邮箱 → 设置新邮箱）、跨进程重启都保持；
 *  - 倒计时结束自动恢复可点击，等待下一次唤醒。
 *
 * 以持久化的发送时间戳为唯一事实来源（重启后重新计算剩余秒数），
 * [remaining] 用 StateFlow 广播给所有按钮，UI 不需要各自维护 countdown。
 */
object EmailCodeCooldown {

    /** 与站点 /email/code 的限频窗口一致。 */
    const val COOLDOWN_SECONDS = 60

    private const val PREF = "qingvps_code_cooldown"
    private const val KEY_SENT_AT = "sent_at_ms"

    private var lastSentAtMs = 0L
    private var appContext: Context? = null
    private val _remaining = MutableStateFlow(0)
    val remaining: StateFlow<Int> = _remaining.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    /** Application.onCreate 调用：恢复上次未结束的冷却。 */
    fun init(context: Context) {
        appContext = context.applicationContext
        lastSentAtMs = sp()?.getLong(KEY_SENT_AT, 0L) ?: 0L
        refresh()
        startTicker()
    }

    /** 发码成功后调用：启动 / 重置完整 60 秒冷却。 */
    fun markSent() {
        lastSentAtMs = System.currentTimeMillis()
        sp()?.edit()?.putLong(KEY_SENT_AT, lastSentAtMs)?.apply()
        refresh()
        startTicker()
    }

    /**
     * 站点明确返回「一分钟只允许申请一次」等限频错误时调用：
     * 即便本地没记录到本次发送，也要把按钮锁进冷却，避免用户面对点不动又说不清原因的死状态。
     */
    fun markRateLimited() = markSent()

    fun canSend(): Boolean = _remaining.value <= 0

    /** 剩余冷却秒数（供非 Compose 调用方读取）。 */
    fun remainingSeconds(): Int = _remaining.value

    private fun sp() = appContext?.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun calcRemaining(): Int {
        if (lastSentAtMs <= 0L) return 0
        val elapsed = (System.currentTimeMillis() - lastSentAtMs) / 1000L
        return (COOLDOWN_SECONDS - elapsed).coerceIn(0L, COOLDOWN_SECONDS.toLong()).toInt()
    }

    private fun refresh() {
        _remaining.value = calcRemaining()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive && calcRemaining() > 0) {
                delay(1000L)
                refresh()
            }
        }
    }
}
