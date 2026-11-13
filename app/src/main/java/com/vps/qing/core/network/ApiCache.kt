package com.vps.qing.core.network

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * L1 内存响应缓存（v1.4.4）。缓存的是**解压后的 JSON 响应体字符串**，
 * 因此与具体 Repository / 数据模型完全解耦，所有接口自动受益，Repository 无需改动。
 *
 * 四个能力：
 *  1. TTL + LRU：按 [CacheRule.ttlMs] 判定新鲜度，超过 [maxEntries] 淘汰最久未用；
 *  2. 会话隔离：key 带 session 指纹，切换账号不会读到别人的私有数据；
 *  3. in-flight 去重：同一 key 的并发请求只放一次网络，其余复用同一结果；
 *  4. stale 兜底：网络失败时允许返回过期副本，避免弱网下整页空白。
 *
 * 只缓存业务成功的响应（[businessStatus] == 200），失败响应不会被固定住。
 */
class ApiCache(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxEntries: Int = 128,
    val maxBodyChars: Int = 1024 * 1024
) {

    data class Entry(
        val key: String,
        val path: String,
        val body: String,
        val storedAt: Long,
        val rule: CacheRule
    ) {
        fun isFresh(now: Long): Boolean = now - storedAt < rule.ttlMs
    }

    private val lock = Any()
    private val memory = LinkedHashMap<String, Entry>(64, 0.75f, true)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<String>>()

    /** key 组成：会话指纹 + 方法 + 完整 URL（含 query，保证分页/搜索互不串数据）。 */
    fun key(method: String, url: String, session: String, path: String): String =
        "$session|$method|$url"

    fun fresh(key: String): String? = synchronized(lock) {
        val e = memory[key] ?: return null
        if (!e.isFresh(clock())) return null
        e.body
    }

    /** 过期副本，仅在网络失败时兜底。 */
    fun stale(key: String): String? = synchronized(lock) { memory[key]?.body }

    fun put(key: String, path: String, body: String, rule: CacheRule) {
        if (body.length >= maxBodyChars || businessStatus(body) != 200) return
        synchronized(lock) {
            memory[key] = Entry(key, path, body, clock(), rule)
            while (memory.size > maxEntries) {
                val eldest = memory.entries.iterator().next().key
                memory.remove(eldest)
            }
        }
    }

    /**
     * 写操作成功后失效相关缓存。prefix 为空表示清空全部。
     * 按记录的相对路径匹配，而不是拼在 key 里，避免误伤同前缀但不同接口的数据。
     */
    fun invalidate(prefix: String) {
        synchronized(lock) {
            if (prefix.isEmpty()) {
                memory.clear()
            } else {
                memory.entries.removeIf { it.value.path.startsWith(prefix) }
            }
        }
    }

    fun clear() {
        synchronized(lock) { memory.clear() }
    }

    fun size(): Int = synchronized(lock) { memory.size }

    // ---------------------------------------------------------------- in-flight 去重

    /** 返回 (deferred, 是否已有请求在飞)。false 表示当前调用者负责发起真实请求。 */
    fun joinOrCreate(key: String): Pair<CompletableDeferred<String>, Boolean> {
        val created = CompletableDeferred<String>()
        val existing = inFlight.putIfAbsent(key, created)
        return if (existing != null) existing to true else created to false
    }

    fun succeed(key: String, deferred: CompletableDeferred<String>, body: String) {
        deferred.complete(body)
        inFlight.remove(key, deferred)
    }

    fun fail(key: String, deferred: CompletableDeferred<String>, error: Throwable) {
        deferred.completeExceptionally(error)
        inFlight.remove(key, deferred)
    }

    fun pendingCount(): Int = inFlight.size

    companion object {
        private val STATUS = Regex(""""status"\s*:\s*(-?\d+)""")

        /**
         * 轻量提取业务状态码：站点 HTTP 恒为 200，业务状态在 body.status。
         * 只扫开头一段，避免为每个响应做完整 JSON 反序列化（旧 AuthInterceptor 的做法）。
         */
        fun businessStatus(body: String): Int? =
            STATUS.find(body.take(512))?.groupValues?.get(1)?.toIntOrNull()
    }
}
