package com.vps.qing.core.network

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class NetworkTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun client(vararg interceptors: okhttp3.Interceptor): OkHttpClient =
        OkHttpClient.Builder().apply {
            interceptors.forEach { addInterceptor(it) }
            callTimeout(5, TimeUnit.SECONDS)
        }.build()

    @Test
    fun `auth interceptor injects bearer and language headers`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":200,"msg":"ok","data":{}}""")
        )
        val c = client(AuthInterceptor(TokenProvider { "jwt-abcdefghijklmnop" }))
        c.newCall(Request.Builder().url(server.url("/console/v1/common")).build()).execute().use {
            assertEquals(200, it.code)
        }
        val recorded = server.takeRequest()
        assertEquals("Bearer jwt-abcdefghijklmnop", recorded.getHeader("Authorization"))
        assertEquals("zh-cn", recorded.getHeader("language"))
        assertEquals("application/json", recorded.getHeader("Accept"))
    }

    @Test
    fun `no token means no authorization header`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"status":200,"data":{}}""")
        )
        val c = client(AuthInterceptor(TokenProvider { null }))
        c.newCall(Request.Builder().url(server.url("/common")).build()).execute().close()
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `business 401 is detected by safeApiCall and fires unauthorized bus`() = runTest {
        var fired = 0
        val previous = UnauthorizedBus.onUnauthorized
        UnauthorizedBus.onUnauthorized = { fired++ }
        try {
            val result = safeApiCall(
                { ApiEnvelope(401, "当前未登录，请先登录后使用", null) }
            ) { Unit }
            assertTrue(result is ApiResult.Failure)
            assertEquals(401, (result as ApiResult.Failure).code)
            assertEquals(1, fired)
        } finally {
            UnauthorizedBus.onUnauthorized = previous
        }
    }

    @Test
    fun `content type guard rejects html fallback`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<!DOCTYPE html><html><body>Not Found</body></html>")
        )
        val c = client(ContentTypeGuardInterceptor())
        val result = runCatching {
            c.newCall(Request.Builder().url(server.url("/login")).build()).execute()
        }
        assertTrue(result.exceptionOrNull() is HtmlFallbackException)
    }

    @Test
    fun `content type guard allows json`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"status":200,"data":{}}""")
        )
        val c = client(ContentTypeGuardInterceptor())
        c.newCall(Request.Builder().url(server.url("/common")).build()).execute().use {
            assertEquals(200, it.code)
        }
    }

    @Test
    fun `token extractor reads data jwt`() {
        val data = JsonParser.parseString("""{"jwt":"eyJhbGciOiJIUzI1NiJ9.longtokenvalue123456"}""")
        assertEquals(
            "eyJhbGciOiJIUzI1NiJ9.longtokenvalue123456",
            TokenExtractor.extract(data)
        )
    }

    @Test
    fun `token extractor strips bearer and searches nested data`() {
        val nested = JsonParser.parseString(
            """{"data":{"jwt":"Bearer eyJhbGciOiJIUzI1NiJ9.anotherlongtoken"}}"""
        )
        assertEquals(
            "eyJhbGciOiJIUzI1NiJ9.anotherlongtoken",
            TokenExtractor.extract(nested)
        )
    }

    @Test
    fun `token extractor returns null for missing or short values`() {
        assertNull(TokenExtractor.extract(null))
        assertNull(TokenExtractor.extract(JsonParser.parseString("""{"data":{}}""")))
        assertNull(TokenExtractor.extract(JsonParser.parseString("""{"jwt":"short"}""")))
    }

    @Test
    fun `safeApiCall maps business status and exceptions`() = runTest {
        val ok = safeApiCall({ ApiEnvelope(200, "ok", JsonParser.parseString("""{"a":"1"}""")) }) { it }
        assertTrue(ok is ApiResult.Success)
        assertEquals("1", (ok as ApiResult.Success).data?.string("a"))

        val fail = safeApiCall({ ApiEnvelope(400, "邮箱未注册", null) }) { Unit }
        assertTrue(fail is ApiResult.Failure)
        assertEquals(400, (fail as ApiResult.Failure).code)
        assertEquals("邮箱未注册", fail.msg)

        val error = safeApiCall<Unit>({ throw HtmlFallbackException("html") }) { Unit }
        assertTrue(error is ApiResult.Error)
        assertTrue((error as ApiResult.Error).throwable is HtmlFallbackException)
    }

    @Test
    fun `envelope helpers are lenient`() {
        val data = JsonParser.parseString(
            """{"list":[{"id":1}],"nested":{"x":2.5},"nullv":null}"""
        )
        assertEquals(1, data.arrayAt("list")?.size())
        assertEquals(2.5, data.objectAt("nested")?.get("x")?.asDouble!!, 0.0001)
        assertNull(data.string("nullv"))
        assertNull(data.string("missing"))
        assertEquals(0, data.arrayAt("nested")?.size() ?: 0)
    }
}
