# TermX Mobile

一款专为移动端设计的现代、轻量、高性能 Android 原生 SSH 客户端与远程服务器运维工具。

采用 **Kotlin + Jetpack Compose (Material 3)** 纯原生架构打造，内置自研高性能 VT100 / xterm 终端仿真器、SFTP 远程文件管理器与运维速查手册。

> **Powered by codywon**

---

## 特性一览

### 💻 现代原生终端仿真 (Terminal Engine)
- **高性能 Canvas 渲染**：Run-Length 样式批处理与硬件加速，低功耗保持满帧流畅。
- **宽屏漫游与自动折行**：支持标准 140 列宽屏输出（完美适配 `docker ps`、`kubectl`、`ps aux`），单指左右平滑漫游配合水平滚动指示条；具备标准的 VT100 Pending Wrap 自动折行机制。
- **120 帧硬件级手势缩放**：双指捏合缩放字号全程使用 GPU 图层矩阵变换，抬手单次提交字号，零重排零重绘。
- **三模辅助配件栏**：参考主流生产力工具设计，提供输入法横滑辅助键、快捷指令卡片宏、完整 PC 全键盘（F1-F12/PageUp/Delete）三模即时切换。
- **横屏自适应空间优化**：横屏软键盘弹起时智能合并为 34dp 极简单行，最大化终端有效可视高度。
- **全面屏手势自然回退**：边缘侧滑返回与物理返回键平滑回到主页，后台会话无缝保活，杜绝直接退出应用。

### 📁 SFTP 远程文件管理
- **面包屑路径浏览**：毫秒级进入远程目录，支持文件上传、下载、删除、重命名与权限查看。
- **内置在线文本编辑器**：支持远程配置文件（nginx、systemd、docker-compose 等）全屏快速编辑与安全保存，键盘自动避让。

### 📖 生产运维速查手册 (Cheatsheet)
- 内置 Linux 系统维护、Docker 容器编排、网络端口排查、Systemd 服务管理等 30+ 条生产高频常用命令。
- 支持分类快速过滤、即时模糊搜索、一键复制及个人常用指令扩展。

### ⚙️ 凭据管理与系统级守护
- **Vault 安全凭据库**：支持密码与非对称私钥（Ed25519、RSA、ECDSA）统一加密管理与多主机复用。
- **后台长连守护**：集成前台保活服务、CPU WakeLock 及 Wi-Fi 锁，设置页支持一键检测与申请原生电池优化豁免（Doze 模式）。
- **极客主题配色**：内置黑曜石翡翠、极客冰蓝、复古琥珀、赛博霓虹 4 套极客终端配色方案，支持深浅色模式即时切换。
- **断线智能自愈**：网络闪断自动定时重试连接，状态日志格式规范隔离。

---

## 架构概览

```
TermX Mobile
├── app/src/main/java/com/termius/clone/
│   ├── terminal/
│   │   ├── engine/          # 自研 VT100 终端解析器、单元格缓冲池与字符矩阵
│   │   └── session/         # SSH 连接管理、PTY 分配、重连看门狗与 SFTP 客户端
│   ├── ui/
│   │   ├── components/      # TerminalView (Canvas 绘制与手势)、AccessoryBar (三模按键)
│   │   ├── screens/         # 主机资产、文件管理、速查手册、终端工作区与全局设置
│   │   └── theme/           # Material 3 动态色彩、极客终端主题与字号状态系统
│   └── service/             # Android 前台长连保活与通知服务
```

关于移动端终端在字符流状态机、多线程锁、视口尺寸重构（Resize）与 Android 软键盘适配的底层避坑与设计思考，请参阅：  
👉 [TermX 移动端终端架构设计与避坑经验指南 (DEVELOPMENT_LESSONS.md)](./DEVELOPMENT_LESSONS.md)

---

## 快速开始

### 方式 1：直接下载安装包
前往项目的 [Releases](../../releases) 页面，下载最新的正式版 APK（`TermX-Mobile-v1.0.0-release.apk`）直接安装使用。

### 方式 2：本地源码编译
**环境要求**：
- Android Studio Ladybug (2024.2+) 或更高版本
- JDK 17
- Android SDK 35 (最低支持 Android 8.0, API 26)

```bash
# 1. 克隆代码仓库
git clone https://github.com/codywon/termius.git
cd termius

# 2. 编译 Debug APK
./gradlew assembleDebug

# 产物输出路径：app/build/outputs/apk/debug/app-debug.apk
```

---

## 许可证与使用条款

本项目遵循 **个人学习与交流许可协议 (Personal Study and Communication License)**。

- **仅供学习与交流**：本项目源码与构建产物仅供个人学习、技术研究、学术交流及非商业场景下的评估测试使用。
- **严禁商业用途**：任何个人或组织不得将本软件全部或部分代码用于商业盈利、商业项目集成、转售、分发闭源衍生品或作为收费服务提供。
- **版权声明**：所有代码分发与衍生版本均须保留原作者版权声明及 `Powered by codywon` 署名。

详细法律条款请阅读项目根目录下的 [LICENSE](./LICENSE) 文件。

---

Copyright (c) 2026 **codywon**. Powered by codywon. All rights reserved.
