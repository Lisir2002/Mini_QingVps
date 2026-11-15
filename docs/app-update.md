# App 内更新说明

> v1.4.19 起，「关于 → 软件更新」支持 App 内**真实下载**新版本 APK，
> 进度来自真实字节数，下载完成后更换弹窗由用户确认是否立即安装。

## 1. 版本检查（`core/network/UpdateChecker.kt`）

- 读取 GitHub `releases/latest`，解析 `tag_name` / `name` / `body` / `html_url` /
  `published_at` / APK 资产 `browser_download_url` 与 `size`。
- `isNewerVersion()` 按点分段比较语义版本，忽略 `v` 前缀与预发布后缀。
- 独立 OkHttpClient + 短超时，失败返回 `null`，UI 降级为「打开 GitHub Releases 页面」，不卡住。

## 2. 更新弹窗阶段状态机（`AboutPage.kt` 的 `UpdatePhase`）

```
Checking      检查中
 ├─ CheckFailed      检查失败 → 打开 Releases / 重新检查
 └─ Ready            发现新版本 / 已是最新（版本对比胶囊 + 完整更新日志 + 包体大小）
      └─ Downloading  下载中（真实进度）
           ├─ DownloadFailed  下载失败 → 重新下载 / 浏览器下载
           └─ Downloaded      下载完成 → 【更换为独立安装确认弹窗】
```

弹窗视觉：糖果圆形徽章头部、当前 → 最新 版本对比、可滚动的更新日志卡片
（v1.4.19 起 Release 写规范版本日志，App 内完整展示而非粗暴截断）。

## 3. 真实下载（`core/update/ApkDownloader.kt`）

- OkHttp **流式**下载，响应 `Content-Length` 优先、缺失时用 Release asset `size` 兜底；
- 逐字节累计真实写入量，进度 `fraction = bytesRead / totalBytes`，
  **不做任何模拟递增或定时假动画**；每 100ms / 256KB 节流回传，但值始终是真实累计字节；
- 写入 `.tmp` → 完整性校验（非空 + 长度一致）→ 原子重命名为目标 APK，避免半成品被安装；
- 取消下载会同时 `cancel()` OkHttp call 中断阻塞读，删除临时文件，不留下可安装的目标文件；
- `formatSize()` 显示「已下载 X / 共 Y」与百分比。

## 4. 安装确认（`core/update/ApkInstaller.kt`）

下载完成后**更换弹窗**为「下载完成 / 是否立即安装」：

- 显示版本、包体大小、保存路径，提供「立即安装」「稍后安装」；
- 通过 `FileProvider`（`com.vps.qing.fileprovider`，`exported=false`，
  仅暴露 `cacheDir/updates/`）以 `content://` URI 拉起系统安装器，
  `FLAG_GRANT_READ_URI_PERMISSION` + `ACTION_VIEW` + `application/vnd.android.package-archive`；
- Android 8+ 需「安装未知应用」授权：未授予时点「立即安装」会打开系统设置页，
  开启后回来再点即可；**绝不静默安装**。

### Manifest / 资源

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<provider android:name="androidx.core.content.FileProvider"
    android:authorities="com.vps.qing.fileprovider"
    android:exported="false" android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

`res/xml/file_paths.xml` 仅配置 `<cache-path name="updates" path="updates/" />`。

## 5. 版本日志联动

- 仓库 `CHANGELOG.md` 手写规范版本日志；
- CI 用 `scripts/extract_changelog.py <tag>` 提取对应版本段落作为 Release `body`；
- App 内更新弹窗读取 `release.body` 展示 —— 用户看到的就是仓库里同一份版本日志。
