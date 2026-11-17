# 魔方 V10 已购机器操作接口调研（v1.4.22）

本文记录 QingVPS「已购机器详情页」重装系统 / 电源 / 重置密码 / 日志能力的接口依据。
结论来自魔方 V10 官方开源源码（`idcsmart/ZJMF-CBAP`，用 10.3.4 本地源码 + 10.4.6 交叉验证），
并对线上站点 `https://vps.qingyayun.cn`（IDCSmart 10.7.2）做了存在性实测。

## 插件体系

机器操作属于 `reserver/mf_cloud`（客户端转发层）+ `server/mf_cloud`（服务端实现）插件，
与优惠码 `addon/promo_code` 同属魔方 V10 插件，不属于核心 API，故 `/console/v1` 清单里需按插件路由找。

## 接口清单（C 端，前缀 `/console/v1`，需登录）

路由定义见 `public/plugins/reserver/mf_cloud/route.php`：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `product/{id}/remf_cloud/image` | 可选镜像列表（两级：分组 → 镜像） |
| GET | `remf_cloud/{id}` | 实例详情（cpu/内存/带宽/镜像/电源状态） |
| POST | `remf_cloud/{id}/reinstall` | 重装系统 |
| POST | `remf_cloud/{id}/reset_password` | 重置密码 |
| POST | `remf_cloud/{id}/on` `/off` `/reboot` `/hard_off` `/hard_reboot` | 电源操作 |
| GET | `remf_cloud/{id}/log` | 操作日志 |

## 重装参数（关键）

来自 `server/mf_cloud/logic/CloudLogic.php::reinstall()` 与 `controller/home/CloudController.php::reinstall()`：

- `image_id`：镜像 id（必填），必须属于该商品且 `enable=1`；收费镜像需已购买。
- `password`：新密码；与 `ssh_key_id` 二选一。App 统一用密码方式（`password_type=0`）。
- `port`：端口（必填），App 默认 `22`。
- 可选：`code`（站点开启「重装短信验证」时需要；本站配置 `reinstall_sms_verify=0`，故不传）。

前端弹窗 `template/clientarea/js/cloudDetail.js` 的 `reinstallData` 同样是
`{ image_id, password, ssh_key_id, port }`，与后端一致。

## 镜像列表结构

`GET product/{id}/remf_cloud/image` → `data.list[]`：

```json
{ "id": 1, "name": "Linux", "icon": "...",
  "image": [ { "id": 10, "name": "CentOS 7", "charge": 0, "price": 0 } ] }
```

App 复用选购页的 `MfParser.parseImages` 解析，无需重复实现。

## 线上实测（2026-11-16）

| 接口 | 未登录响应 | 结论 |
| --- | --- | --- |
| `GET product/{id}/remf_cloud/image` | `{"status":200,...,"data":{"list":[]}}` | 存在（id 无效故空列表） |
| `POST remf_cloud/{id}/reinstall` | `{"status":401,"msg":"当前未登录..."}` | 存在且需登录 |
| `POST remf_cloud/{id}/reset_password` | 401 | 存在 |
| `GET remf_cloud/{id}/log` | 401 | 存在 |
| `POST remf_cloud/{id}/on` | 401 | 存在 |

401 表示接口真实存在、App 登录态下携带 token 即可正常调用。

## App 落点

- `data/repository/HostOpsBodies.kt`：重装 / 重置密码请求体构造、连接信息文本、SSH 命令、日志解析（纯函数，便于单测）。
- `data/api/QingVpsApiService.kt` + `data/repository/ProfileRepository.kt`：接口与仓库封装（复用 `mfImages` + `MfParser`）。
- `feature/profile/PurchasedProductPage.kt`：详情页拆为 概览 / 连接信息 / 重装系统 / 费用与订单 四个 tab；
  连接信息逐项 + 一键复制；重装页镜像选择 + 二次确认 + 电源 + 重置密码 + 日志。

---

## v1.4.23 补充调研：统计图表 / 磁盘 / 网络 / 备份快照 / 应用预设

