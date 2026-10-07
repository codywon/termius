# TermX AI SRE 智能体深度演进与 Pi-Agent 双轨兼容架构设计总结

> **版本追踪**：`v2.0.7` (Code 11) &rarr; `v2.0.8` (Code 12)  
> **核心主题**：软件升级自主执行铁律、假冒套话根除、Pi-Agent 双轨 ReAct 引擎与全模型自适应降级自愈

---

## 一、 问题背景与用户痛点复盘

### 1. 现象复盘
用户在 TermX 移动端 AI Agent 中输入指令：
> *“帮我升级cliproxyapi和cpa manager plus到最新版本”*

- **阶段一（v2.0.6 之前）**：Agent 给出了一句极其敷衍的机械模板回复：
  > *“已接收到您的运维需求。若需要对主机进行状态诊断，建议直接点击下方「🔍 系统全面体检」或输入具体排障指令，我将立刻为您执行。”*  
  完全没有执行任何 SSH 工具调用或排查动作。
- **阶段二（v2.0.7）**：剔除虚假套话后，露出了底层真实状况：界面提示：
  > *“⚠️ 大模型服务本次未返回有效回答或工具调用。请确认您在设置中配置的模型支持当前功能，或检查 API 额度与网络连接后再次重试。”*
- **阶段三（用户关键质问）**：
  > *“为什么我的这个大模型在 Pi Agent（pi-mono）中可以正常工作，而我们的 Agent 也是参考 Pi Agent 进行开发，为什么不支持呢？”*

---

## 二、 深度技术根因剖析（Root Cause Analysis）

通过对比开源社区顶级极简智能体框架 **Pi Agent (`@earendil-works/pi` / `@mariozechner/pi-mono`)** 源码，我们发现此前 TermX 的 Agent 实现存在以下五个深层缺陷：

### 1. 假冒兜底文本制造的“推诿假象”（False Fallback Illusion）
旧版代码中存在硬编码兜底：
```kotlin
if (rawAnswer.isEmpty() && fullAccumulatedReasoning.isBlank()) {
    safeAnswer = "已接收到您的运维需求。若需要对主机进行状态诊断，建议直接点击下方「🔍 系统全面体检」..."
}
```
当大模型因为各种原因（格式不兼容、额度波动、tools 字段排斥）未返回正文时，客户端未如实抛出异常，反而自作主张合成了这句假话，导致用户强烈误解为“AI 故意偷懒推诿”。

### 2. 强依赖原生 `tools` 参数引发的“模型休克”
* **行业现状**：许多开源大模型（如 DeepSeek-R1、Llama 3、部分 Qwen 版本、本地 Ollama）、以及第三方 API 聚合中转站（NewAPI / OneAPI），底层并未完美实现 OpenAI 标准的 Function Calling 协议；
* **休克原因**：旧版 TermX 在请求体中无条件发送 `"tools": [...]` 与 `"tool_choice": "auto"`。许多中转网关在将请求转发给不支持 tools 的模型时，会出现**流式管道阻塞、静默空响应或连接直接 EOF**。

### 3. 流式 Content Block Array 结构解析盲区
* 部分中转网关或将 Claude/Gemini 转换为 OpenAI 协议的代理，其返回的 `choice.delta.content` **不是单纯的 String**，而是 Content Block 数组：
  ```json
  {"delta": {"content": [{"type": "text", "text": "好的，正在帮您排查..."}]}}
  ```
* 在 Android 原生 `JSONObject` 中，使用 `delta.optString("content")` 读取 `JSONArray` 时，Android 会**直接返回空字符串 `""`**！大模型吐出的有效正文被全部当作空白丢弃。

### 4. 工具对象层级的扁平化容错缺失
部分代理返回的 `tool_calls` 是扁平结构（例如直接返回 `{"name": "...", "arguments": "..."}`），缺少外层的 `{"function": {...}}` 嵌套。旧版仅查找 `function` 对象，导致匹配到的工具函数名 `name` 为空，工具调用彻底丢失。

### 5. 提示词（Prompt）中缺少【软件与服务升级】的行为闭环
旧版 Prompt 侧重于常规的“磁盘体检、高负载排查、网络报错”，缺少当用户要求“升级某特定服务或工具”时主动探查进程、查找容器、检索官方更新命令的行动准则。

---

## 三、 生产级架构重构与解决方案（v2.0.8）

我们在 `v2.0.8` 中彻底重构了 `TermXAgentClient.kt`，完整融入了 Pi Agent 的灵魂级兼容架构：

