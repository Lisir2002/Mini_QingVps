# 清婉云 `/console/v1` 接口施工图

> 调研时间：2026-11-12 ｜ 目标站点：`https://vps.qingyayun.cn` ｜ 系统：IDCSmart **10.7.2**（清婉云自定义模板 `wry`）
>
> **获取方式**：全部接口定义从站点自身公开的前端 JS 中提取（模板 `api/*.js`），并对免登录的 GET 端点做了真实请求验证。**未执行任何写操作、未尝试登录或绕过鉴权**，所以 POST/PUT/DELETE 的请求体字段只有调用处线索、没有实测响应，文末已明确列出待确认项。
>
> 机器可读版本见同目录 `qingwanyun_endpoints.json`（192 个端点，含方法与来源文件）。

---

## 1. 结论速览

| 项 | 结论 |
|---|---|
| 基址 | `https://vps.qingyayun.cn/console/v1` |
| 风格 | RESTful + JSON，前后端分离；页面 `.htm` 只是壳 |
| 接口总数 | **192 个唯一端点**（从 15 个 JS 文件提取去重） |
| 鉴权 | JWT `Authorization: Bearer <jwt>`，登录 `POST /login` 获取 |
| 免登录可直读 | `/common` `/menu` `/country` `/captcha` `/gateway` `/product*` `/help/{id}` `/announcement` `/news` `/cart`（空车） |
| 必须登录 | `/index` `/global_search` `/account` `/auth` `/order` `/host*` `/home/host` `/pay/{id}/status` `/withdraw*` 等 |

**对技术选型的修正（重要）**：上一轮建议「A 原生直连为主、B WebView 兜底长尾页」。实测后发现 **订购配置页必须走 B**——`GET /product/{id}/config_option` 返回的不是结构化配置项，而是一整段 **Vue 模板 HTML**（官方前端用 `$(".config-box").html(res.data.data.content)` 直接塞进 DOM）。原生 App 无法可靠还原配置表单。

站点恰好留了官方后门：`goods_iframe.htm` + `iframe_token`（`request.js` 里 `isGoodsIframe` 分支）。所以第一版合理形态是：

> **商品列表 / 详情 / 购物车 / 订单用原生；「选配置 → 询价 → 加购」这一段用 WebView 承载站点自带的 iframe 订购页，通过 `iframe_token` 注入 JWT。**

---

## 2. 通用约定（来自 `utils/request.js`）

**请求头**

```
Authorization: Bearer <jwt>      // 登录后必带
language: zh-cn                  // 未登录时由前端带，App 里建议固定带
Accept: application/json
```

`Axios.create({ baseURL: "/console/v1", timeout: 10min })`，`withCredentials: true`；加购接口单独放宽到 20 分钟。

**统一响应包装**（HTTP 状态码几乎恒为 200，业务状态在 body 里）

```json
{ "status": 200, "msg": "请求成功", "data": { ... } }
```

| status | 含义 / 前端行为 |
|---|---|
| 200 | 成功 |
| 302 | 跳 `/console/v1/install`（系统未安装） |
| 307 | 无动作 |
| 400 | 业务校验失败；`data` 可能含 `client_operate_methods` / `operate_password`（需要操作密码） |
| **401** | 未登录或 2 小时无操作过期 → 前端清 jwt 并跳登录页（白名单页面除外） |
| 403 / 404 | 无权限 / 不存在 |
| 405 | 跳登录 |
| 409 | 该管理员下无此客户，跳首页 |
| 410 / 422 / 500 | 失败提示 |

**会话与后门**

