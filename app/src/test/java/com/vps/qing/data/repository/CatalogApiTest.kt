package com.vps.qing.data.repository

import com.google.gson.Gson
import com.vps.qing.core.network.ApiResult
import com.vps.qing.data.api.QingVpsApiService
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** 用 MockWebServer 校验新增端点的 Retrofit 注解与解析链路（不依赖真实网络）。 */
class CatalogApiTest {

    private fun withServer(block: (MockWebServer, CatalogRepository) -> Unit) {
        val server = MockWebServer()
        server.start()
        try {
            val api = Retrofit.Builder()
                .baseUrl(server.url("/console/v1/"))
                .addConverterFactory(GsonConverterFactory.create(Gson()))
                .build()
                .create(QingVpsApiService::class.java)
            block(server, CatalogRepository(api))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `products request carries fpg_id and spg_id and maps result`() = withServer { server, repo ->
        server.enqueue(
            MockResponse().setBody(
                """{"status":200,"msg":"请求成功","data":{"list":[
                   {"id":45,"name":"APAC | 亚太-体验版","price":"1.00","cycle":"月付",
                    "description":"<div class=\"pd-br\">月流量：50GiB</div>"}],"count":"20"}}"""
            )
        )
        val result = runBlocking { repo.products("1", "4") }
        assertTrue(result is ApiResult.Success)
        val list = (result as ApiResult.Success).data
        assertEquals(1, list.size)
        assertEquals("45", list[0].id)
        assertTrue(list[0].specs.contains("月流量：50GiB"))

        val path = server.takeRequest().path.orEmpty()
        assertTrue(path.startsWith("/console/v1/product?"))
        assertTrue(path.contains("fpg_id=1"))
        assertTrue(path.contains("spg_id=4"))
    }

    @Test
    fun `secondGroups request carries parent id`() = withServer { server, repo ->
        server.enqueue(
            MockResponse().setBody(
                """{"status":200,"msg":"请求成功","data":{"list":[
                   {"id":"4","name":"✅代理加盟","parent_id":"1"}],"count":"6"}}"""
            )
        )
        val result = runBlocking { repo.secondGroups("1") }
        assertTrue(result is ApiResult.Success)
        assertEquals("4", (result as ApiResult.Success).data[0].id)
        assertTrue(server.takeRequest().path.orEmpty().contains("id=1"))
    }

    @Test
    fun `business failure is surfaced instead of empty list`() = withServer { server, repo ->
        server.enqueue(MockResponse().setBody("""{"status":401,"msg":"当前未登录，请先登录后使用"}"""))
        val result = runBlocking { repo.hosts() }
        assertTrue(result is ApiResult.Failure)
        assertEquals(401, (result as ApiResult.Failure).code)
    }
}
