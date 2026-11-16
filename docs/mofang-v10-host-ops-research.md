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
