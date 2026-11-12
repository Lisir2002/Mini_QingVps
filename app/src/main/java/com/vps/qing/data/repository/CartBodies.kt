package com.vps.qing.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * 真实加购 / 算价请求体构造（纯 JVM，便于单测）。
 *
 * 协议来自站点 common_product.js 的 addCart() / calcPrice() 实测：
 * POST /console/v1/cart （匿名 cart_cookie 会话即可，无需登录态）
 * {
 *   "product_id": 45,
 *   "config_options": {
 *     "configoption": {"12568": 45697},     // 选项 id -> 选中 sub id（quantity 类型是 [n]）
 *     "cascade_configoption": {},
 *     "cycle": 3706                          // custom_cycles[].id，不是「月付」字符串
 *   },
 *   "qty": 1,
 *   "customfield": {},
 *   "self_defined_field": {}
 * }
 * 算价 POST /product/{id}/config_option 用同样的 body。
 */
object CartBodies {

    /** 一个配置项的选中值。[array] 为 true 时站点要求包成单元素数组（quantity / multi_select）。 */
    data class Selection(
        val optionId: String,
        val value: String,
        val array: Boolean = false
    )

    fun build(
        productId: String,
        selections: List<Selection>,
        cycleId: String?,
        qty: Int
    ): JsonObject {
        val configoption = JsonObject()
        selections.forEach { sel ->
            val id = sel.value.toLongOrNull()
            if (sel.array) {
                val arr = JsonArray()
                if (id != null) arr.add(id) else arr.add(sel.value)
                configoption.add(sel.optionId, arr)
            } else {
                if (id != null) configoption.addProperty(sel.optionId, id)
                else configoption.addProperty(sel.optionId, sel.value)
            }
        }

        val configOptions = JsonObject().apply {
            add("configoption", configoption)
            add("cascade_configoption", JsonObject())
            cycleId?.let {
                val cid = it.toLongOrNull()
                if (cid != null) addProperty("cycle", cid) else addProperty("cycle", it)
            }
        }

        return JsonObject().apply {
            val pid = productId.toLongOrNull()
            if (pid != null) addProperty("product_id", pid) else addProperty("product_id", productId)
            add("config_options", configOptions)
            addProperty("qty", qty.coerceAtLeast(1))
            add("customfield", JsonObject())
            add("self_defined_field", JsonObject())
        }
    }

    /** PUT /cart/{position}/qty 的 body：{qty: n}。 */
    fun qty(value: Int): JsonObject = JsonObject().apply {
        addProperty("qty", value.coerceAtLeast(1))
    }
}