- 登录响应里的 jwt 由前端存 `localStorage`（不是 Cookie），App 里应存 Keystore / EncryptedSharedPreferences。
- 响应头另有 `sl-session=...; HttpOnly; SameSite=None; Secure; Max-Age=86400`，OkHttp 的 CookieJar 要保留。
- `?queryParam=<jwt>`：任何页面 URL 带此参数，`request.js` 会自动写入本地存储并移除该参数 → **WebView 同步登录态的现成通道**。
- `goods_iframe.htm?iframe_token=<jwt>`：iframe 专用 token 注入，用于嵌入订购页。
- 不需要 jwt 的页面白名单：`login.htm` `userStatus.htm` `goodsList.htm` `source.htm` `news_detail.htm` `helpTotal.htm` `goods.htm` `goods_iframe.htm` `agreement.htm` `regist.htm` `forget.htm` `oauth.htm` `404.htm`（注意这是**页面级**白名单，不等于接口免鉴权，实测以第 1 节为准）。

---

## 3. 第一版核心链路（建议先实现这 10 个）

| # | 步骤 | 方法 & 路径 | 主要参数 | 鉴权 | 匿名实测 |
|---|---|---|---|---|---|
| 1 | 站点配置 | `GET /common` | — | 否 | ✅ 200 |
| 2 | 一级分组 | `GET /product/group/first` | — | 否 | ✅ 200 |
| 3 | 二级分组 | `GET /product/group/second?id={fid}` | `id` | 否 | ✅ 200 |
| 4 | 商品列表 | `GET /product` | `fpg_id`, `spg_id`, `page`, `limit`, `id`(二级分组) | 否 | ✅ 200，20 条 |
| 5 | 商品详情 | `GET /product/{id}` | — | 否 | ✅ 200 |
| 6 | 自定义字段 | `GET /product/{id}/self_defined_field/order_page` | — | 否 | ✅ 200（`data.data: []`） |
| 7 | **订购配置** | `GET /product/{id}/config_option` | `id`, `flag` | 否 | ⚠️ 200，返回 **Vue HTML 模板** |
| 8 | 询价 | `POST /product/{id}/config_option` | `qty`, `config_options{...}` 等 | 否 | ⚠️ 空参数返回 `400 请传入数量` |
| 9 | 加入购物车 | `POST /cart` | `{product_id, qty, config_options, customfield}` 或 `{products:[...]}` | 是 | 未测（写操作） |
| 10 | 购物车列表 | `GET /cart` | — | 是 | ✅ 200（空车 `list: []`） |

后续链路：`POST /cart/settle` 结算 → `GET /gateway` 取支付渠道 → `POST /pay` 下单 → `GET /pay/{id}/status` 轮询结果 → `GET /order/{id}` 订单详情。

**登录链路**：`GET /captcha`（返回 HTML 片段，滑动/图形验证码）→ `POST /login`（`email`/`phone` + `password` 或验证码，按 `/common` 开关决定）→ 保存 jwt → `POST /logout`。

`/common` 里的关键开关（实测值）：`captcha_client_login=0`（登录免图形码）、`captcha_client_register=1`（注册要）、`login_phone_verify=0`、`register_email=1`、`register_phone=0`、`currency_code=CNY`、`currency_prefix=￥`、`recharge_open=1`。

---

## 4. 关键响应结构

### 商品列表 `GET /product`（`data.list[]`，`count` 为总数）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int | 商品 ID，后续所有接口的 `{id}` |
| `name` | string | 名称，如 `APAC \| 亚太-体验版` |
| `price` / `cycle` | string | `"1.00"` / `月付` |
| `pay_type` | string | `recurring_prepayment`（预付费循环）、`on_demand` 等 |
| `pay_ontrial` | array | 试用配置 |
| `product_group_id_first` / `_second` | int | 一级 / 二级分组 ID |
| `product_group_name_first` / `_second` | string | 分组名 |
| `qty` / `stock_control` | int | 库存 / 是否限购 |
| `module` / `module1` / `res_module` | string\|null | 模块标识，如 `idcsmart_common` |
| `mode` | string | `only_api` 等 |
| `recommend` / `recommend_text` / `recommend_color` | — | 推荐标 |
| `description` | string | **整页 HTML**（含嵌套 `<!DOCTYPE html>`），需清洗后渲染 |

