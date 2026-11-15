# 魔方 V10（IDCSmart / ZJMF-CBAP）优惠码系统深度调研

> 调研时间：2026-11-15
> 目标站点：vps.qingyayun.cn（IDCSmart 10.7.2，前缀 `/console/v1`）
> 调研方式：官方文档站 + 官方开源仓库源码（GitHub `idcsmart/ZJMF-CBAP`）+ Gitee 镜像交叉验证
> 源码版本：官方开源主线 10.4.6（前台逻辑与 10.3.4 一致）

## 一句话结论

魔方 V10 的优惠码**不是系统内置能力，而是一个官方插件** `addon/promo_code`（插件名"优惠码插件(基础版)"）。
它面向 C 端**只暴露 2 个接口**，且都是"**用户提交码 → 服务端校验并返回抵扣金额**"的模式；
**官方不存在"拉取全站优惠码列表 + 每个码可用专区/商品"的公开接口**。
因此 App 想"自动识别某专区有哪些优惠券"，唯一可行的数据来源仍是**专区公告/页面的 HTML 文案解析**，
再用官方 `/console/v1/promo_code/apply` 做真实校验抵扣。这与当前实现方向一致。

---

## 1. 系统定位：优惠码是一个"插件（addon）"

- 数据表全部以 `idcsmart_addon_promo_code*` 前缀开头 → 属于插件，不是核心表。
- 插件目录：`public/plugins/addon/promo_code/`
- 插件标题：`优惠码插件(基础版)`，作者：智简魔方。
- 这解释了为什么直接翻 `/console/v1` 核心 API 清单**找不到优惠码列表**——它在独立插件路由里。

官方营销工具其实是**两套并行机制**，不要混淆：

| 机制 | 前端组件 | 用户感知 | 核心接口 |
|---|---|---|---|
| **优惠码 promo_code** | `discountCode` | 手动输入一串码（如 `Qwy202652`） | `POST /console/v1/promo_code/apply` |
| **代金券 voucher** | `cashCoupon` | 从"可用券列表"里下拉选择 | `GET /console/v1/voucher/enable` |

> 用户截图里"专区公告中的优惠码"属于**第一种 promo_code**：是商家在后台建码后，把码写进公告正文里对外公布的，属于**运营文案**，不是接口数据。

---

## 2. 官方接口全清单（源码路由 `promo_code/route.php` 逐条提取）

### 2.1 前台 C 端（`/console/v1`，需 `CheckHome` 登录态）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/console/v1/promo_code/apply` | **应用/校验优惠码**，返回抵扣金额 |
| GET | `/console/v1/promo_code/host/:id/promo_code` | 产品内页查询该产品用过的优惠码 |

**仅此 2 个。没有 list 接口。**

### 2.2 后台管理端（`/admin/v1`，需 `CheckAdmin` 管理员权限）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/v1/promo_code` | 优惠码列表（分页/搜索/状态筛选） |
| GET | `/admin/v1/promo_code/generate` | 随机生成一个码 |
| POST | `/admin/v1/promo_code` | 创建 |
| GET | `/admin/v1/promo_code/:id` | 详情（含绑定的 products / need_products） |
| PUT | `/admin/v1/promo_code/:id` | 编辑 |
| PUT | `/admin/v1/promo_code/status` | 启用/停用 |
| DELETE | `/admin/v1/promo_code` | 删除 |
| GET | `/admin/v1/promo_code/:id/log` | 使用记录 |
| GET | `/admin/v1/promo_code/host/:id/log` | 产品优惠码记录 |

> 这些接口能拿到"码 ↔ 商品"绑定关系，但**需要管理员 JWT**，App 端用户账号无权调用；让 App 内置管理员凭据既不安全也不现实。

---

## 3. 核心接口 `promo_code/apply` 完全解析

### 请求参数（源码 `IndexController@apply` + `PromoCodeValidate`，10.4.6）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `scene` | string | 是 | 场景：`new` 新购 / `renew` 续费 / `upgrade` 升降级 |
| `promo_code` | string | new 时必填 | 优惠码字符串；老版本兼容不限长度，标准码 9 位 |
| `host_id` | int | 与 promo_code 二选一 | 产品 ID；续费/升降级可用它回溯原码 |
| `product_id` | int | 是 | 商品 ID |
| `qty` | int | new 时必填 | 数量（按数量循环计算折扣） |
| `amount` | float | 是 | 单价（优惠前） |
| `billing_cycle_time` | int | 是 | 周期秒数（用于周期限制校验） |

### 响应（成功）

```json
{
  "status": 200,
  "msg": "success",
  "data": { "discount": 12.50 }
}
```

失败：`{"status":400,"msg":"<本地化错误码>"}`（如 `addon_promo_code_has_expired`、`addon_promo_code_product_cannot_use`）。

### 服务端校验链（`clientPromoCodeSingleHandle`，逐条）

