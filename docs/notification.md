# 通知体系说明

> App 内通知是一套完整体系：渠道分类 → 应用内开关 → 系统授权 → 免打扰/去重 → 深链落地。
> 目标是「通知内容与实际落点、实际触发时机对称」，不出现点了没反应或永远静默的渠道。

## 1. 渠道（`core/notify/NotificationCatalog.kt`）

| 渠道 id | 标题 | 触发 | 点击落点 | 重要性 |
|---|---|---|---|---|
| `qing_order` | 订单与支付 | 支付成功、待支付订单提醒 | `orders`（订单列表） | 高 |
| `qing_ticket` | 工单与回复 | 工单有新客服回复 | `ticket:<id>` / `tickets` | 默认 |
| `qing_news` | 公告与活动 | 站点发布新公告 | `announcements` / `announcement:<id>` | 默认 |
| `qing_renew` | 续费与到期 | 服务即将到期 | `hosts` | 高 |
| `qing_security` | 安全中心 | 密码/邮箱/手机变更成功 | `security` | 默认 |

所有落点都是 **App 内原生路由**，不跳网站。`DeepLinkBus`（StateFlow）保证冷启动点击也不丢事件。

## 2. 四道闸（发送前逐级判定）

`QingNotifier` 发送前依次检查：

1. **应用内已同意通知权限**（`PermissionPrefs`）；
2. **通知总开关**打开（`NotificationPrefs.master`）；
3. **系统已授予通知权限**（`POST_NOTIFICATIONS` / `NotificationManagerCompat.areNotificationsEnabled()`）；
4. **当前时段允许推送**（免打扰 `isInDnd`）+ **去重**。

「通知管理」页顶部实时展示这四道闸的状态，并提供「一键开启通知」（补齐应用内同意、
总开关、Android 13+ 系统权限申请）。

## 3. 触发链路（`NotificationDispatcher`）

首页数据快照与上一份对比，产生差异通知：

- **新公告**：新增公告 → `qing_news`；
- **待支付订单**：出现未支付订单 → `qing_order`（文案「点击前往订单列表查看并继续付款」，
  与落点 `orders` 对齐）；
- **续费到期**：即将到期服务 → `qing_renew`；
- **工单新回复**：`ProfileViewModel.fetchTicketDetail()` 对比上一次回复列表，
  只有**新增的客服回复**才触发 `qing_ticket`。

## 4. 去重设计

`dedupeKey(channelId, businessId)` 避免重复打扰，但**不能让后续事件永远静默**：

- 工单通知按 **回复 id** 去重（`ticket-<id>-<replyKey>`）。
  早期按工单 id 去重，导致同一工单后续客服回复全部被吞掉，已修正。
- 安全通知按 **事件 id** 去重。早期按分钟去重，语义混乱，已修正。

## 5. 深链映射

`ProfileScreen` 消费 `navRequest` 时完整放行
`product:*` / `order:*` / `ticket:*` / `announcement:*` / `orders` / `tickets` / `hosts` /
`security` / `permissions` / `notifications`。早期工单通知 `ticket:<id>`、订单通知 `order:<id>`
被映射丢弃 → 点通知无反应，已修正。

## 6. 通知管理页

- 分类开关、总开关、免打扰时段、提示音（全局静音开关，不是只作用于重要通知）、
  测试通知、系统通知设置入口。
- 「我们如何通知你」文案明确写出真实触发时机（首页快照差异、工单新回复、
  密码/邮箱/手机变更成功、支付成功即时提醒），与实现一致。
- 根布局避开状态栏，避免顶栏与状态栏堆叠。