> 实测 `fpg_id=1&spg_id=4` 返回 20 条，但 `product_group_id_second` 实际是 10、一级是 9 —— **页面 URL 参数与商品分组 ID 不是一套编号**，别直接拿 `spg_id` 当分组 ID 传给 `group/second`。

### 商品详情 `GET /product/{id}`

`data.product`：`id, name, pay_type, price, cycle, product_group_id, product_group_id_first, plugin_custom_fields[], show, module, customfield[], mode, supplier_id, supplier_name, profit_type, profit_percent`。

### 分组

- `GET /product/group/first` → `{list:[{id,name,type}], count}`（实测 2 个一级分组，其中 `id=1 🎉活动 | 2026特惠区`）
- `GET /product/group/second?id=1` → 6 个二级分组（`{id,name,parent_id,type,description}`）
- `GET /product/group/second?id=9` → 1 个（`id=10 QY▪CDN加速|高防`）

### 支付渠道 `GET /gateway`

实测仅 1 个：`{id:36, name:"EpayAli", title:"支付宝"}`；`url` 字段是 **base64 内联 PNG 图标**（很长，需忽略或单独缓存）。**没有微信支付渠道** → 支付集成只需考虑支付宝，工作量比预想小。

### 其他

- `GET /captcha` → `data.html` 是一段带 `<style>` 的 HTML 片段（验证码组件），App 里得用 WebView 承载，不能当图片。
- `GET /announcement` / `/news` → `{list:[{id,title,img,create_time(秒级时间戳)}], count}`。
- `GET /help/{id}` → `{help:{id,title,keywords,attachment[],create_time,update_time,prev{},next{}}}`。
- `GET /cart` 匿名返回 `{"list":[]}`，不报 401 —— 购物车匿名可读但为空，别用「是否 200」判断登录态，用 `/account` 或 `/index`（返回 `401 当前未登录，请先登录后使用`）。

---

## 5. 六个坑（写码前先看）

1. **配置页是 HTML 模板，不是 JSON。** `GET /product/{id}/config_option` 的 `data.content` 是含 `{{}}` 插值、`v-for`、`el-select` 的 Vue 模板片段，官方页面直接 `.html()` 注入。带 `?cycle=month` 时只返回 `product_name`，说明内容按参数动态生成。**原生无法稳定还原** → 用 WebView 加载 `goods_iframe.htm?id={id}&iframe_token={jwt}`，站点自己会渲染并调 `POST /product/{id}/config_option` 询价、`POST /cart` 加购；App 只需拦截跳转/回调。
2. **业务错误在 body，HTTP 恒 200。** Retrofit 不能只靠 `isSuccessful`，必须先解析 `status`；401 要全局拦截（清 token → 跳登录），400 要读 `data.client_operate_methods` 判断是否要操作密码二次验证。
3. **部分端点会回退成 HTML 首页。** 实测 `GET /login`、`/client_level`、`/presale_consult/config`、`/online_service`、`/credit_limit`、`/logout` 返回的是完整 HTML 页面（插件未启用 / 方法不匹配 / 路由回退）。**必须按 `Content-Type` 判断**，否则 JSON 解析会崩。写操作不要照抄 GET 探测结果。
4. **`description` 字段是脏 HTML**（嵌套 DOCTYPE、内联样式），列表卡片取摘要要剥离标签；详情页用 WebView 或 `HtmlCompat` + 白名单清洗，注意 XSS。
5. **私有接口、无公开文档、随版本变。** `system_version=10.7.2`，站点一升级路径/字段可能改。所以 Repository 层要集中定义常量 + 数据类，并配一份契约测试（拿本文第 3 节的 10 个匿名接口做冒烟）。
6. **风控提示。** 站点页面明确写着「不要用 VPN 访问本站」，且 `sl-session` Cookie `SameSite=None; Secure`。App 要走正常网络、保留 CookieJar，失败时给用户清晰提示，别静默重试。

