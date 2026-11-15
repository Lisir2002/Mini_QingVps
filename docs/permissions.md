# 权限合规说明

> 原则：**所有权限默认关闭**，尊重用户知情权。首次启动进入独立「权限管理」页，
> 逐项说明用途与不开通的影响，由用户决定；应用内随时可在「我的 → 权限管理」复管。

## 1. 权限清单（`core/permissions/PermissionCatalog.kt`）

| 权限 | 分组 | 说明 | 不开通的影响 |
|---|---|---|---|
| 本地网络权限 `network` | 必要 | 访问互联网/局域网，连接站点接口 | 无法连接站点，所有在线功能不可用（安装期系统权限） |
| 存储 / 相册读写 `storage` | 必要 | 读取用户主动选择的图片（自定义背景） | 不能用自定义背景图；应用仍可用 |
| 通知权限 `notifications` | 可选 | `POST_NOTIFICATIONS`，推送各类提醒 | 无系统通知，应用内功能不受影响 |
| 定位权限 `location` | 可选 | 预留给按位置的能力 | 不影响购买、支付与管理 |
| 打开外部应用 `external_apps` | 可选 | 拉起支付 App / 浏览器等 | 支付仍在 App 内 WebView 完成 |
| 读取/写入剪切板 `clipboard` | 可选 | 复制支付链接等 | 不提供一键复制入口 |
| 使用摄像头 `camera` | 可选 | 预留给扫码等能力 | 不影响核心链路 |

> 定位、相册内容**绝不上传服务器**。同意之前不申请任何运行时权限。

## 2. 运行时权限与状态同步

- `PermissionPrefs`（DataStore）持久化每项「应用内是否同意」；`PermissionStatus` 读取
  **系统真实授权**生成 `StateFlow<Snapshot>`。
- `MainActivity.onResume()` 调用 `PermissionStatus.refresh()`：从系统设置返回、前后台切换时
  立即重读系统授权，App 内开关/状态文案即时重组，解决「在系统里开了通知，App 内不同步」。
- 若系统撤销通知权限，会自动回滚 `PermissionPrefs` 中的应用内同意标记，
  杜绝「开关开着但没权限」。
- 权限申请结果回调（`RequestMultiplePermissions`）后用快照判断授权，被拒绝则回滚开关。

## 3. 权限管理页

同一份 `PermissionOnboardingScreen` 复用两处：

- **首次启动**（`isOnboarding=true`）：顶层导航页，逐项说明 + 完成按钮；
- **设置内复管**（`isOnboarding=false`）：个人中心子页。

每项卡片显示用途、不开通影响、以及实时状态（已开启·系统已授权 / 应用内已关闭·系统权限仍授权 等）。
系统撤销后提供「去系统设置」入口。

## 4. 应用内更新相关权限

- `REQUEST_INSTALL_PACKAGES`：仅为「App 内更新」声明，**不静默安装**。
  下载完成后由用户点「立即安装」确认，未授予「安装未知应用」时引导到系统设置页。
- `FileProvider`：`exported=false` + `grantUriPermissions`，仅暴露 `cacheDir/updates/`，
  不下放任何其他目录。详见 [app-update.md](app-update.md)。

## 5. Manifest 概览

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

Android 11+ 的 `queries` 声明 `https`/`http`/`alipay`/`weixin` 可见性，供「打开外部应用」
开关开启后拉起支付深链。默认不申请、不拉起。
