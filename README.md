# TeamX Mobile

[![Build and Release TeamX APK](https://github.com/codywon/termius/actions/workflows/android-build.yml/badge.svg)](https://github.com/codywon/termius/actions/workflows/android-build.yml)

一款基于 **Kotlin + Jetpack Compose (Material 3)** 打造的现代纯原生 Android SSH 终端、SFTP 文件管理器与隧道调度工具。视觉风格采用 **Obsidian Shell（黑曜石极客暗黑风）** 设计系统。

---

## 🎨 视觉系统与设计规范

- **核心主色调**：`#67DF70` / `#3FB950`（Terminal Emerald 翡翠荧光绿，用于光标、状态高亮与核心按键）
- **背景层级**：`#0B141C` (深度黑曜石底色) / `#141C24` (卡片低层容器) / `#222B33` (表面高层容器)
- **次强调色**：`#A2C9FF` (Electric Ice 冰蓝次色，用于网络标签、SFTP 目录与快捷字符)
- **状态提示**：`#FABC45` (琥珀金黄，用于延迟/警告) / `#FFB4AB` (高能荧红，用于中断/错误)
- **字体排版**：JetBrains Mono (终端字符与代码)、Inter / Google Sans (界面交互)

---

## 🚀 核心功能特性

### 1. Hosts & Clusters (主机与集群资产管理)
- 顶部 TeamX Mobile 品牌标识、云同步安全状态指示灯与用户中心。
- 快速过滤标签：`All Hosts`, `Production`, `Staging`, `Docker`, `AWS`, `K8s`。
- 高保真主机卡片：展示别名、环境标签、用户名、IP 与端口，提供一键直连、SFTP 与配置管理。

### 2. SSH 终端仿真与三模扩展控制台
- **状态栏防重叠适配**：完美适配沉浸式状态栏与各种打孔屏、水滴屏。
- **多会话横向滑动 Tab 标签栏**：支持一键多任务快速切换，带有运行状态呼吸灯与快速关闭。
- **中文字符完美支持**：内置 POSIX/xterm 宽字符排版引擎，彻底杜绝全角中文字符与标点符号重叠错位。
- **三模软键盘配件条 (Auxiliary Mode Switcher)**：
  - **`Keys` 经典按键**：第一层功能键（`ESC`, `TAB`, `CTRL`, `ALT`, 方向键, 常用符号）+ 第二层快捷宏（`Ctrl+C`, `Ctrl+Z`, `Ctrl+D`, `sudo !!`, `:wq`, `exit` 以及 `+ Add Combo` 自定义按键）。
  - **`D-Pad Joystick` 触觉方向控制器**：
    - 4-Way 触觉 D-Pad 导航盘（上、下、左、右），配合中央醒目、带触觉反馈的圆形 **`ENTER`** 核心回车盘，专为手机端操作 Vim、Nano、Htop、Tmux 深度优化。
    - 左侧功能列：`ESC`, `TAB`, `CTRL`, `ALT`, `PgUp`, `PgDn`。
    - 右侧符号列：`/`, `|`, `~`, `:`, 以及 `PASTE`（剪贴板一键粘贴）。
    - 底部横滑微调带：`-`, `_`, `` ` ``, `&&`, `>>`, `^C`, `^Z`, `^D`, `^R`。
  - **`Quick` 运维指令快速注入**：一键注入执行常见运维指令（`docker ps`, `git status`, `htop`, `df -h`, `free -m` 等）。
- **会话遥测状态微条 (Telemetry Micro-Strip)**：实时显示网络 Ping 延迟（如 `38ms`）、在线时长动态走秒计时器（`00:15:32`）、终端窗口参数与负载指标。

### 3. Port Forwarding & SSH 隧道调度中心
- 终端界面常驻活跃隧道提示横幅：`PORT FORWARDING ACTIVE: localhost:8080 ➔ remote:3000`。
- 支持管理本地端口转发（Local Port Forwarding）与远程目标端口映射。
- 独立 Socket 流量转发与字节调度引擎，支持一键切换启停。

### 4. Host Connection Configuration (主机配置中心)
- 键盘弹起自动避让与防遮挡滚动，底部宽裕垫高空间，顺畅输入长表单。
- 别名配置、颜色选择器、主机 IP 剪贴板快速粘贴、端口快速芯片 (`22`, `2222`, `443`)。
- 三种安全认证：`[Password (推荐)]` | `[SSH Key]` | `[Vault Key]`，支持密码明文/密文切换。
- 高级选项：后台防掉线保活、Keep-Alive 心跳间隔设置。
- **一键连通性测试**：提供 "Test SSH Handshake" 连通性测试与延迟诊断。

### 5. SFTP 远程文件浏览器
- 远程路径面包屑逐级导航，直观展示文件夹与文件大小、修改时间。
- 支持文件下载、上传、重命名、删除及快捷返回上一级。

### 6. 安全凭据保险库与现代加密算法
- 内置现代 BouncyCastle 提供者，原生支持 `X25519`、`Ed25519`、`curve25519-sha256` 等现代 SSH 密钥交换算法。
- Android KeyStore 本地加密安全凭据。
- 前台常驻保活服务，适配 Android 14+ `dataSync` 权限体系，锁屏/切应用连接不中断。

---

## 🛠️ GitHub Actions CI 自动构建与发布

本项目已配置完整的 GitHub Actions CI 流水线：
- 每次向 `main` 分支 push，会自动编译 Debug APK 并上传为构建 Artifacts。
- 每次推送以 `v*` 开头的 tag 时，会自动编译 Debug 与已签名的 Release APK，并自动创建 GitHub Release。

---

## 💻 本地构建与安装

1. 克隆本仓库：
   ```bash
   git clone https://github.com/codywon/termius.git teamx-mobile
   cd teamx-mobile
   ```
2. 使用 **Android Studio** (Ladybug 2024.2+ / Jellyfish) 打开本工程根目录。
3. 等待 Gradle 同步完成（JDK 17 + Android SDK 35）。
4. 运行编译命令：
   ```bash
   ./gradlew assembleDebug
   ```
5. 生成的 APK 文件位于：`app/build/outputs/apk/debug/app-debug.apk`。
