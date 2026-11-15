#!/usr/bin/env python3
"""从 CHANGELOG.md 提取指定版本的段落，作为 GitHub Release 的说明正文。

用法：
    python3 scripts/extract_changelog.py v1.4.19            # 打印到 stdout
    python3 scripts/extract_changelog.py v1.4.19 RELEASE_BODY.md  # 写入文件

设计目的（v1.4.19）：CI 不再用 generate_release_notes 自动生成的模板文案，
而是把仓库里「正儿八经手写」的版本日志作为 Release 说明，保证 App 内更新弹窗
（读取 release.body）与仓库文档看到的是同一份内容。

找不到对应版本段落时返回非零退出码，让 CI 明确失败而不是发布空日志。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHANGELOG = ROOT / "CHANGELOG.md"


def version_of(tag: str) -> str:
    """v1.4.19 / 1.4.19 -> 1.4.19；忽略预发布后缀。"""
    return tag.strip().lstrip("vV").split("-")[0].strip()


def extract(text: str, version: str) -> str | None:
    # 匹配 "## [1.4.19] - 日期" 或 "## 1.4.19"
    pattern = re.compile(r"^##\s+\[?" + re.escape(version) + r"\]?.*$", re.MULTILINE)
    m = pattern.search(text)
    if not m:
        return None
    start = m.end()
    nxt = re.search(r"^##\s+\[?\d", text[start:], re.MULTILINE)
    end = start + nxt.start() if nxt else len(text)
    body = text[start:end].strip()
    # 去掉段落末尾可能残留的 "---" 分隔线
    body = re.sub(r"\n---\s*$", "", body).strip()
    return body


def main() -> int:
    if len(sys.argv) < 2:
        print("用法: extract_changelog.py <tag> [输出文件]", file=sys.stderr)
        return 2
    tag = sys.argv[1]
    version = version_of(tag)
    if not CHANGELOG.exists():
        print(f"找不到 {CHANGELOG}", file=sys.stderr)
        return 1
    body = extract(CHANGELOG.read_text(encoding="utf-8"), version)
    if not body:
        print(f"CHANGELOG.md 中找不到版本 [{version}] 的段落", file=sys.stderr)
        return 1

    header = (
        f"## QingVPS {tag}\n\n"
        f"> 本说明摘自仓库 [CHANGELOG.md](https://github.com/Lisir2002/Mini_QingVps/blob/main/CHANGELOG.md)，"
        f"自 v1.4.19 起采用规范版本日志。\n\n"
    )
    footer = (
        "\n\n---\n\n"
        "**安装说明**：Debug 构建使用仓库内固定签名，可直接覆盖安装旧版本（不会签名冲突）。\n"
        "- 应用名 QingVPS，包名 `com.vps.qing`\n"
        "- 站点 `https://vps.qingyayun.cn`（IDCSmart 10.7.2，`/console/v1`）\n"
        "- 校验值见同版本 `SHA256SUMS.txt`\n\n"
        "App 内「关于 → 软件更新」可直接下载并安装本 APK；如遇网络问题请避免使用 VPN 访问站点。"
    )
    full = header + body + footer

    if len(sys.argv) >= 3:
        Path(sys.argv[2]).write_text(full, encoding="utf-8")
        print(f"已写入 {sys.argv[2]}（{len(full)} 字符）")
    else:
        print(full)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