用户要求（2026-11-17）：把网站端已购机器管理页的「统计图表 / 磁盘 / 网络 / 备份与快照 / 日志 / 应用预设」
并入 App，重装页系统选择改为「下拉 + 菜单区块」，统一各页下拉刷新。
按「实现前先查接口 + 开源源码 + 网上经验」的硬性要求，补充调研如下（**先调研后编码**）。

### 调研方法（三路交叉）
1. **官方开源源码**：本地缓存 `idcsmart/ZJMF-CBAP` 10.3.4（`public/plugins/reserver/mf_cloud/route.php`
   + `controller/home/CloudController.php` 的 `@return` 注释 + `server/mf_cloud/template/clientarea/api/cloud.js`
   + `js/cloudDetail.js` 的实际调用）。
2. **线上站点实测**：`https://vps.qingyayun.cn`（IDCSmart 10.7.2），逐个接口未登录探测，
   全部返回 `{"status":401,"msg":"当前未登录..."}` → **证明接口真实存在**（401=需登录，非 404）。
3. **网上经验**：智简魔方官网 / 官方文档 `docs.idcsmart.com` / GitHub / Gitee / 雨云对接教程交叉确认
   魔方云（KVM 加强版）具备实例监控、磁盘、备份快照、网络与安全组能力。

### 关键结论：接口路径前缀映射
前端 JS 调 `/mf_cloud/{id}/xxx`（server 层），C 端实际暴露为
`/console/v1/remf_cloud/{id}/xxx`（reserver 转发层加 `re` 前缀）。
App 基址已含 `/console/v1/`，故 Retrofit 路径直接用 `remf_cloud/...`（与 v1.4.22 一致）。

### 接口清单（路径 + 参数 + 返回，均已源码+实测确认）

| 功能 | 方法与路径 | 参数 | 返回 data |
| --- | --- | --- | --- |
| 统计-CPU/带宽/磁盘IO/内存 | GET `remf_cloud/{id}/chart` | `start_time`(秒级), `type`=`cpu\|bw\|disk_io\|memory` | `list[]` |
| 统计-流量包 | GET `remf_cloud/{id}/flow` | 无 | `total,used,leave,reset_flow_date` |
| 实时用量 | GET `remf_cloud/{id}/real_data` | 无 | `cpu_usage,memory_total,memory_usable,memory_usage` |
| 磁盘列表 | GET `remf_cloud/{id}/disk` | 无 | `list[{id,name,size,create_time,type,type2,status}]` |
| 快照列表 | GET `remf_cloud/{id}/snapshot` | `page,limit` | `list[{id,name,create_time,notes}],count` |
| 备份列表 | GET `remf_cloud/{id}/backup` | `page,limit` | `list[{id,name,create_time,notes}],count` |
| IP 列表 | GET `remf_cloud/{id}/ip` | `page,limit` | `list[{ip,subnet_mask,gateway}],count` |
| NAT 转发 | GET `remf_cloud/{id}/nat_acl` | 无 | 转发规则列表 |
| NAT 建站 | GET `remf_cloud/{id}/nat_web` | 无 | 建站规则列表 |
| 安全组 | GET `security_group` | `page=1,limit=9999` | `list[{id,name,...}]` |
| 操作日志 | GET `remf_cloud/{id}/log` | `page,limit` | `list[],count`（v1.4.22 已有） |

### chart 四类曲线的字段映射（来自 cloudDetail.js 实际解析，权威）
- `cpu` → `list[].value`（CPU 使用率 %）
- `bw` → `list[].in_bw/out_bw`（进/出带宽 bps）
- `disk_io` → `list[].read_bytes/write_bytes`（B/s，÷1024² 得 MB/s）、`read_iops/write_iops`
- `memory` → `list[].total/used`（字节，÷1024³ 得 GB）
- `list[].time` 为秒级时间戳；时间范围 `start_time = now - {86400, 259200, 604800}`
  （过去 24 小时 / 3 天 / 7 天），与截图下拉一致。

### 应用预设（截图第五张）——特殊处理，非盲猜
- 在开源 10.3.4 全量源码、线上站点 API 清单、官方文档、全网检索中**均未找到该功能的 C 端接口规范**；
- 站点已安装插件清单（`addons_js`）显示云主机相关仅 `IdcsmartCloud`(29)，无独立应用/面板插件；
- 它是 10.7.2 客户端新增/站点定制能力，无公开接口文档 → **不猜测接口**。
- App 落地策略：该 Tab 复用项目现有 WebView 能力内嵌站点原页面（站点自身渲染并调用其私有接口），
  保证功能可见可用，待后续抓到真实接口再原生化。

