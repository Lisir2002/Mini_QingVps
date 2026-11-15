# QingVPS

> 面向站点 `https://vps.qingyayun.cn`（IDCSmart 10.7.2，`/console/v1`）的原生 Android 客户端。
> 用原生页面逐步替代网页，把登录、选购、结算支付、订单/工单/子账户、实名认证、通知与更新
> 等核心链路全部收进 App 内完成。

- **包名**：`com.vps.qing`
- **技术栈**：Kotlin · Jetpack Compose · Material3 · Retrofit/OkHttp · Gson · Coil · DataStore
- **最低版本**：Android 8.0（API 26），目标 API 35
- **当前版本**：见 [`CHANGELOG.md`](CHANGELOG.md) 与 [Releases](https://github.com/Lisir2002/Mini_QingVps/releases)

---

## 目录

- [功能概览](#功能概览)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [核心设计](#核心设计)
- [版本与发布](#版本与发布)
- [文档索引](#文档索引)
- [测试](#测试)
- [说明与免责](#说明与免责)

---

## 功能概览

| 模块 | 能力 |
|---|---|
| 认证 | 邮箱+密码登录、注册、找回密码；图形验证码全链路；邮箱验证码全局 60s 冷却 |
| 首页 | 账户快照、余额/信用额度、已购服务、公告、快捷入口、下拉刷新 |
| 选购 | 商品分类/商品列表、购物车（站点云端）、配置选配与实时算价、结算 |
| 支付 | 网关由站点 `GET /gateway` 实时返回；App 内渲染站点支付 HTML / 真实支付码；订单有效期倒计时；「我已完成支付」三态校验 |
| 订单/产品 | 订单列表与详情、已购产品详情、待支付订单续付、交易记录 |
| 工单 | 列表 / 详情 / 新建 / 回复 / 关闭，客服新回复触发通知 |
| 子账户 | 列表 / 启停 / 删除 / 新建 / 编辑，多层权限树逐项勾选 |
| 实名认证 | 个人认证；企业认证（站点开启时可用，未开启时显式说明并支持重检） |
| 安全中心 | 密码 / 邮箱 / 手机修改，绑定变更触发安全通知 |
| 应用设置 | 17 套主题、自定义主色、内置字体、自定义背景图与柔化/缩放定位 |
| 权限 | 首次启动独立「权限管理」页，所有权限默认关闭，必要/可选逐项知情选择 |
| 通知 | 5 类渠道、分类开关、免打扰、四道闸自检、深链直达对应页面 |
| 关于/更新 | 官网/群聊/开发团队入口、App 内真实下载 + 安装确认、规范版本日志 |

设计原则：**不做网页兜底**（核心链路原生化）、**按钮必有反馈**、**顶/底栏透明玻璃无遮挡画布**、
**数据实时保真**（网关、价格、支付方式、有效期均来自站点真实参数，不硬编码）。

---

## 快速开始

### 环境要求

- JDK 17
- Android SDK Platform 35 + Build Tools 35.0.0
- Gradle 8.9（仓库已带 wrapper）

### 构建

```bash
# Debug APK（已配置固定签名，可直接覆盖安装）
./gradlew assembleDebug --no-daemon
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 单元测试
./gradlew testDebugUnitTest testReleaseUnitTest --no-daemon
```

> `app/qingvps.keystore` 为仓库内固定签名，本地与 CI 使用同一份，确保新版本可直接覆盖安装
> 而不出现签名冲突。该密钥仅用于公开的测试分发，正式上架务必换成私有 keystore。

### 配置

- `local.properties` 指向本机 Android SDK（`sdk.dir=...`）。
- 站点基址、鉴权头等集中在 `core/network` 与 `core/AppContainer`，无需额外环境变量。
- 软件更新读取 GitHub Releases（`UpdateChecker.REPO`）。

---

## 项目结构

```
app/src/main/java/com/vps/qing/
├── MainActivity.kt / AppNavHost.kt / QingVpsApp.kt   # 入口、顶层导航、Application 初始化
├── core/
│   ├── network/      # ApiEnvelope、缓存策略、拦截器、Token、更新检查
│   ├── permissions/  # 权限清单、权限管理页、系统授权实时快照
│   ├── notify/       # 通知渠道/开关/发送门面/调度/深链/设置页
│   ├── update/       # ApkDownloader（真实下载）、ApkInstaller（安装）
│   ├── ui/           # 玻璃组件库、主题、图标、字符串、尺寸令牌、边到边 insets
│   └── AppContainer / SettingsStore / EmailCodeCooldown  # 依赖容器与持久化设置
├── data/
│   ├── api/          # QingVpsApiService（Retrofit 接口）
│   └── repository/   # 各业务模型、解析器（纯 JVM）、仓库、请求体
└── feature/
    ├── auth/         # 登录 / 注册 / 找回密码 / 图形验证码
    ├── main/         # 底栏 Tab 容器、支付 overlay、顶/底栏
    ├── dashboard/    # 首页
    ├── shop/         # 选购 / 购物车 / 配置选配 / 结算支付
    ├── profile/      # 个人中心及订单/工单/子账户/实名/安全/关于等子页
    ├── message/      # 消息中心
    └── web/          # 内置 WebView（含支付深链处理）
```

分层约定：`feature/*` 只负责 UI 与状态收集；`data/repository` 的解析器为**纯 JVM** 函数，
便于单元测试；`core/*` 提供跨模块基础设施。

---

## 核心设计

| 主题 | 说明 | 详见 |
|---|---|---|
| 网络与缓存 | 统一 `ApiEnvelope`（响应体顶层 `code`）、`safeApiCall`/`safeApiCallEnvelope`；`CachePolicy` 对 `gateway`、`pay/*` 强制不缓存，保证支付状态轮询实时 | [docs/architecture.md](docs/architecture.md) |
| 权限合规 | 所有权限默认关闭；必要（网络、存储）与可选（通知、定位、外部应用、剪切板、相机）分类；`onResume` 刷新系统授权快照 | [docs/permissions.md](docs/permissions.md) |
| 通知体系 | 5 类渠道 + 四道闸（应用内同意→总开关→系统授权→免打扰/去重）+ 深链直达 | [docs/notification.md](docs/notification.md) |
| 支付真实性 | 站点 `POST /pay` 的 `data.html` 原样渲染；`GET /pay/{id}/status` 顶层 `code=Paid`；有效期来自订单 `unpaid_timeout`/`remain_pay_time` | [docs/payment.md](docs/payment.md) |
| App 内更新 | 真实字节进度下载 + 完整性校验 + 下载完成后安装确认（FileProvider） | [docs/app-update.md](docs/app-update.md) |
| 站点接口清单 | 192 个 `/console/v1` 端点调研 | [docs/site_api_inventory.md](docs/site_api_inventory.md) · [docs/site_endpoints.json](docs/site_endpoints.json) |

---

## 版本与发布

- **版本日志**：自 v1.4.19 起在 [`CHANGELOG.md`](CHANGELOG.md) 手写规范日志，
  Release 说明由 [`scripts/extract_changelog.py`](scripts/extract_changelog.py) 从对应段落生成，
  App 内「软件更新」展示的也是同一份内容。历史 v1.0.0–v1.4.18 的 tag 与 APK 全部保留。
- **CI**：`.github/workflows/android.yml`
  - 推送 `main` / PR：跑 JVM 单元测试；
  - 打 `v*` tag：单元测试 + `assembleDebug` + 生成 `SHA256SUMS.txt` + 发布 Release（读取版本日志）。
- **版本号**：`app/build.gradle.kts` 的 `versionCode`/`versionName` 与 `Strings.APP_VERSION` 同步推进。

发版清单：更新版本号 → 在 CHANGELOG 写该版本段落 → 运行双变体单测 → 打 tag →
确认 CI 两个 job 通过 → 校验本地与 Release APK 的 SHA256 一致。

---

## 文档索引

| 文档 | 内容 |
|---|---|
| [CHANGELOG.md](CHANGELOG.md) | 规范版本日志（v1.4.19 起） |
| [docs/architecture.md](docs/architecture.md) | 分层、网络/缓存、导航与返回栈 |
| [docs/permissions.md](docs/permissions.md) | 权限清单、默认关闭、实时同步 |
| [docs/notification.md](docs/notification.md) | 通知渠道、四道闸、去重与深链 |
| [docs/payment.md](docs/payment.md) | 支付真实协议与 App 实现 |
| [docs/app-update.md](docs/app-update.md) | App 内下载/进度/安装确认 |
| [docs/site_api_inventory.md](docs/site_api_inventory.md) | 站点接口施工图 |
| [docs/site_endpoints.json](docs/site_endpoints.json) | 机器可读端点清单 |

---

## 测试

单元测试位于 `app/src/test`，全部为纯 JVM（不依赖 Android 框架）：

```bash
./gradlew testDebugUnitTest testReleaseUnitTest --no-daemon
```

覆盖：解析器（订单/工单/子账户/配置/网关/支付）、价格链路、权限树勾选状态机、
验证码冷却、通知去重、主题、版本比较、APK 下载真实进度与取消等。
测试报告：`app/build/reports/tests/`。

---

## 说明与免责

- 本项目为站点功能的原生客户端封装，接口行为以站点实际返回为准；
  「企业认证」等能力是否可用取决于站点开关 `certification_company_open`。
- 仓库内固定签名仅用于测试分发；请勿用于生产上架。
- 访问站点如遇网络错误，站点提示不要使用 VPN。
