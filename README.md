# TermX Mobile

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84.svg?style=flat-square&logo=android)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Toolkit](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Release](https://img.shields.io/badge/Release-v2.0.0-success.svg?style=flat-square)](https://github.com/codywon/termius/releases)
[![License](https://img.shields.io/badge/License-Non--Commercial%20Personal%20Use-blue.svg?style=flat-square)](./LICENSE)

TermX Mobile 是专为 Android 移动平台打造的高性能终端仿真器、SSH/SFTP 客户端与掌上自主 SRE 运维智能体。基于 Kotlin 与 Jetpack Compose 纯原生技术栈构建，专注于在移动触控屏幕上提供稳定、低延迟、深度环境感知与高安全护栏的远程服务器管理体验。

> **Powered by codywon**

---

## 核心特性

### 🤖 自主 SRE 运维智能体 (Autonomous Ops Agent)
- **极简 Pi-Agent 架构**：纯粹 ReAct 循环（Thought -> Action -> Observation -> Final Answer），流式打字与思考链深度解析，兼容 DeepSeek、通义千问、OpenAI、本地 Ollama 等任意接口。
- **远程系统环境感知 (Environment-Aware)**：自动探测目标主机操作系统发行版（Ubuntu/Debian/CentOS/Alpine/Arch）、包管理器、内核架构与核心服务，100% 精准下发系统兼容命令。
- **安全审批拦截 (Human-in-the-Loop)**：内置高危命令正则沙箱，针对 `rm -rf`、磁盘格式化、关机、清空防火墙等致命指令，强制挂起并弹出带风险说明的人工审批卡片，必须经用户亲自批准方可执行。
- **终端屏幕一键感知**：一键提取当前终端屏幕最后 50~100 行可见输出，命令报错无需繁琐复制粘贴。
- **双通道轻量联网搜索**：双轨引擎（必应 Bing CN + DuckDuckGo HTML），免配置任何第三方搜索 API Key 即可实时检索报错排障文档。

### 💻 终端仿真 (Terminal Engine)
- **硬件加速渲染**：基于 Canvas 原生绘制管线与 Run-Length 样式批处理算法，低功耗维持 60~120 FPS 满帧流式吞吐。
- **宽屏漫游与自动折行**：支持标准的 140 列等宽显示与触控水平平滑漫游，完美适应 `docker ps`、`kubectl`、`htop` 等宽表输出；内置标准 VT100 Pending Wrap 延迟折行状态机。
- **无损矩阵手势缩放**：双指捏合缩放字号全程通过 GPU 图层变换预览，手势释放时执行单次视口重排，杜绝频繁回流与闪烁。
- **原生触控导航交互**：支持系统全局返回手势平滑退回主页，后台 PTY 会话透明保活，避免误触丢失会话。

### 辅助按键与卡片宏 (Accessory & Shortcuts)
- **流体手势拖拽排序**：基于弹簧物理动画与位置占位推开算法的卡片排序系统，支持高频键位随心布局。
- **高频操作即时响应**：默认预设 `Ctrl+C (中断/复制)`、`Ctrl+V (粘贴)`、方向键与常用控制序列，支持自定义快捷指令。
- **三模输入快速切换**：在输入法辅助单行、指令宏卡片面板与完整 PC 全键盘（F1-F12、Tab、Esc、PageUp/Down）之间即时切换，横屏时自动折叠优化可视高度。

### 远程文件传输 (SFTP Manager)
- **并发互斥安全通道**：SFTP 底层驱动基于非阻塞 Coroutine Mutex 互斥排队机制，彻底解决并发调用导致的 Channel 踩踏与管道断流。
- **纯内存流式传输**：文本查看与在线编辑直通内存缓冲流，无需本地零散临时文件落地，秒级加载与保存。
- **即时面包屑导航**：毫秒级多级路径切转与分级浏览，支持文件批量管理、权限查看与远程服务配置即时更新。

### 会话安全与网络自愈 (Connection & Reliability)
- **统一凭据库 (Vault)**：集中式凭据加密存储，全面支持 Ed25519、RSA、ECDSA 密钥对与密码复用。
- **透明重连与看门狗**：底层集成心跳保活（Keep-Alive）与断线自动探测，网络波动后提供非侵入式自愈连接。
- **系统级长连保活**：支持 Android 前台服务、CPU WakeLock 与原生系统电池优化（Doze）白名单指引。
- **离线运维手册**：内置常用 Linux 系统指令、Docker 容器操作及常用排障 Cheatsheet，支持即时模糊检索与单键复制。

---

## 架构设计

```
TermX Mobile
├── app/src/main/java/com/termius/clone/
│   ├── terminal/
│   │   ├── engine/          # VT100/xterm 字符解析器、单元格缓冲池与视口矩阵
│   │   └── session/         # SSH2 会话管理、PTY 分配、重连看门狗与互斥 SFTP 客户端
│   ├── ui/
│   │   ├── components/      # TerminalView (Canvas 绘制与手势)、AccessoryBar (辅助键与宏卡片)
│   │   ├── screens/         # 主机资产、文件管理、速查手册、终端视口与全局设置
│   │   └── theme/           # Material 3 动态配色系统与终端极客主题
│   └── service/             # Android 前台长连保活与通知服务
```

深入的技术演进与底层避坑总结，请参阅：  
📘 [TermX 移动端终端架构设计与避坑经验指南 (DEVELOPMENT_LESSONS.md)](./DEVELOPMENT_LESSONS.md)

---

## 快速开始

### 安装包下载
前往项目的 [Releases](https://github.com/codywon/termius/releases) 页面，下载最新发布的正式安装包：
- `TermX-Mobile-v2.0.0-release.apk`

### 从源码构建

#### 环境要求
- Android Studio Ladybug (2024.2+) 或更高版本
- JDK 17 (推荐 Eclipse Temurin 或 OpenJDK)
- Android SDK 35 (最低支持 Android 8.0 / API 26)

#### 构建步骤
```bash
# 1. 获取项目源码
git clone https://github.com/codywon/termius.git
cd termius

# 2. 构建 Release APK
./gradlew assembleRelease

# 编译产物位于: app/build/outputs/apk/release/
```

---

## 许可证与使用条款

本项目采用 **个人学习与交流许可证 (Personal Study and Communication License)** 发布。

- **适用范围**：本项目源码及编译产物仅供个人学习、技术研究、学术交流与非商业环境下的性能评估使用。
- **严禁商业用途**：任何个人或组织不得将本项目的全部或部分代码用于商业盈利、衍生收费软件、商业服务集成、付费二次分发或任何形式的商用闭源产品。
- **版权保留**：所有分发与衍生用途必须完整保留原始版权声明及 `Powered by codywon` 标识。

完整法律声明请参阅 [LICENSE](./LICENSE)。

---

Copyright (c) 2026 **codywon**. Powered by codywon. All rights reserved.
