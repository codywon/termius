# TermX Mobile

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84.svg?style=flat-square&logo=android)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Toolkit](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Release](https://img.shields.io/badge/Release-v2.0.2-success.svg?style=flat-square)](https://github.com/codywon/termius/releases)
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
- **ConnectBot 黄金视口自适应与超小字号高密排版**：终端列数 100% 紧贴屏幕物理宽度自适应排版，告别向右滑动烦恼；支持 6~26 SP 超微型字号与双指捏合无感缩放，一屏尽览海量信息；内置标准 VT100 Pending Wrap 延迟折行状态机。
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
- `TermX-Mobile-v2.0.2-release.apk`

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

## 发布流程（固定流程：本地提交 + GitHub Actions 构建发版）

发版一律走 GitHub Actions，**不在本地构建发布产物、不手动上传 APK**。

### 1. 版本号

修改 `app/build.gradle.kts`：

```kotlin
versionCode = 25          // 每次 +1
versionName = "2.0.22"    // 与即将打的 tag 严格一致
```

> 教训：v2.0.21 曾出现 tag 已打、`versionName` 仍是 `2.0.20` 的错位。tag 与 versionName 必须同步。

### 2. 提交并推送

```bash
# 一个功能/修复一个提交（conventional commits，中文描述）
git add -A && git commit -m "fix(terminal): ... (v2.0.22)"
git push origin master
```

推送到 `master`/`main` 会触发 `.github/workflows/android-build.yml`：跑 `assembleDebug assembleRelease`，
把 Debug/Release APK 作为 artifact 上传（保留 14/30 天），**不创建 Release**。

### 3. 打 tag 正式发版

```bash
git tag -a v2.0.22 -m "本次版本说明第一行
第二行……"
git push origin v2.0.22
```

推送 `v*` tag 会额外触发同一个工作流：

1. 构建 Debug + Release APK → 产出 `TermX-Mobile-vX.Y.Z-release.apk` / `-debug.apk`
2. 用 **tag 注释的前 12 行** + 上一个 tag 以来的 `feat/fix/perf/refactor` 提交自动生成 Release 正文
3. 自动创建 GitHub Release 并附加两个 APK（`softprops/action-gh-release`）

> 所以 **tag 注释就是 Release 正文**，写人话、写清本次改了什么。

### 4. 校验

```bash
gh run list --limit 3          # 看本次构建状态
gh run watch <run-id>          # 实时跟踪
gh release view v2.0.22        # 确认 Release 与 APK 附件
```

### 备注（踩过的坑，别再踩）

- **tag 必须是 annotated tag**：`git tag -a`（不是 `git tag v1.2.3`）。Release 正文取自 tag 注释，
  轻量 tag 没有注释，正文会退化成 commit 标题。
- **checkout 默认拿不到 tag 对象**：tag 事件下 `actions/checkout` 按 SHA 拉取，本地只剩指向 commit 的
  轻量 tag，`%(contents)` 会退化成 commit message。工作流已显式 `git fetch` tag ref 并用
  `git cat-file tag` 取注释；同时 `fetch-depth: 0` 保证 `上一个 tag..当前 tag` 的改动区间可用。
- **同一个 tag 重复触发不会刷新正文**：`softprops/action-gh-release` 遇到"该 tag 已有 release"时，
  会新建一个 draft 再丢弃它并沿用旧 release，正文保持旧值。要修正文用
  `gh release edit vX.Y.Z --notes-file <文件>`；正常的一次性发版不受影响。
- Release 签名使用仓库内 `app/termx_release.jks`（密码在 `app/build.gradle.kts` 的 `signingConfigs`）。
  仓库若转为公开需先评估该密钥的处置方式。
- 工作流当前会提示 `actions/checkout@v4` 等动作的 Node 20 弃用告警，属上游迁移提醒，不影响构建。

---

## 许可证与使用条款

本项目采用 **个人学习与交流许可证 (Personal Study and Communication License)** 发布。

- **适用范围**：本项目源码及编译产物仅供个人学习、技术研究、学术交流与非商业环境下的性能评估使用。
- **严禁商业用途**：任何个人或组织不得将本项目的全部或部分代码用于商业盈利、衍生收费软件、商业服务集成、付费二次分发或任何形式的商用闭源产品。
- **版权保留**：所有分发与衍生用途必须完整保留原始版权声明及 `Powered by codywon` 标识。

完整法律声明请参阅 [LICENSE](./LICENSE)。

---

Copyright (c) 2026 **codywon**. Powered by codywon. All rights reserved.
