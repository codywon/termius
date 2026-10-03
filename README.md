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

### 1. 消除顶部虚位留白，沉浸式紧凑标题
- 全局优化 WindowInsets 与 Scaffold 布局，消除多余内边距计算。
- 顶部标题栏紧密自然贴顶，状态栏与标题栏色彩一体化沉浸，屏幕可视空间最大化。

### 2. 精简四大核心 Tab 导航架构
- 💻 **Hosts (主机资产)**：服务器资产直观卡片、实时搜索、多分组标签筛选、一键连接。
- 📁 **SFTP (远程文件)**：目录面包屑导航、文件上传下载、全屏在线文本编辑器（键盘自动避让与防遮挡）。
- 📖 **Cheatsheet (运维速查手册)**：
  - 内置 Linux、Docker、网络端口、Systemd 服务、磁盘文件清理、安全防火墙等 30+ 条生产高频命令；
  - 分类 Chip 过滤与即时模糊搜索；
  - 点击一键复制代码，支持添加个人常用自定义指令。
- ⚙️ **Settings (全局设置中心)**：
  - **Vault 凭据钥匙串**：私钥 (Ed25519, RSA) 与密码安全保存与管理；
  - **4 款极客终端主题配色即时换肤**：黑曜石翡翠、极客冰蓝、复古琥珀、赛博霓虹紫；
  - **动态中英文双语热切换**：简体中文 / English 一键即刻生效，无需重启；
  - **连接与保活设置**：SSH Keepalive 探活、后台常驻保护状态监控；
  - **关于与更新**：TeamX Mobile、`Powered by codywon` 专属标牌与多通道最优路径平滑检查更新。

### 3. 网易 UU 远程同款三模辅助交互系统
- **输入法模式**：整行横向滑动功能键 (ESC, TAB, CTRL, ALT, 方向键等)；
- **快捷键模式**：智能收起系统软键盘，左侧支持【编辑】/【添加】/【调序】自定义指令宏；
- **电脑键盘模式**：独享完整 F1-F12、Insert、Delete、PageUp/Down、Home/End、箭头键；
- **100% 全屏终端**：一键隐藏配件条，右下角极简半透明悬浮胶囊随时唤起。

### 4. 工业级后台常驻与断连自愈
- 持有 CPU WakeLock 与全功能低延迟 Wi-Fi Lock，息屏切后台防休眠断连；
- 屏幕广播动态感知与定时心跳探活空包。

### 5. 多镜像并发竞速平滑在线升级
- 聚合 5 大国内高速开源镜像加速通道；
- 1200ms 并发 HEAD 测速竞速排选最优路径；
- 低速看门狗熔断自动切线，流式进度回传与 FileProvider 安全安装。

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