---

## 6. 完整接口清单（192 个，按业务分组）

> `GET /api`、`POST /api` 等带 `{module}` / `{param}` 的是插件动态路由，来自 `common.js` 的模板字符串；`{id}` / `{pos}` 为路径变量归一化结果。标注「定义」的来自 JS 源码，未做请求验证。

### 商品与分组 (12)

```
GET    /client_level/product/{id}/amount
GET    /event_promotion/product/{id}/event_promotion
GET    /menu/{id}/host
GET    /product
GET    /product/group/first
GET    /product/group/second
GET    /product/{id}
GET    /product/{id}/config_option
GET    /product/{id}/self_defined_field/order_page
POST   /event_promotion/apply
POST   /product/settle
POST   /product/{id}/config_option
```

### 购物车与结算 (7)

```
GET    /cart
POST   /cart
POST   /cart/settle
PUT    /cart/{pos}
PUT    /cart/{pos}/qty
DELETE /cart/batch
DELETE /cart/{pos}
```

### 登录 / 注册 / 验证码 / 账号 (36)

```
GET    /account                              GET    /oauth/token
GET    /account/credit/freeze                GET    /oauth/{name}
GET    /account/traffic_warning             GET    /register/custom_field
GET    /authreset/certificate               POST   /account/credit/remind
GET    /authreset/certificate/status        POST   /account/password_reset
GET    /captcha                             POST   /authreset/applications
GET    /client_custom_field_value           POST   /authreset/reset/cancel
GET    /log                                 POST   /authreset/reset/sure
GET    /login                               POST   /captcha
GET    /login/exception/certification/status  POST  /email/code
POST   /login                               POST   /phone/code
POST   /login/exception/certification/create POST  /register
POST   /logout                              POST   /oauth/client/bind
POST   /oauth/unbind/{name}                 PUT    /account
PUT    /account/email                       PUT    /account/phone
PUT    /account/email/old                   PUT    /account/phone/old
PUT    /account/operate_password            PUT    /account/traffic_warning
PUT    /account/password                    PUT    /language
```

### 订单 / 支付 / 财务 (45)

```
GET    /coin/client/coupon                  GET    /voucher/mine
GET    /coin/coupon                        GET    /voucher/pay
GET    /coin/coupon/{id}/use_detail        GET    /withdraw
GET    /coin/pay                           GET    /withdraw/rule/credit
GET    /coin/recharge                      POST   /coin/pay
GET    /coin/rules                         POST   /coin/{id}/get
GET    /coin/wait_get                      POST   /credit
GET    /credit                             POST   /credit_limit/pay
GET    /credit_limit                       POST   /credit_limit/prepayment
GET    /credit_limit/account                POST  /order/combine
GET    /credit_limit/account/{id}/order    POST   /order/{id}/submit_application
GET    /export_excel/order                 POST   /pay
GET    /gateway                            POST   /promo_code/apply
GET    /order                              POST   /promo_code/apply_batch
GET    /order/{id}                         POST   /recharge
GET    /pay/{id}/status                    POST   /refund
GET    /refund                             POST   /voucher/pay
GET    /refund/host/{id}/refund            POST   /voucher/{id}/get
GET    /refund/pending/amount              POST   /withdraw
GET    /transaction                        PUT    /order/{id}/gateway
GET    /voucher                            PUT    /order/{id}/voucher
PUT    /refund/{id}/cancel                 DELETE /order
                                           DELETE /order/{id}
```

### 主机与服务管理 (64)

