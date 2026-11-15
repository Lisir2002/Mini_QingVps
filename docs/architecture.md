# 架构说明

> QingVPS 采用「UI 层 / 数据层 / 基础设施层」三层划分，全部 Kotlin + Jetpack Compose。
> 本文说明分层职责、网络与缓存、导航返回栈、边到边布局等关键设计。

## 1. 分层

```
feature/*          UI + 状态收集（Composable / ViewModel）
    ↓ 调用
data/repository/*  仓库 + 纯 JVM 解析器 + 请求体
    ↓ 调用
core/network/*     Retrofit/OkHttp、ApiEnvelope、缓存、Token
```

- **UI 层**：`feature/` 下每个业务一个包。`ViewModel` 持有 `StateFlow<UiState>`，
  `Composable` 用 `collectAsState()` 渲染；页面只收集输入、不直接发网络请求。
- **数据层**：`data/repository` 的 `*Parser` / `*Bodies` / `*Models` 都是**纯 JVM**（无 Android 依赖），
  只用 Gson `JsonElement` 宽松取值，因此能直接在 JVM 单元测试里覆盖。
- **基础设施**：`core/` 提供 `AppContainer`（依赖容器）、`SettingsStore`（DataStore 设置）、
  网络、权限、通知、更新、UI 组件库。

## 2. 网络与响应封装

- `ApiEnvelope` 统一站点响应：除常规 `data` 外，保留**响应体顶层 `code`**。
  这是支付状态接口 `GET /pay/{id}/status` 返回 `code=Paid` 的必需字段（早期只读 `data.code`，
  导致永远检测不到支付成功）。
- `safeApiCall()` 处理常规响应；`safeApiCallEnvelope()` 需要访问整个 `ApiEnvelope`（顶层 code）时使用。
- `TokenManager` + `Interceptors` 注入 JWT `Authorization: Bearer` 与 `language: zh-cn`；
  401 通过 `UnauthorizedBus` 触发整个导航图重建到登录页。

## 3. 缓存策略（`core/network/CachePolicy.kt`）

按 HTTP 方法与相对路径分三档：

| 档位 | 含义 | 典型路径 |
|---|---|---|
| `PUBLIC` | 公共字典数据，可落磁盘、带 TTL | `common`、`menu`、`country`、静态资源 |
| `PRIVATE` | 账号私有数据，仅内存、按会话命名空间隔离、绝不落盘 | `index`、`account`、`order`、`host*` |
| `NONE` | 一律不缓存 | 所有非 GET、`captcha`、算价、`gateway`、`pay`、`pay/*` |

> **支付状态轮询必须实时**：`gateway` 与 `pay/*` 明确为 `NONE`，否则缓存命中会让
> 「我已完成支付」永远读到旧的未支付状态。

## 4. 导航与返回栈

- 顶层 `AppNavHost`：`LOGIN` / `REGISTER` / `FORGOT` / `PERMISSIONS`（首次启动）/ `MAIN` / `WEB`。
- `MAIN` 是底栏 Tab 容器（`Scaffold` + 4 个 Tab）。个人中心子页用**逗号分隔的字符串栈**
  （`navStack`）管理：`panel:*`、`product:*`、`order:*`、`ticket:*`、`subnew`、`subedit:*`、
  `secedit:*`、`settings`、`permissions`、`notifications`、`about`。
  任何页面进入子页都 push、返回都 pop，保证「从哪进就退回哪」，而不是替换当前面板。
- 通知深链通过 `DeepLinkBus`（StateFlow，冷启动不丢事件）映射到对应子页 token。

## 5. 页面叠加修复

早期用 `Box` 叠加渲染多个页面，底层 Tab 仍在组合中，半透明玻璃导致上一页文字透出
（「确认支付叠在订单详情」「选配页叠在采购页」）。修复：**任何全屏 overlay 打开时，
底层 Tab 内容一律不渲染**；公告列表与详情改互斥渲染。

## 6. 边到边与状态栏让位（v1.4.19）

`MainActivity.enableEdgeToEdge()` + 透明系统栏 + 根级 `CartoonBackground` 铺满整窗。
状态栏让位规则由 `LocalTopInsetApplied` + `topInsetPadding()` 统一管理：

- `MAIN` 容器内 `Scaffold` 已用 `padding.calculateTopPadding()` 让位 → provide `true`，
  子页**不再**补 `statusBarsPadding()`，消除「顶栏上方一大截空白」的双重让位；
- 顶层导航页（登录/注册/找回/WebView/首次启动权限页）不在 Scaffold 内 → 默认 `false`，
  页面自行让位，避免标题与状态栏堆叠。

## 7. UI 组件库（`core/ui`）

`CartoonKit`（形状/玻璃顶栏/底栏/卡片/按钮）、`GlassSurface`、`GlassTopBar`、
`glassField()`（输入框）、`UiT`（尺寸令牌）、`AppIcons`（自绘矢量图标）、
`CartoonColors`/`ThemePalettes`（17 套主题）、`AppFonts`（内置字体）。
所有卡片为半透明磨砂玻璃，支持用户自定义背景图与柔化/缩放定位。
