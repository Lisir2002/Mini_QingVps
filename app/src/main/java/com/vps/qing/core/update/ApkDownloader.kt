package com.vps.qing.core.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * v1.4.19：App 内真实下载更新包。
 *
 * 用户反馈「下载新版本的进度条不真实」——旧版根本没有 App 内下载，只有一个
 * 「前往 GitHub 下载」跳浏览器的按钮。这里用 OkHttp **流式**下载，按响应
 * `Content-Length`（缺失时用 Release assets 的 size 兜底）逐字节累计已下载量，
 * 进度完全来自真实写入字节数，不做任何模拟递增或定时假动画。
 *
 * 下载写入 `.tmp`，完整校验通过后原子重命名为目标文件，避免半成品被安装；
 * 协程取消会同时 cancel OkHttp call，中断阻塞读。
 */
object ApkDownloader {

    /** 真实下载进度（纯 JVM，便于单元测试）。 */
    data class Progress(val bytesRead: Long, val totalBytes: Long) {
        /** 无 Content-Length 且无兜底大小时为 true，UI 显示不确定态。 */
        val indeterminate: Boolean get() = totalBytes <= 0L
        /** 0f..1f；不确定态返回 0f。 */
        val fraction: Float
            get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
        val percent: Int get() = (fraction * 100).toInt()
    }

    /** 纯函数：由真实字节数计算进度比例，供单测锁定「不做假进度」。 */
    fun fractionOf(bytesRead: Long, totalBytes: Long): Float =
        Progress(bytesRead, totalBytes).fraction

    /** 人类可读的下载量，如 "3.4 MB / 12.9 MB"。纯 JVM。 */
    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "--"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS) // 不限总时长，大文件慢速下载也能完成
            .followRedirects(true)                 // GitHub release 会 302 到 objects.githubusercontent.com
            .followSslRedirects(true)
            .build()
    }

    /**
     * 流式下载到 [dest]，通过 [onProgress] 回传真实进度。
     * @param fallbackTotal 响应无 Content-Length 时使用的兜底总大小（Release asset size）。
     * @throws IllegalStateException  HTTP 失败 / 空响应 / 下载不完整 / 落盘失败
     * @throws CancellationException  协程取消（同时取消网络请求）
     */
    suspend fun download(
        url: String,
        dest: File,
        fallbackTotal: Long = 0L,
        onProgress: (Progress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "QingVPS-App")
            .header("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
            .build()
        val call = client.newCall(request)
        // 取消协程时同步关闭连接，避免阻塞读无法中断
        currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }

        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("下载失败（HTTP ${response.code}）")
            }
            val body = response.body ?: throw IllegalStateException("下载失败：空响应体")
            val headerTotal = body.contentLength()
            val total = if (headerTotal > 0) headerTotal else fallbackTotal

            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            if (tmp.exists()) tmp.delete()

            var written = 0L
            var lastEmitMs = 0L
            var lastEmitBytes = 0L
            body.byteStream().use { input ->
                tmp.outputStream().buffered().use { out ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        written += n
                        // 节流：每 100ms 或每 256KB 才回调一次，但仍传真实累计字节数
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 100 || written - lastEmitBytes >= 256 * 1024) {
                            onProgress(Progress(written, total))
                            lastEmitMs = now
                            lastEmitBytes = written
                        }
                    }
                }
            }

            // 完整性校验：非空 + 长度与声明一致（声明存在时）
            if (written <= 0) {
                tmp.delete()
                throw IllegalStateException("下载失败：内容为空")
            }
            if (total > 0 && written != total) {
                tmp.delete()
                throw IllegalStateException("下载不完整（${formatSize(written)}/${formatSize(total)}）")
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                throw IllegalStateException("下载失败：无法保存安装包")
            }
            onProgress(Progress(written, if (total > 0) total else written))
            dest
        }
    }
}
