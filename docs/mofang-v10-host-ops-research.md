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