### 对实现的具体指引
- API：`QingVpsApiService` 增补上表 GET（chart 用 `@Query`）。
- 数据模型：`HostOpsBodies.kt` 增补 chart/flow/disk/snapshot/backup/ip 解析纯函数 + 单测。
- 仓库：`ProfileRepository` 增补对应方法，复用 `ApiEnvelope`/`ApiResult` 容错。
- ViewModel：`ProductDetailUi` 扩展各 Tab 数据 + 懒加载；Tab 结构由 4 个扩为网站同款。
- UI：统计图表用 Compose 自绘折线/面积图（不引第三方图表库）；重装系统选择复用 `core/ui/SelectionKit.kt`
  的 `GlassDropdown` + `ChipFlowRow`（下拉 + 菜单区块，模仿选购页）；下拉刷新统一走 `core/ui/RefreshKit.kt`。

---

## v1.4.24 补充：电源状态实时轮询（修复「管理 Tab 点击无效果」）

### 问题定位
用户反馈管理 Tab 所有操作「点击无效果」。逐一排查排除了接口路径/方法/请求体/回调链/按钮/反馈机制/
hostId 来源等所有静态环节（接口线上实测均返回标准 401，证明存在且写法正确）。**真正根因**：
官方前端在电源/重装/重置成功后会立即调用 status 接口递归轮询、并把中文 `desc` 实时渲染，
而 App 缺失该轮询，操作后只重拉**非实时**的详情，界面状态不变 → 主观「无效果」。

### 官方接口依据（源码 + 线上实测）
- 路由：`GET remf_cloud/:id/status` → `CloudController@status`（route.php:35，线上实测存在）。
- 返回（`@return` 注释 875-881 行）：`data.status`（`on`=开机 / `off`=关机 / `operating`=操作中 / `fault`=故障）
  + `data.desc`（中文状态描述）。
- 前端 `cloudDetail.js:getCloudStatus()`（1794 行）：`id` 在 URL；成功后
  `this.status = res.data.data.status`、`this.statusText = res.data.data.desc`；
  **当 `status == "operating"` 时递归调用自身**，直到稳定态才停止。
- 每次操作成功回调里都调用 `getCloudStatus()`：开机 1939、关机 1959、重启、重装 1623、重置密码 2632。

### 细分状态推导（网站端四态 → App 六态文案）
status 只有四态，网站端的「正在开机/正在关机/正在重启-关机中/正在重启-开机中」是
「operating + 用户动作 + 状态迁移轨迹」推断。App 复刻为纯函数 `HostOpsBodies.powerLabel(state, action, sawOff)`：
- 稳定态：`on→已开机`、`off→已关机`、`fault→故障`；
- operating 且 action=on/off：正在开机/正在关机；
- operating 且 action=reboot/hard_reboot：未观察到 off→「正在重启-关机中」，观察到 off 后→「正在重启-开机中」；
- 无 action 的 operating：直接用服务器 desc。

### 实现要点
- API/仓库/ViewModels：`cloudStatus` 接口 + `PowerState` 解析 + `pollPowerStatus()` 递归轮询
  （每 1.2s 一次，上限 75 轮防死循环；重启需观察 off→on 轨迹才算完成；完成/失败都清理状态）。
- 操作提交成功**立即**设 `pendingPowerAction` 给出可见反馈（按钮「处理中…」+ 状态横幅 + 转圈），
  再后台轮询；轮询结束刷新详情（系统镜像/端口/密码实时更新）与日志。
- 进入管理 Tab / 下拉刷新时也拉取一次 status，保持状态常新。
- 5 例纯函数单测覆盖稳定态、开机/关机、重启两阶段、desc 回退。

---

## v1.4.25 补充：修复「一直转圈 / 日志不显示 / 版本号不更新」

用户反馈三点：① 关于页版本号已升级仍显示旧值；② 电源操作后一直转圈不落到对应状态；
③ 多次重启/重装但操作日志为空，且日志应从「管理」拆成独立 Tab。