```
GET    /aodun_firewall/host_ip             GET    /idcsmart_domain/info_template/{id}
GET    /aodun_firewall/host_ip/{id}/refresh_status   GET /idcsmart_domain/whois
GET    /aodun_firewall_agent/host_ip       GET    /idcsmart_ssl/brand
GET    /aodun_firewall_agent/host_ip/{id}/refresh_status  GET /idcsmart_ssl/product
GET    /certification/auth                 GET    /index/host
GET    /certification/custom_fields        GET    /ssh_key
GET    /certification/info                 GET    /sub_account/{id}/auth
GET    /certification/plugin               POST   /certification/company
GET    /client/host                        POST   /certification/person
GET    /client_care/mail                   POST   /e_contract/{id}/cancel
GET    /client_care/mail/list              POST   /e_contract/{id}/download
GET    /e_contract                         POST   /e_contract/{id}/mail
GET    /e_contract/first_part_info         POST   /host/renew/batch
GET    /e_contract/host/{id}/timeout       POST   /host/{id}/flow_packet_order
GET    /e_contract/order                   POST   /host/{id}/on_demand_to_recurring_prepayment
GET    /e_contract/{id}/preview            POST   /host/{id}/product_cashback
GET    /home/host                          POST   /host/{id}/renew
GET    /host/renew/batch                   POST   /idcsmart_domain/info_template
GET    /host/{id}                          POST   /idcsmart_domain/info_template/{id}/certifications
GET    /host/{id}/flow_packet              POST   /ssh_key
GET    /host/{id}/ip                       PUT    /api/{id}/white_list
GET    /host/{id}/on_demand_to_recurring_prepayment  PUT /client_care/mail/read
GET    /host/{id}/product_cashback         PUT    /e_contract/first_part_info
GET    /host/{id}/renew                    PUT    /host/notes/batch
GET    /host/{id}/specific_info            PUT    /host/{id}/renew/auto
GET    /host/{id}/view                     PUT    /ssh_key/{id}
GET    /host_transfer/host/{id}/is_transfer  DELETE /api/{id}
GET    /idcsmart_domain/bulk_check         DELETE /client_care/mail
GET    /idcsmart_domain/check_domain       DELETE /idcsmart_domain/info_template/{id}
GET    /idcsmart_domain/config             DELETE /ssh_key/{id}
GET    /idcsmart_domain/domain_suffix
GET    /idcsmart_domain/get_price
GET    /idcsmart_domain/info_template
GET    /idcsmart_domain/info_template/support
```

### 内容 / 帮助 / 其他 (28)

```
GET    /api                                GET    /online_service
GET    /auth                               GET    /presale_consult/config
GET    /client_level                       GET    /recommend/promoter
GET    /common                             GET    /referral/promoter/statistic
GET    /country                            GET    /ticket
GET    /global_search                      GET    /{module}/{id}/traffic_package
GET    /help/{id}                          GET    /{module}/{id}/upgrade_defence/price
GET    /index                              GET    /{module}/{id}/upgrade_defence_config
GET    /menu                               POST   /api
GET    /mp_weixin_notice/client            POST   /mp_weixin_notice/qrcode/bind_client
GET    /mp_weixin_notice/qrcode            POST   /mp_weixin_notice/qrcode/select_client_login
GET    /mp_weixin_notice/qrcode/status     POST   /{module}/batch_operate
GET    /news/index                         POST   /{module}/{id}/upgrade_defence/order
PUT    /aliyun_agency/account/email        PUT    /mp_weixin_notice/accept_push
```

> 另有匿名实测存在但未出现在 api JS 定义里的端点：`GET /announcement`、`GET /news`、`GET /help/{id}`。说明站点还有服务端/插件提供的路由，清单不是绝对全集。

---

## 7. Retrofit 骨架（可直接照着写）

