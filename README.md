# TermX Mobile (Android Termius 复刻版)

[![Build TermX Android APK](https://github.com/codywon/termius/actions/workflows/android-build.yml/badge.svg)](https://github.com/codywon/termius/actions/workflows/android-build.yml)

一款基于 **Kotlin + Jetpack Compose (Material 3)** 打造的现代高保真 Android 原生 SSH 客户端与终端工具，UI 规范深度遵循 Google Stitch **Obsidian Shell（黑曜石极客暗黑风）** 设计系统（参考项目：`https://stitch.withgoogle.com/projects/12105989460024767953`）。

---

## 🎨 视觉与 UI 架构 (Design System: Obsidian Shell)

- **核心主色调**：`#67DF70` / `#3FB950`（翡翠荧光绿，用于光标、状态高亮与核心按键）
- **背景层级**：`#0B141C` (深度黑曜石底色) / `#141C24` (卡片低层) / `#222B33` (表面高层)
- **次强调色**：`#A2C9FF` (冰蓝次色，用于网络标签与 SFTP 目录)
- **字体规范**：JetBrains Mono (终端字符与代码)、Inter / Google Sans (界面排版)

---

## 🚀 核心复刻功能特性

1. **Hosts & Clusters (主机与集群资产管理)**
   - 顶部 TermX 品牌标识、云同步状态指示（Vault Synced & Secure）与用户头像。
   - 快速过滤标签：`All Hosts`, `Production`, `Staging`, `Docker`, `AWS`, `K8s`。
   - 包含延迟指示、认证模式、快捷一键直连、SFTP 入口的主机卡片。

2. **SSH Terminal with Custom Keys (终端仿真与双层扩展按键栏)**
   - 顶部多会话横向滑动 Tab 标签栏，支持快速在多个已连接服务器间切换。
   - **第一层核心按键**：`ESC`, `TAB`, `CTRL`, `ALT`, `↑`, `↓`, `←`, `→`, `/`, `-`, `|`, `~`。
   - **第二层快捷宏与组合键 (Quick Combos & Snippets strip)**：
     - `Ctrl+C`, `Ctrl+Z`, `Ctrl+D`, `Ctrl+R`, `Ctrl+A`, `Ctrl+E`, `sudo !!`, `:wq`, `exit`。
     - 支持 `+ Add Combo` 自定义宏按键，动态添加个性化命令或特殊控制序列并固定在键盘栏。
   - 高保真等宽字符 Canvas 渲染，完美支持 Vim / Htop / Tmux 等全屏 TUI 模式。

3. **Host Connection Configuration (主机配置中心)**
   - 深度还原 Stitch 设计：别名配置、颜色选择器、主机 IP 剪贴板快速粘贴、端口快速芯片 (`22`, `2222`, `443`)。
   - 三种认证方式：`[SSH Key (Recommended)]` | `[Password]` | `[Vault Key]`。
   - 高级 SSH 选项：跳板机 (Jump Host)、端口转发、Keep-Alive 心跳间隔设置。
   - **一键测试连通性**：提供 "Test SSH Handshake" 连通性测试与延迟诊断。

4. **SFTP File Explorer (远程文件传输与浏览)**
   - 远程路径面包屑导航，直观展示文件夹与文件大小、权限信息。
   - 支持文件下载、上传、重命名、删除及快捷返回上一级。

5. **Security & Identity Vault (安全凭据与保活服务)**
   - Android KeyStore + AES-GCM 本地加密保存敏感密码与私钥。
   - 前台常驻保活服务（Foreground Service），在切出应用或锁屏时确保 SSH 长连接不中断。

---

## 🛠️ GitHub Actions 自动化编译

本项目已配置完整的 GitHub Actions CI 流水线：
- 每当向 `main` 分支提交代码，或在 GitHub Actions 页面点击 **Run workflow** 时，系统将自动构建 Debug APK。
- 编译产物将保存为 `TermX-Debug-APK` Artifact，可直接下载并在 Android 手机或模拟器上安装运行。

---

## 💻 本地构建与运行

1. 使用 **Android Studio** (2024.1+) 打开本仓库目录。
2. 等待 Gradle 同步完成（JDK 17 + Android SDK 35）。
3. 运行 `./gradlew assembleDebug` 或在 Android Studio 中直接点击 **Run** 安装。
