# TermX Mobile (Android 原生 Termius 高保真复刻版)

[![Build TermX Android APK](https://github.com/codywon/termius/actions/workflows/android-build.yml/badge.svg)](https://github.com/codywon/termius/actions/workflows/android-build.yml)

一款基于 **Kotlin + Jetpack Compose (Material 3)** 打造的现代高保真 Android 原生 SSH 客户端、SFTP 文件管理器与隧道工具。全套 UI 规范深度遵循 Google Stitch **Obsidian Shell（黑曜石极客暗黑风）** 设计系统（参考项目：[Stitch Project 12105989460024767953](https://stitch.withgoogle.com/projects/12105989460024767953)）。

---

## 🎨 视觉与 UI 架构 (Design System: Obsidian Shell)

- **核心主色调**：`#67DF70` / `#3FB950`（Terminal Emerald 翡翠荧光绿，用于光标、状态高亮与核心按键）
- **背景层级**：`#0B141C` (深度黑曜石底色) / `#141C24` (卡片低层) / `#222B33` (表面高层)
- **次强调色**：`#A2C9FF` (Electric Ice 冰蓝次色，用于网络标签、SFTP 目录与快捷字符)
- **警告与强调**：`#FABC45` (琥珀金黄，用于延迟/警告) / `#FFB4AB` (高能荧红，用于中断/错误)
- **字体规范**：JetBrains Mono (终端字符与代码)、Inter / Google Sans (界面排版)

---

## 🚀 核心复刻功能特性

### 1. Hosts & Clusters (主机与集群资产管理 - Stitch Screen 1)
- 顶部 TermX 极客品牌标识、云同步状态指示（Cloud Sync Synced & Secure）与用户头像。
- 快速过滤标签：`All Hosts`, `Production`, `Staging`, `Docker`, `AWS`, `K8s`。
- 包含延迟指示、认证模式、快捷一键直连、SFTP 入口的高保真主机卡片。

### 2. SSH Terminal with D-Pad & Custom Keys (双层终端与触觉控制台 - Stitch Screen 6 & 4)
- **多会话横向滑动 Tab 标签栏**：支持一键多任务切换，带有状态呼吸灯与快速关闭。
- **三模辅助配件条 (Auxiliary Mode Switcher)**：
  - **`Keys` 经典按键模式**：第一层核心按键（`ESC`, `TAB`, `CTRL`, `ALT`, `↑`, `↓`, `←`, `→`, `/`, `-`, `|`, `~`）+ 第二层快捷宏（`Ctrl+C`, `Ctrl+Z`, `Ctrl+D`, `sudo !!`, `:wq`, `exit` 及 `+ Add Combo` 自定义按键）。
  - **`D-Pad Joystick` 触觉方向控制器 (Screen 4 原型还原)**：
    - 4-Way 触觉 D-Pad 导航盘（Up / Down / Left / Right），搭配中间醒目带触觉的圆形 **ENTER** 核心回车盘。
    - 左侧功能列：`ESC`, `TAB`, `CTRL`, `ALT`, `PgUp`, `PgDn`。
    - 右侧符号列：`/`, `|`, `~`, `:`, `PASTE`（剪贴板一键粘贴）。
    - 底部横滑微调带：`-`, `_`, `` ` ``, `&&`, `>>`, `^C`, `^Z`, `^D`, `^R`。
  - **`Quick` 快捷命令注入条**：一键注入执行常见运维指令（`docker ps`, `htop`, `git status`, `df -h` 等）。
- **会话遥测状态 (Telemetry Micro-Strip)**：实时显示网络 Ping 延迟（如 `38ms`）、在线时长计时器（`00:15:32` 动态走秒）、终端分辨率与编码（`80x24 xterm-256color`）。

### 3. Port Forwarding & SSH Tunnels (端口转发与隧道管理 - Stitch Screen 4)
- 终端界面常驻活跃隧道指示横幅：`Port Forwarding Active: localhost:8080 ➔ remote:3000`。
- 支持管理本地端口转发（Local Port Forwarding）、远程转发与动态 SOCKS5 代理。
- 内置独立 Socket 流量转发与字节吞吐调度引擎，支持一键切换启停与删除。

### 4. Host Connection Configuration (主机配置中心 - Stitch Screen 5)
- 深度还原 Stitch 设计：别名配置、颜色选择器、主机 IP 剪贴板快速粘贴、端口快速芯片 (`22`, `2222`, `443`)。
- 三种认证方式：`[SSH Key (Recommended)]` | `[Password]` | `[Vault Key]`。
- 高级 SSH 选项：跳板机 (Jump Host / Bastion)、端口转发、Keep-Alive 心跳间隔设置。
- **一键测试连通性**：提供 "Test SSH Handshake" 连通性测试与延迟诊断。

### 5. SFTP File Explorer (远程文件传输与浏览 - Stitch Screen 3)
- 远程路径面包屑逐级导航，直观展示文件夹与文件大小、修改时间。
- 支持文件下载、上传、重命名、删除及快捷返回上一级。

### 6. Security Keychain Vault & Snippets (钥匙串凭据库与代码片段)
- Android KeyStore 本地加密安全凭据。
- 内置 RSA / ED25519 密钥对生成器。
- 前台常驻保活服务（Foreground Service），在切出应用或锁屏时确保 SSH 长连接不被系统杀死。

---

## 🛠️ GitHub Actions CI 自动构建

本项目已配置完整的 GitHub Actions CI 流水线：
- 每次向 `main` 分支 push，会自动触发构建任务。
- 工作流文件位于 [`.github/workflows/android-build.yml`](.github/workflows/android-build.yml)。
- 编译产物在 Actions 详情页的 **Artifacts** 区域即可一键下载 **`TermX-Debug-APK`**。

---

## 💻 本地构建与安装

1. 克隆本仓库：
   ```bash
   git clone https://github.com/codywon/termius.git
   cd termius
   ```
2. 使用 **Android Studio** (Ladybug 2024.2+ / Jellyfish) 打开本仓库目录。
3. 等待 Gradle 同步完成（JDK 17 + Android SDK 35）。
4. 运行以下命令进行编译：
   ```bash
   ./gradlew assembleDebug
   ```
5. 生成的 APK 文件位于：`app/build/outputs/apk/debug/app-debug.apk`。