### ① 版本号（非接口问题，构建配置单一数据源）
- 根因：`Strings.APP_VERSION` 硬编码 `"1.4.22"`，`build.gradle` versionName 已是 1.4.24+，两处脱节。
- 修复：`buildFeatures.buildConfig = true`，`APP_VERSION = BuildConfig.VERSION_NAME`（普通 val，
  故 `const val` 改为 `val`）。显示处（AboutPage / ProfileScreen）无需改动，自动同步。

### ② 电源一直转圈（源码实证，v1.4.24 结束条件写错）
- 官方 `CloudLogic::status`（server/mf_cloud/logic/CloudLogic.php:76-119）映射：
  `on/wait_reboot/paused → on`、`off → off`、`suspend → suspend`、
  `task/cold_migrate/hot_migrate → operating`、其余 → fault。C 端只会拿到
  **on/off/suspend/operating/fault 五态**。
- 官方前端 `cloudDetail.js:getCloudStatus()`（1810 行）：**仅当 status=="operating" 递归，
  否则 emit 停止**；操作按钮回调（doOn/doOff/doReboot/…）成功后统一 `this.status="operating"`
  再调用它。没有「必须看到 off 再回到 on」的判定。
- v1.4.24 的错误：`done = sawOff && raw=="on"`。因 wait_reboot 直接映射成 on（不经过 off），
  `sawOff` 永远为 false → 转满 75 轮≈90s；且 stable 漏掉 suspend（暂停态也死循环）。
- 修复：`HostOpsBodies.isPowerSettled(raw, action, offRounds, rounds)`：
  - operating/空值不结束；
  - 动作驱动至少 2 轮再落定（防读到旧状态过早结束）；
  - 普通动作任意稳定态即结束；
  - 重启时 off 是瞬时态不结束，但连续 off 达 `REBOOT_OFF_GRACE_ROUNDS=5` 兜底结束（防死转）。
  - `parseStatus` 的 stable 补上 `suspend`，文案映射 `suspend→已暂停`。

### ③ 操作日志（源码实证，缺分页参数）
- 官方 `CloudController::log @return`：`data.list[].description / create_time / ip`、`data.count`。
- 官方前端 `getLogList()`（cloudDetail.js:3574）：参数 `id + {...logParams}`，
  `logParams = {page:1, limit:20, orderby:"id", sort:"desc", keywords:""}`（634 行）。
- 后端 `SystemLogModel::systemLogList`：sort/orderby/limit 缺省时取站点 config，部分站点返回空/乱序。
- v1.4.24 的 `@GET cloudLog` 只传 id，其它全缺省 → 列表空或不可见。
- 修复：Retrofit `cloudLog` 补 `@Query page=1, limit=50, orderby="id", sort="desc"`。
- UI：新增 `HostDetailTab.OP_LOG` 独立 Tab（`Strings.HOST_TAB_OP_LOG`），从「管理」中拆出
  `OpLogCard`，进入即 `refreshHostLogs`，顶栏下拉刷新也带上日志刷新。
- 6 例新增单测，共 286 例全过。

---

## v1.4.26：续费接口（addon idcsmart_renew，源码实证）

### 依据来源
- `plugins/addon/idcsmart_renew/controller/clientarea/IndexController.php`（C 端续费控制器）
- `plugins/addon/idcsmart_renew/model/IdcsmartRenewModel.php`（续费页/下单逻辑）
- `plugins/reserver/mf_cloud/template/clientarea/js/cloudDetail.js`（前端 `subRenew()` / `renewPage()`）
- `plugins/reserver/mf_cloud/template/clientarea/api/cloud.js`（`renewPage` / `renew` / `renewAutoStatus`）

### 官方接口（App 侧统一加 `console/v1/` 前缀）
| 端点 | 方法 | 说明 |
|---|---|---|
| `host/{id}/renew` | GET | 续费页：`data.host[]` 可续费周期 |
| `host/{id}/renew` | POST | 提交续费：body `{id, billing_cycle, customfield:{promo_code:[]}}` |
| `host/{id}/renew/auto` | GET | 自动续费状态：`data.status` 1开0关 |
| `host/{id}/renew/auto` | PUT | 自动续费开关：body `{id, status:1\|0}` |
| `host/renew/batch` | GET/POST | 批量续费（本期未接入 UI） |