1. 码非空校验（new/renew 必须带码或 host_id）。
2. `status=1` 启用、`start_time/end_time` 有效期内。
3. `client_type`：`all` 全部 / `new` 无产品用户 / `old` 有激活产品用户。
4. `single_user_once=1` 时单用户只能用一次。
5. `max_times` 最大使用次数（`used >= max_times` 失效）。
6. 场景：`upgrade` 需 `upgrade=1`、`renew` 需 `renew=1`。
7. 周期限制 `cycle_limit=1` 时按 `billing_cycle_time` 落在 `monthly/quarterly/...` 秒数区间判断。
8. **商品适用**：`idcsmart_addon_promo_code_product` 表中若有绑定，则 `product_id` 必须命中，否则 `product_cannot_use`。
9. 前置商品 `need_products`：需已持有指定商品。
10. 折扣计算（`type`）：
    - `percent`：`amount * value / 100`
    - `fixed_amount`：`min(value, amount)`
    - `replace_price`：`max(amount - value, 0)`（覆盖价）
    - `free`：`discount = amount`（免费）
11. `loop=1` 时计算续费折扣 `discount2`。

### 码格式（`checkCode`）

标准码 **9 位**、仅 `[0-9A-Za-z]`，且必须同时含数字/小写/大写三类；
10.4.6 起注解 `兼容老财务优惠码(不限长度)`，放宽了长度校验。

---

## 4. 数据表结构（决定"码如何绑定专区/商品"）

```sql
-- 主表
idcsmart_addon_promo_code
  code, type(percent|fixed_amount|replace_price|free), value,
  status, client_type(all|new|old), start_time, end_time,
  max_times, used, single_user_once, upgrade, host_upgrade, renew,
  loop, cycle_limit, cycle, notes, delete_time ...

-- 码 → 允许商品（多对多）  ★ 这就是"码对应哪些专区/商品"的绑定表
idcsmart_addon_promo_code_product(addon_promo_code_id, product_id)

-- 码 → 前置要求商品（必须已持有）
idcsmart_addon_promo_code_product_need(addon_promo_code_id, product_id)

-- 使用日志
idcsmart_addon_promo_code_log(...)
```

关键：**一个码可绑定多个 `product_id`（商品）**；专区在魔方里是商品分组（`first_group/second_group`），
绑定是落到具体商品 ID 上的，不是直接绑专区。

---

## 5. 代金券（voucher / cashCoupon）——另一套机制

C 端接口（`api/common.js`、`api/finance.js`）：

| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| GET | `/console/v1/voucher/enable` | `scene, product_id[], price` | **可用代金券列表**（下拉选择用） |
| GET | `/console/v1/voucher` | params | 可领取代金券列表 |
| GET | `/console/v1/voucher/mine` | params | 我的代金券 |
| POST | `/console/v1/voucher/{id}/get` | - | 领取代金券 |

代金券是账户资产，有列表接口；优惠码没有。二者不要混为一谈。

---

## 6. 对 QingVPS App 的结论与建议

### 核心判断

1. **官方没有"优惠码列表 + 可用专区"接口**。想让 App 自动列出"某专区有哪些优惠券"， pure API 方案不成立。
2. 商家把 promo_code 写进**专区公告/购前须知文案**（截图所见 `Qwy202652` 等）是对外公布的**唯一渠道**，属于 HTML 文案。
3. 因此正确架构是：
   - **发现层**：解析 `cart/goodsList.htm?fpg_id=X&spg_id=Y` 等 SSR 页面的公告/须知 HTML，提取码 + 所属专区（即当前方向）。
   - **校验层**：用户输入/选中码后，调用官方 `POST /console/v1/promo_code/apply` 做真实校验，用返回的 `discount` 抵扣（已实现）。
   - **代金券**：如需，可另接 `/console/v1/voucher/enable` 做"可用券下拉"。

### 已确认的专区 → 码映射（来自 SSR 公告解析）

| 专区 spg_id | 优惠码 |
|---|---|
| spg15 | Qwy202652 |
| spg16 | Qwy202602 |
| spg17 | Qwy202606 |
| spg19 | Qwy202616 |

### 落地要点（实现"独立优惠码中心 + HTML 解析展示"时）

- 解析来源保持 SSR 页面，缓存策略走公共缓存（公告类）。
- 校验一律以 `promo_code/apply` 返回为准，不信任页面文案里的"面额"描述。
- 提交时 `scene/new` 必带 `promo_code + product_id + qty + amount + billing_cycle_time`；续费/升降级可用 `host_id` 回溯。
- 错误码本地化文案参考插件 `lang/zh-cn.php`。

---

## 附：官方资料来源

- 官方仓库：https://github.com/idcsmart/ZJMF-CBAP （AGPL）
- 接口文档入口：https://www.idcsmart.com/wiki_list/930.html
- 官方 Demo：https://v10demo.idcsmart.com/ytorfbze/login.htm （admin123 / admin123）
- 插件关键文件：`public/plugins/addon/promo_code/{route.php, controller/clientarea/IndexController.php, model/PromoCodeModel.php, validate/PromoCodeValidate.php}`
