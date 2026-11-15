# 更新日志（Changelog）

本文件记录 QingVPS 每个正式版本面向用户与开发者的重要变更，格式参考
[Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号采用
[语义化版本](https://semver.org/lang/zh-CN/)。

> **说明**：自 **v1.4.19** 起采用本规范，每个版本的 Release 说明直接从本文件对应段落生成。
> 早期版本（v1.0.0 – v1.4.18）由 CI 以统一模板自动发布，其 tag 与 APK 资产全部保留以便追溯，
> 但不再逐版补写日志。历史版本列表见仓库 [Releases 页面](https://github.com/Lisir2002/Mini_QingVps/releases)。

变更类型约定：

- `新增` 新功能 / 新页面 / 新能力
- `优化` 对已有功能的体验、性能、视觉改进
- `修复` 缺陷与不符合预期的行为
- `文档` 文档、注释、发布流程相关
- `测试` 单元测试与构建校验

---

## [1.4.19] - 2026-11-15

### 新增

- **App 内真实下载更新包**：关于页 →「软件更新」不再只跳浏览器，改为 App 内通过 OkHttp
  流式下载 APK。新增 `ApkDownloader`（`Content-Length` 优先、Release asset size 兜底、
  逐字节累计、支持取消、原子重命名与完整性校验）与 `ApkInstaller`（FileProvider +
  `REQUEST_INSTALL_PACKAGES` 拉起系统安装器）。
- **下载成功后的安装确认弹窗**：下载完成后自动更换为独立的「下载完成 / 是否立即安装」
  弹窗，提供「立即安装」「稍后安装」；未授予「安装未知应用」时引导到系统设置。
- **关于页开发团队署名**：「APP 开发」下方显示「MiniMe-Core开发团队」及官方链接
  `https://qm.qq.com/q/39MulEBLXW`，点击可打开。
- **文档体系**：新增 `README.md`、`docs/architecture.md`、`docs/permissions.md`、
  `docs/notification.md`、`docs/payment.md`、`docs/app-update.md`；建立本文件与
  `scripts/extract_changelog.py`，CI Release 说明改为读取规范版本日志。

### 优化

- **版本更新弹窗美化**：糖果圆形徽章头部、版本对比胶囊（当前 → 最新）、包体大小、
  完整可滚动的更新日志卡片；进度条改用真实字节比例自绘（含百分比与「已下载 / 总量」），
  不再有假进度。
- **子账户操作权限卡片**：由「只列一级分类 + N 项计数」重写为递归展开的**具体权限树**，
  逐层显示每个权限节点名称与 `rules`，支持整组开关、逐项勾选、全选/半选/未选三态，
  顶部显示「已选 / 共 N 项」；按站点 `addChild.js` 协议生成提交 auth（选中叶子 +
  半选/全选父链，「概要」必选）。
- **顶栏上部空白**：修复个人中心子页（权限管理 / 通知管理 / 安全中心编辑页）在
  Scaffold 已让位状态栏后又补 `statusBarsPadding()` 造成的**双重让位**；引入
  `LocalTopInsetApplied` + `topInsetPadding()`，顶层导航页仍自行让位，避免一刀切。

### 修复

- 权限树提交逻辑不再遗漏父节点链、不再把「概要」误当可取消项；编辑回填的完整 auth
  列表可直接作为状态输入并保真还原。
- 更新解析补读 APK 资产真实字节数（`apkSize`），用于进度分母与包体展示。

### 测试

- 新增 `AuthTreeSelectionTest`（8 例，覆盖多层树 / 三态 / 整组开关 / 概要必选 / 回填）、
  `ApkDownloaderTest`（7 例，含真实流式进度单调性、取消、完整性校验、MockWebServer）、
  扩展 `UpdateCheckerTest`（`apkSize` 解析）。
- JVM 单元测试合计 **243 例**，Debug / Release 双变体全绿。

---

## 历史版本

v1.0.0 – v1.4.18 的 tag 与 APK 均保留在仓库 Releases 页面，未在本文档补写逐版日志。
各阶段的代表性工作可参考提交记录与 `docs/` 下的专题文档。
