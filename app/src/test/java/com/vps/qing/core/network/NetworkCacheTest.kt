package com.vps.qing.core.network

import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * v1.4.4 网络层缓存体系回归测试。
 * 站点零缓存头、条件请求无效，因此缓存正确性完全由客户端策略保证。
 */
class NetworkCacheTest {

    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        cacheDir = Files.createTempDirectory("qv-cache-test").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    private fun okBody(text: String = "[]"): String =
        """{"status":200,"msg":"ok","data":$text}"""

    private fun json(body: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(body)

    private fun buildClient(
        cache: ApiCache = ApiCache(),
        withDisk: Boolean = true
    ): OkHttpClient = OkHttpClient.Builder().apply {
        if (withDisk) cache(Cache(cacheDir, 2L * 1024 * 1024))
        addInterceptor(IdempotentRetryInterceptor())
        addInterceptor(AuthInterceptor(TokenProvider { "test-token" }))
        addInterceptor(ApiCacheInterceptor(cache, sessionKey = { "s1" }))
        addInterceptor(ContentTypeGuardInterceptor())
        addNetworkInterceptor(HttpCacheResponseInterceptor())
    }.build()

    // ---------------------------------------------------------------- CachePolicy

    @Test
    fun `公共只读接口按语义分级 TTL`() {
        val product = CachePolicy.of("GET", "/console/v1/product")
        assertEquals(CacheScope.PUBLIC, product.scope)
        assertEquals(CachePolicy.TTL_CATALOG, product.ttlMs)
        assertEquals(60, product.seconds)

        // 分类变动极少，用字典级 TTL；商品列表保持 60 秒以保证上新可见
        assertEquals(CacheScope.PUBLIC, CachePolicy.of("GET", "/console/v1/product/group/first").scope)
        assertEquals(
            CachePolicy.TTL_DICT,
            CachePolicy.of("GET", "/console/v1/product/group/first").ttlMs
        )
        assertEquals(CachePolicy.TTL_NEWS, CachePolicy.of("GET", "/console/v1/news/index").ttlMs)
        assertEquals(CachePolicy.TTL_DICT, CachePolicy.of("GET", "/console/v1/common").ttlMs)
        assertEquals(CachePolicy.TTL_DETAIL, CachePolicy.of("GET", "/console/v1/product/28").ttlMs)
    }

    @Test
    fun `私有数据只进内存且统计类 TTL 更短`() {
        assertEquals(CacheScope.PRIVATE, CachePolicy.of("GET", "/console/v1/account").scope)
        assertEquals(CacheScope.PRIVATE, CachePolicy.of("GET", "/console/v1/ticket").scope)
        assertEquals(CachePolicy.TTL_DICT, CachePolicy.of("GET", "/console/v1/ticket/type").ttlMs)
        assertEquals(CachePolicy.TTL_STAT, CachePolicy.of("GET", "/console/v1/ticket/statistic").ttlMs)
        assertEquals(CachePolicy.TTL_DETAIL, CachePolicy.of("GET", "/console/v1/host/4").ttlMs)
    }

    @Test
    fun `写操作算价验证码与未知路径一律不缓存`() {
        assertFalse(CachePolicy.of("POST", "/console/v1/cart").cacheable)
        assertFalse(CachePolicy.of("PUT", "/console/v1/ticket/1/close").cacheable)
        assertFalse(CachePolicy.of("DELETE", "/console/v1/sub_account/1").cacheable)
        assertFalse(CachePolicy.of("GET", "/console/v1/product/28/config_option").cacheable)
        assertFalse(CachePolicy.of("GET", "/console/v1/captcha").cacheable)
        assertFalse(CachePolicy.of("GET", "/some/unknown/path").cacheable)
        assertEquals(CacheScope.NONE, CachePolicy.of("GET", "/console/v1/product/28/config_option").scope)
    }

    @Test
    fun `静态资源识别与失效前缀`() {
        assertTrue(CachePolicy.isStaticAsset("/clientarea/template/pc/default/js/app.js"))
        assertTrue(CachePolicy.isStaticAsset("/plugins/addon/x/style.css?v=3"))
        assertTrue(CachePolicy.isStaticAsset("/img/logo.png"))
        assertFalse(CachePolicy.isStaticAsset("/console/v1/product"))
        assertFalse(CachePolicy.isStaticAsset("/cart/goodsList.htm"))

        assertEquals("product", CachePolicy.relativePath("/console/v1/product"))
        assertNull(CachePolicy.relativePath("/captcha/tp_captcha/index/refresh"))
        assertEquals(listOf("cart"), CachePolicy.invalidationPrefixes("POST", "/console/v1/cart"))
        assertEquals(listOf("ticket"), CachePolicy.invalidationPrefixes("POST", "/console/v1/ticket"))
        assertEquals(listOf(""), CachePolicy.invalidationPrefixes("POST", "/console/v1/login"))
        assertTrue(CachePolicy.invalidationPrefixes("GET", "/console/v1/product").isEmpty())
    }

    // ---------------------------------------------------------------- ApiCache (L1)

    @Test
    fun `TTL 过期后可作 stale 兜底`() {
        var now = 0L
        val cache = ApiCache(clock = { now })
        val key = cache.key("GET", "http://x/console/v1/product", "s1", "product")
        val rule = CacheRule(CacheScope.PUBLIC, 60_000L)
        cache.put(key, "product", okBody(), rule)

        assertEquals(okBody(), cache.fresh(key))
        now = 60_001L
        assertNull(cache.fresh(key))
        assertEquals(okBody(), cache.stale(key))
    }

    @Test
    fun `业务失败响应不写入缓存`() {
        val cache = ApiCache()
        val key = cache.key("GET", "http://x/console/v1/account", "s1", "account")
        cache.put(key, "account", """{"status":401,"msg":"未登录"}""", CacheRule(CacheScope.PRIVATE, 60_000L))
        assertNull(cache.fresh(key))
        assertEquals(0, cache.size())
        assertEquals(401, ApiCache.businessStatus("""{"status":401,"msg":"未登录"}"""))
        assertEquals(200, ApiCache.businessStatus("""{ "status" : 200, "data":[] }"""))
        assertNull(ApiCache.businessStatus("""{"data":[]}"""))
    }

    @Test
    fun `LRU 淘汰与按前缀失效`() {
        val cache = ApiCache(maxEntries = 2)
        val rule = CacheRule(CacheScope.PUBLIC, 60_000L)
        val k1 = cache.key("GET", "u1", "s1", "product")
        val k2 = cache.key("GET", "u2", "s1", "product/group/first")
        val k3 = cache.key("GET", "u3", "s1", "cart")
        cache.put(k1, "product", okBody(), rule)
        cache.put(k2, "product/group/first", okBody(), rule)
        // 访问 k1 让它变成最近使用，k2 成为最久未用
        cache.fresh(k1)
        cache.put(k3, "cart", okBody(), rule)
        assertEquals(2, cache.size())
        assertNull(cache.fresh(k2))

        cache.invalidate("product")
        assertNull(cache.fresh(k1))
        assertEquals(okBody(), cache.fresh(k3))

        cache.invalidate("")
        assertEquals(0, cache.size())
    }

    @Test
    fun `会话指纹不同则 key 不同`() {
        val cache = ApiCache()
        val a = cache.key("GET", "http://x/console/v1/account", "userA", "account")
        val b = cache.key("GET", "http://x/console/v1/account", "userB", "account")
        assertNotEquals(a, b)
    }

    @Test
    fun `in-flight 去重只放行一个请求`() = kotlinx.coroutines.test.runTest {
        val cache = ApiCache()
        val key = cache.key("GET", "http://x/console/v1/product", "s1", "product")
        val (d1, joined1) = cache.joinOrCreate(key)
        val (d2, joined2) = cache.joinOrCreate(key)
        assertFalse(joined1)
        assertTrue(joined2)
        assertEquals(1, cache.pendingCount())
        cache.succeed(key, d1, "SHARED")
        assertEquals("SHARED", d2.await())
        assertEquals(0, cache.pendingCount())
    }

    // ---------------------------------------------------------------- 拦截器集成

    @Test
    fun `L1 命中第二次完全不发网络`() {
        server.enqueue(json(okBody()))
        val client = buildClient()
        val req = Request.Builder().url(server.url("/console/v1/product")).build()

        client.newCall(req).execute().use { assertEquals(200, it.code) }
        client.newCall(req).execute().use {
            assertEquals("L1-memory", it.header("X-QingVPS-Cache"))
            assertEquals(okBody(), it.body?.string())
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `清空 L1 后 L2 磁盘缓存仍可命中`() {
        server.enqueue(json(okBody()))
        val cache = ApiCache()
        val client = buildClient(cache)
        val req = Request.Builder().url(server.url("/console/v1/product")).build()

        client.newCall(req).execute().use { assertEquals(200, it.code) }
        cache.clear() // 只清内存，磁盘缓存保留
        client.newCall(req).execute().use { assertEquals(200, it.code) }
        assertEquals("仅发起一次网络请求，第二次由磁盘缓存命中", 1, server.requestCount)
    }

    @Test
    fun `私有数据不落磁盘缓存`() {
        server.enqueue(json(okBody()))
        server.enqueue(json(okBody()))
        val cache = ApiCache()
        val client = buildClient(cache)
        val req = Request.Builder().url(server.url("/console/v1/account")).build()

        client.newCall(req).execute().close()
        cache.clear()
        client.newCall(req).execute().close()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `业务 401 响应不会被缓存`() {
        server.enqueue(json("""{"status":401,"msg":"未登录"}"""))
        server.enqueue(json("""{"status":401,"msg":"未登录"}"""))
        val client = buildClient()
        val req = Request.Builder().url(server.url("/console/v1/account")).build()
        client.newCall(req).execute().close()
        client.newCall(req).execute().close()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `并发同一 URL 只放一次网络`() {
        server.enqueue(json(okBody()).setBodyDelay(250, TimeUnit.MILLISECONDS))
        val client = buildClient()
        val url = server.url("/console/v1/product")
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val codes = AtomicInteger(0)

        repeat(2) {
            thread {
                start.await()
                try {
                    client.newCall(Request.Builder().url(url).build()).execute().use {
                        if (it.code == 200) codes.incrementAndGet()
                    }
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(2, codes.get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `写操作成功后失效对应前缀缓存`() {
        server.enqueue(json(okBody())) // GET product
        server.enqueue(json(okBody())) // GET cart
        server.enqueue(json(okBody())) // POST cart
        server.enqueue(json(okBody())) // GET cart again
        val cache = ApiCache()
        val client = buildClient(cache)
        val product = Request.Builder().url(server.url("/console/v1/product")).build()
        val cart = Request.Builder().url(server.url("/console/v1/cart")).build()

        client.newCall(product).execute().close()
        client.newCall(cart).execute().close()
        val post = Request.Builder().url(server.url("/console/v1/cart"))
            .post("{}".toRequestBody("application/json".toMediaType())).build()
        client.newCall(post).execute().close()
        client.newCall(cart).execute().close()

        // POST /cart 只失效 cart 前缀，product 缓存仍新鲜，不会新增网络请求
        client.newCall(product).execute().use {
            assertEquals("L1-memory", it.header("X-QingVPS-Cache"))
        }
        assertEquals(4, server.requestCount)
    }
}