```kotlin
// 统一响应
@Serializable
data class ApiResp<T>(val status: Int, val msg: String, val data: T? = null)

interface QingwanApi {
    @GET("common") suspend fun common(): ApiResp<CommonConfig>
    @GET("product/group/first") suspend fun groupFirst(): ApiResp<GroupList>
    @GET("product/group/second") suspend fun groupSecond(@Query("id") id: Int): ApiResp<GroupList>
    @GET("product")
    suspend fun products(
        @Query("fpg_id") fpgId: Int? = null,
        @Query("spg_id") spgId: Int? = null,
        @Query("id") groupId: Int? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResp<ProductList>
    @GET("product/{id}") suspend fun product(@Path("id") id: Int): ApiResp<ProductDetail>
    @GET("cart") suspend fun cart(): ApiResp<CartData>
    @GET("gateway") suspend fun gateways(): ApiResp<GatewayList>
    @POST("login") suspend fun login(@Body body: LoginReq): ApiResp<LoginData>
    @POST("cart") suspend fun addToCart(@Body body: AddCartReq): ApiResp<Unit>
    @POST("cart/settle") suspend fun settle(@Body body: SettleReq): ApiResp<SettleData>
    @POST("pay") suspend fun pay(@Body body: PayReq): ApiResp<PayData>
    @GET("pay/{id}/status") suspend fun payStatus(@Path("id") id: Int): ApiResp<PayStatus>
}
```

OkHttp 拦截器三件事：① 注入 `Authorization: Bearer <jwt>` 与 `language: zh-cn`；② 保留 `sl-session` Cookie（CookieJar）；③ **按 body 的 `status` 判错**，401 清 token 并发事件通知 UI 跳登录，400 检查 `client_operate_methods`。另加一层 `Content-Type` 检查，遇到 `text/html` 直接抛自定义 `HtmlFallbackException`，避免解析崩溃。

---

## 8. 接口来源文件（都是从站点里取的）

| 文件 | 请求数 |
|---|---|
| `/clientarea/template/pc/default/api/common.js` | 108 |
| `/clientarea/template/pc/default/api/finance.js` | 41 |
| `/clientarea/template/pc/default/api/goodsList.js` | 21 |
| `/clientarea/template/pc/default/api/account.js` | 19 |
| `/clientarea/template/pc/default/api/home.js` | 11 |
| `/clientarea/template/pc/default/api/security.js` | 9 |
| `/clientarea/template/pc/default/api/certification.js` | 7 |
| `/clientarea/template/pc/default/api/product.js` | 6 |
| `/clientarea/template/pc/default/api/withdraw.js` | 1 |
| `/cart/template/pc/wry/api/goodsList.js` | 24 |
| `/cart/template/pc/wry/api/shopping.js` | 10 |
| `/cart/template/pc/wry/api/product.js` | 8 |
| `/clientarea/template/pc/default/utils/request.js` | 鉴权与响应码 |

辅助确认：`/cart/template/pc/wry/js/goods.js`（`$(".config-box").html(res.data.data.content)`）、`goodsList.js`（加购参数）、`shoppingCar.js`（询价参数）、`login.htm`（登录表单字段）。

---

## 9. 还没确认的（下一步抓包 / 需要账号）

1. **所有 POST/PUT/DELETE 的完整请求体字段**：为避免产生副作用没有实测，只从调用处拿到线索（`POST /cart` 的 `{product_id, qty, config_options, customfield}`、`POST /product/{id}/config_option` 的 `qty`/`config_options`、`POST /login` 的 `email`/`password` 等）。真机抓一次包即可补齐。
2. **`POST /login` 响应里 jwt 的确切字段名**（前端只做 `localStorage.setItem("jwt", ...)`，来源在登录页内联脚本）。
3. **需 JWT 接口的响应结构**：`/account` `/order` `/host/{id}` `/cart`(非空) 等，需要有测试账号登录后才能看到。
4. **支付回调形态**：`POST /pay` 返回的是表单、跳转 URL 还是二维码；以及 `/pay/{id}/status` 的终态枚举。
5. **配置项的结构化数据是否另有接口**：`config_option` 返回 Vue 模板，其内嵌脚本可能还请求了别的端点；若想彻底原生化配置页，需要继续读 `/plugins/reserver/...` 下的模板 JS。
6. **接口稳定性**：版本 10.7.2 的私有实现，升级可能变更；建议把本文第 3 节的匿名接口做成 CI 冒烟测试。
