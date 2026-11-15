# 支付真实链路说明

> 支付是全项目最容易「看起来能用、实际不对」的部分。本文记录从站点官方前端 JS
> （`payDialog.js` / `pay_ctrl.php`）与真实抓包确认的协议，以及 App 的对应实现。

## 1. 站点真实协议

| 环节 | 接口 | 关键点 |
|---|---|---|
| 可用支付方式 | `GET /gateway` | 网关 `name` / `title` / `iconUrl` 原样来自站点，**不硬编码、不做白名单**；站点新增即出现、下线即消失 |
| 发起支付 | `POST /pay` | `body { gateway, id }`；响应 `data.data.html` 是网页端用 `$("#payBox").html(...)` **原样渲染**的真实支付内容 |
| 支付状态 | `GET /pay/{id}/status` | 成功标志 `code === "Paid"` 在**响应体顶层**，不是 `data.code` |
| 支付有效期 | `GET /order/{id}` | `unpaid_timeout` / `remain_pay_time` 才是真实有效期；官方 `time=300000` 只是轮询上限 |

真实样本中，`data.html` 是一段「立即支付」跳转型收银台按钮
（`<a href="https://zf.qingyayun.cn/submit.php?...">立即支付</a>`），**并非静态二维码**。

## 2. 早期错误（已修正）

- 把 `data.html` 里的收银台 `href` 用 `QrBitmap.encode()` **自造成二维码**——不是网页端给的支付码；
- `ApiEnvelope` 未接收响应体顶层 `code`，导致**永远读不到 `Paid`**，点「我已完成支付」静默失败；
- 有效期写死「最长等待 10 分钟」，与订单真实 `remain_pay_time` 不符；
- 「打开支付宝」无反应：内置 WebView 未处理 `alipays://` / `weixin://` 等支付深链。

## 3. App 实现

- **解析器**（`CheckoutModels.kt`）：
  - `PayOutcome` 保留 `payHtml` 原文 + 站点按钮文案 `payButtonLabel` +
    真实 `qrContent` / `qrImageUrl`（若站点确实返回二维码）+ 收银台 `payUrl`；
  - `payStatusEnvelope()` 读取顶层 `code`（兼容 `Paid` / `Unpaid` / `Upaid`）；
  - `PayDeadline` 解析 `unpaid_timeout` / `remain_pay_time`。
- **渲染**（`CheckoutSheet.kt`）：优先展示站点返回的**真实二维码图片/内容**；
  否则原样渲染站点 `data.html`（网页端 `#payBox` 同款），按钮文案取站点文字；
  **绝不再伪造二维码**。有效期用本地每秒倒计时，不再写死 10 分钟。
- **状态机**（`ShopViewModel`）：`payWith()` 按站点网关发起；「我已完成支付」调用修正后的
  状态接口，给出**成功 / 暂未检测到支付 / 查询失败**三态明确反馈；轮询上限由真实有效期驱动。
- **深链**（`WebViewScreen.shouldOverrideUrlLoading`）：`alipays://`、`weixin://` 等
  交给系统应用处理，其它 http(s) 仍在 App 内加载，修复「打开支付宝没反应」。
- **缓存**：`CachePolicy` 对 `gateway`、`pay`、`pay/*` 全部 `NONE`，轮询不会被缓存命中。

## 4. 权限相关性

「打开外部应用」是**可选权限且默认关闭**。关闭时支付仍应在 App 内 WebView 完成；
只有开启后才额外提供「用外部应用打开」入口。「复制支付链接」受剪切板可选权限控制。

## 5. 结论

当站点网关返回「立即支付」跳转按钮而非静态二维码时，App 不再显示任何伪造二维码；
若站点改返回真实二维码图片或支付码内容，App 会自动原生展示。所有支付方式、金额、
有效期均以站点实时参数为准。