### 1. Pi-Agent 双轨工具派发引擎（Hybrid Dual-Track Tool Dispatching）
不仅支持 OpenAI 原生 Function Calling，还建立了一套透明的文本 ReAct 工具捕获机制：
- **轨 1（原生调用）**：优先通过标准 API `tool_calls` 派发；
- **轨 2（文本 ReAct 兼容）**：系统在 System Prompt 中开放文本块调用协议：
  ````markdown
  ```tool:execute_shell_command
  {"command": "docker ps -a"}
  ```
  或者:
  <tool_call>
  {"name": "execute_shell_command", "arguments": {"command": "docker ps -a"}}
  </tool_call>
  ````
- **客户端拦截器**：客户端通过 `extractTextualToolCalls` 实时正向扫描正文中的代码块与标签，发现后立即转换为内部 Action 执行，完全脱离了对服务端 Function Calling 特性的单点依赖！

### 2. 自适应免 tools 降级自愈机制（Adaptive No-Tools Fallback）
* 当带 `tools` 发起请求时，如果大模型返回了彻底空白（无内容、无思考、无工具），或者返回 400 提示不支持 tools；
* 引擎**立即原地自适应降级自愈**：将 `useNativeTools` 设为 `false`，剥离请求体中的 `tools` 与 `tool_choice` 参数，在同一个 step 内自动以纯文本模式发起重试！
* 大模型在免除 `tools` 干扰后能够顺畅生成正文与文本工具调用，彻底解决中转站与开源模型“空响应”的顽疾！

### 3. 全格式 Content Block 递归提取器（Recursive Content Extractor）
新增 `extractContentText` 方法，智能解包多模态与 Content Block 数组：
```kotlin
private fun extractContentText(jsonObj: JSONObject, key: String): String {
    if (jsonObj.isNull(key)) return ""
    val raw = jsonObj.opt(key) ?: return ""
    return when (raw) {
        is String -> raw
        is JSONArray -> {
            val sb = StringBuilder()
            for (i in 0 until raw.length()) {
                val item = raw.optJSONObject(i)
                if (item != null) {
                    val text = item.optString("text", "")
                    if (text.isNotEmpty()) sb.append(text)
                } else {
                    sb.append(raw.optString(i, ""))
                }
            }
            sb.toString()
        }
        else -> raw.toString()
    }
}
```

### 4. 双向观测回填与角色守卫（Dual Observation Ingestion）
- 原生工具调用结果以 `{"role": "tool", "tool_call_id": ...}` 回填；
- 文本工具调用结果以 `{"role": "user", "content": "【工具执行观测结果 (Tool Observation for $funcName)】: ..."}` 回填，避免某些严格模型对连续 `tool` 角色产生格式校验报错。

### 5. 软件与服务升级执行铁律（Software Upgrade Paradigm）
在 System Prompt 中明确确立应用更新三步闭环：
1. **探测运行形态**：主动执行 `ps aux | grep -i <app>`、`docker ps -a | grep -i <app>`、`systemctl list-unit-files | grep -i <app>`、`which <app>`；
2. **获取升级方案**：Docker 执行 `docker compose pull`，Git 执行 `git pull`，专有开源软件调用 `web_search` 搜索官方升级文档；
3. **执行升级与验证状态**：执行命令后验证进程端口与版本号，输出结构化报告。

---

## 四、 架构对比总览

| 维度 | 旧版实现 (v2.0.6) | 重构后实现 (v2.0.8) |
| :--- | :--- | :--- |
| **工具调用形式** | 仅支持 OpenAI 原生 `tool_calls` | **双轨制**：原生 `tool_calls` + 文本 Markdown/XML 代码块 |
| **不支持 tools 的模型表现** | 直接返回空白或触发假冒推诿套话 | **自适应原地降级**，自动切为免 tools 纯文本模式重试 |
| **流式 Content 格式** | 仅支持单 String，遇 Array 变空白 | **智能解包**：完美支持 String 与 Content Block Array |
| **工具对象层级** | 强依赖 `function` 嵌套对象 | **自适应规范化**：自动兼容扁平与嵌套结构 |
| **升级/维护指令** | 容易陷入纸上谈兵与反问 | **强制行动闭环**：搜进程、搜容器、搜官方更新、执行并检验 |
| **空响应反馈机制** | 自作主张伪造“建议点击系统体检”假话 | **真实客观报错**，并自动重试降级 |

---

## 五、 发布与验证状态

1. **v2.0.7 (`versionCode = 11`)**：
   - 彻底移除了假冒推诿套话；
   - 注入了【软件与服务升级执行铁律】与【未连接主机主动引导】；
   - 实现了流式与全量 JSON 双模解析基础。
2. **v2.0.8 (`versionCode = 12`)**：
   - 完整落地了 Pi-Agent 双轨工具派发引擎与自适应免 tools 降级自愈；
   - 彻底解决了不同大模型、中转代理与反向网关之间的兼容性壁垒；
   - CI/CD 自动化构建全绿，正式发布。