### 关键结论（源码实证）
- 周期字段来自 `CloudLogic::durationPrice` / 通用模块 `duration_price`：
  `id / billing_cycle / duration(秒) / price(售价) / base_price / renew_amount / max_renew`。
  提交时 **`billing_cycle` 必须原样回传**（云产品英文 `month/year`，通用产品中文 `小时`），App 不做翻译。
- `renewPage` 前置校验：产品需 `status in [Active,Suspended]` 且 `billing_cycle != onetime`，否则返回 400（`host_cannot_renew`）→ App 侧周期为空时给出「不支持续费」提示。
- `renew` 提交返回（`IdcsmartRenewModel::renew` 395 行）：
  - 余额抵扣/免费自动续费 → `code=Paid`，直接续费成功；
  - 需在线支付 → `code=Unpaid, data.id=订单ID`（前端 `subRenew()` 拿它 `showPayDialog(orderId, amount)`）。
- 优惠码：`customfield.promo_code` 是**数组** `["code"]`（与官方一致，App 预留该参数）。

### App 改动
- API 层新增 4 个端点；`RenewModels.kt`（`RenewParser`）解析周期/结果/自动续费状态。
- ViewModel：`refreshRenew / selectRenewCycle / submitRenew / setAutoRenew`；续费状态挂在 `ProductDetailUi`。
- UI：费用与订单 Tab 新增「续费」区块（周期单选 + 应付金额 + 立即续费），自动续费改为可点击开关。
- 支付衔接：`submitRenew` 拿到订单ID后回调 `onContinuePay → shopVm.startPayOrder`，复用现成支付面板。
- 7 例新增单测（周期全字段/数组兼容/空数据/Unpaid订单ID/Paid/内嵌code/自动续费状态），共 293 例全过。

---

## v1.4.27：购前须知数据源修正 + R8 混淆（源码与线上实证）

### 购前须知根因（线上 SSR 实证）
- 现象：选购页「购前须知」显示「主题云2023年中秋国庆放假公告」等无关全站公告标题。
- 错误实现（v1.4.20）：`ShopViewModel.openConfig()` 用 `catalog.announcements()`（GET /announcement
  全站公告）前 3 条填充购前须知。
- 线上抓取 `cart/goodsList.htm?fpg_id=&spg_id=` 真实 HTML 证实：真正的购前须知是页面顶部
  「公告 · 购买前必读」区块（`id="announcementContentPC"` / 手机端 `announcementContent`），
  内容为「基础服务条例：一、退款规则 / 二、使用须知 …」。
- 该公告全站统一（对比 spg 17/20/25 内容一致），用商品 fpg/spg 抓取即可，分组缺失用默认专区兜底。
- 修复：新增 `PrePurchaseNoticeParser`（纯 JVM，按 div 深度配对提取 + 清洗 HTML/样式），
  Repository `prePurchaseNotices()` 复用 `AppContainer.fetchHtml`；UI 按章节标题/条款分行渲染，
  默认折叠 6 条可展开。新增 5 例单测。

### R8 混淆决策（反射点排查）
- 排查全项目反射/字符串反查点：`ServicePanel.valueOf(token)`（导航 token）、
  Gson 经 Retrofit 反序列化 `ApiEnvelope` / `CaptchaRefreshResponse`、Compose runtime。
- 关键坑：仅 `-keepclassmembers enum ... valueOf()` 不够——R8 会把枚举常量名改成 b/c/d，
  `valueOf("HOSTS")` 运行时抛 `IllegalArgumentException`。改用 `-keep enum ServicePanel { *; }`
  整类保留；DEX `strings` 实测 HOSTS/SUB_ACCOUNTS/CERTIFICATION 明文存在，验证通过。
- 效果：APK 8.8 MB → 5.1 MB；`minifyReleaseWithR8` + `shrinkReleaseRes` 均生效。
- CI 由 assembleDebug 改 assembleRelease，发布产物真正带混淆（更新检测动态读 .apk asset，不受文件名影响）。
