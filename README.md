<div align="center">
  <img src="icon.png" width="200" height="200" alt="玄剑公会 Logo">
</div>

<div align="center">

# 玄剑公会官网联动模组（xuanjianmod）

Minecraft Fabric 客户端模组：打通游戏与官网 xuanjian.top，游戏内完成签到、任务、贡献点管理，支持图形界面

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11%20%7C%2026.2-green?style=flat-square)
![Fabric](https://img.shields.io/badge/Fabric-Loader-dbd0b4?style=flat-square)
![Java](https://img.shields.io/badge/Java-21%20%7C%2025-orange?style=flat-square)
![GUI](https://img.shields.io/badge/GUI-Screen%20%2B%20ClothConfig-004AAD?style=flat-square)
![Build](https://img.shields.io/badge/Build-GitHub%20Actions-2088FF?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)

**官网**: https://xuanjian.top

</div>

---

## 项目介绍

xuanjianmod 是「我的世界玄剑公会」官网 xuanjian.top 的联动模组。模组以**客户端模组**形态运行：只要玩家本机安装了模组，无论进入哪个服务器，游戏内即可使用 `/xj` 指令完成官网的核心操作——账号绑定、自动签到、任务接取与完成、贡献点查询与转账、贡献点申报、在线玩家查看等；官网发布的日报、决策与申报审核通知也会实时同步到游戏内。**服务器无需安装本模组**，彻底摆脱对服务器端的依赖。

- 基于 **Fabric** 模组加载器，支持双版本线（GitHub 分支策略）：
  - `1.21` 分支：Minecraft 1.21.11（Java 21 + Mojang 官方映射）
  - `26` 分支：Minecraft 26.2（Java 25 + Mojang 官方映射）
- 所有网络请求在**后台线程**执行，指令响应流畅、不卡顿游戏主线程
- 图形界面采用**混合方案**：信息面板使用原版 Screen（零依赖），设置页使用 Cloth Config（jar-in-jar 打包，玩家无需单独安装）
- 构建产物由 GitHub Actions 自动构建发布

---

## 功能特性

### 账号绑定

- `/xj bind <官网账号>` 将当前游戏角色与官网账号绑定，官网向账号邮箱发送**确认邮件**，点击链接即完成绑定
- 绑定状态自动与官网同步（`/xj bind status` 实时查询），邮箱确认后游戏内立即生效，无需重启
- 绑定成功后，官网个人档案中的「游戏 ID」自动填写当前游戏内名字

### 每日签到

- 加入任意服务器后**自动完成官网每日签到**（仅已绑定玩家，每天一次），到账贡献点自动显示
- `/xj checkin` 手动签到，与自动签到等效

### 任务系统

- `/xj task list` 查看当前可接取的官方任务
- `/xj task accept <任务ID>` 接取任务
- `/xj task verify <任务ID> <验证码>` 提交完成验证码，任务完成即时发放贡献点
- `/xj task my` 查看已接取任务与状态

### 贡献点经济

- `/xj cb` 查询贡献点余额
- `/xj cb pay <玩家ID> <金额>` 发起转账，`/xj cb confirm` 二次确认、`/xj cb cancel` 取消（120 秒内有效）
- `/xj claim <数量> <理由>` 提交贡献点申报（理由至少 10 个字符），等待管理员审核到账

### 在线玩家

- `/xj online` 查看当前在线的玄剑玩家（含所在服务器，仅统计已绑定官网账号的玩家）
- 在线状态由**客户端上下线上报**驱动：加入服务器自动上报上线、退出自动上报下线、心跳周期续报兜底；官网按「服务器白名单」识别并 60 分钟 TTL 过期兜底，无需服务器密钥

### 游戏内通知

- **日报 / 决策公示同步**：官网发布新日报或决策公示时，已绑定玩家游戏内即时收到提示
- **申报审核提醒**：有新的贡献点申报待审核时，在线的官网管理员游戏内即时收到提醒（仅管理员可见）

### 图形界面

- `/xj gui` 打开**信息面板**：绑定状态、贡献点余额、在线玄剑玩家一目了然，支持刷新
- `/xj settings` 打开**设置页**：官网地址、本服地址、同步 / 心跳间隔图形化配置，保存即生效
- 所有界面数据在后台线程加载，界面操作流畅不卡顿

### 活跃统计

- 已绑定玩家在线期间自动周期上报活跃心跳，参与官网「活跃总人数」统计

---

## 指令一览

| 指令 | 说明 |
|:---|:---|
| `/xj bind <官网账号>` | 绑定官网账号（需邮箱确认） |
| `/xj bind status` | 查看绑定状态（实时同步官网） |
| `/xj checkin` | 手动签到 |
| `/xj task list` | 官方任务列表 |
| `/xj task my` | 我的任务列表 |
| `/xj task accept <任务ID>` | 接取任务 |
| `/xj task verify <任务ID> <验证码>` | 提交任务完成验证码 |
| `/xj cb` | 贡献点余额 |
| `/xj cb pay <玩家ID> <金额>` | 贡献点转账（发起） |
| `/xj cb confirm` | 确认转账 |
| `/xj cb cancel` | 取消转账 |
| `/xj claim <数量> <理由>` | 贡献点申报 |
| `/xj online` | 查看在线玄剑玩家 |
| `/xj gui` | 打开信息面板 |
| `/xj settings` | 打开设置页 |
| `/xj help` | 查看全部指令 |
| `/xj version` | 查看模组版本 |

> 完整指令与玩法说明见 [docs/commands.md](docs/commands.md)。

---

## 安装与使用

### 环境要求

- 对应版本的 Minecraft（1.21.11 或 26.2）
- 对应版本的 [Fabric Loader](https://fabricmc.net/use/)
- 对应版本的 [Fabric API](https://modrinth.com/mod/fabric-api)

### 客户端安装（推荐）

1. 下载对应游戏版本的模组 jar（GitHub Actions 构建产物 `xuanjianmod-121` / `xuanjianmod-26`）
2. 将 jar 放入 `.minecraft/mods/` 目录
3. 启动游戏，进入任意服务器，输入 `/xj` 即可看到指令补全

> 模组为纯客户端运行，**服务器无需安装**；已绑定玩家的在线状态会自动上报官网。

### 服务端可选安装

在公会自有服务器安装模组后，`/xj` 指令同样可用（服务端命令与客户端命令共用同一套逻辑）。在线状态仍由客户端上报驱动，服务端仅参与通知定向广播。

---

## 配置

配置文件 `config/xuanjianmod.properties`（首次启动自动生成），也可在游戏内通过 `/xj settings` 图形化修改：

```properties
# 官网 API 地址（一般无需修改）
api.base=https://xuanjian.top
# 服务器密钥（已弃用：在线状态改由客户端上下线上报，无需密钥，可留空）
server.key=
# 本服地址（可选）：填写后 /xj online 优先查询本服在线玩家；留空则查询全网玄剑玩家
server.ip=
# 日报/决策同步检查间隔（秒，最小 30）
sync.interval=60
# 活跃心跳上报间隔（秒，最小 30）
heartbeat.interval=1800
```

---

## 在线统计原理

1. 玩家（已绑定官网账号）加入任意服务器，模组读取服务器地址并上报 `/api/mod/online/join`
2. 官网校验绑定关系，并按「服务器白名单」（官网管理后台配置的 `mod_servers.server_ip`，忽略端口比较）判定归属服务器
3. 玩家退出时上报 `/api/mod/online/leave`；崩溃 / 断网场景由心跳周期续报兜底
4. 在线记录 60 分钟 TTL 过期自动清理，`/xj online` 与官网在线列表仅返回有效记录

> 该方案彻底移除了服务器密钥，任何服务器上的已绑定玩家都能参与在线统计。

---

## 双版本支持

| 分支 | Minecraft | Java | 映射 | Loom |
|:---|:---|:---|:---|:---|
| `1.21` | 1.21.11 | 21 | Mojang 官方映射 | fabric-loom-remap |
| `26` | 26.2 | 25 | Mojang 官方映射 | fabric-loom |

两分支共用同一套业务逻辑，仅在客户端 API 上做版本适配：

- 1.21.11 客户端消息使用 `displayClientMessage`，ServerData 地址字段为 `ip`
- 26.2 使用 `sendSystemMessage`，渲染入口为 `extractRenderState(GuiGraphicsExtractor)`，界面切换使用 `minecraft.gui.setScreen`

---

## 构建

### GitHub Actions（推荐）

推送 `1.21` / `26` 分支自动触发对应工作流，构建产物上传为 artifact：

- [build-121.yml](.github/workflows/build-121.yml)（JDK 21 + Gradle 9.5.1）
- [build-26.yml](.github/workflows/build-26.yml)（JDK 25 + Gradle 9.5.1）

### 本地构建

```bash
# 1.21 分支
git checkout 1.21
gradle build

# 26 分支
git checkout 26
gradle build
```

产物位于 `build/libs/`。

---

## 项目结构

```
xuanjian mod/
├── src/
│   ├── main/java/top/xuanjian/guild/
│   │   ├── XuanjianMod.java           # 主入口（服务端侧）
│   │   ├── bind/BindManager.java      # 绑定管理（本地缓存 + 官网同步）
│   │   ├── checkin/CheckinManager.java# 每日签到
│   │   ├── task/TaskManager.java      # 任务接取 / 完成
│   │   ├── economy/                   # 贡献点：余额 / 转账 / 申报
│   │   ├── network/ApiClient.java     # 官网 HTTP 客户端（JDK HttpClient）
│   │   ├── command/                   # 双端命令抽象与实现
│   │   ├── config/ModConfig.java      # 配置文件读写
│   │   ├── sync/                      # 日报 / 决策 / 申报提醒同步
│   │   └── online/OnlineManager.java  # 在线查询
│   └── client/java/top/xuanjian/guild/
│       ├── XuanjianModClient.java     # 客户端入口（命令注册 / 上下线 / 心跳 / 同步）
│       ├── command/ClientCommandActor.java
│       └── gui/                       # 信息面板 + 设置页
├── docs/commands.md                   # 指令说明书
├── build.gradle / gradle.properties   # 构建配置
├── .github/workflows/                 # 双版本 CI
└── LICENSE
```

---

## 技术栈

| 项目 | 技术 |
|:---|:---|
| 模组加载器 | Fabric Loader |
| 版本映射 | Mojang 官方映射（双版本线） |
| 网络 | JDK 内置 `java.net.http`（无第三方依赖） |
| JSON | `com.google.gson`（随 Minecraft 提供） |
| 设置页 | Cloth Config（jar-in-jar 打包） |
| 信息面板 | 原版 Screen（`GuiGraphicsExtractor`） |
| CI | GitHub Actions（Gradle 9.5.1） |

---

## 更新日志

### v0.1.0（当前）

- **客户端模组化**：`/xj` 指令注册到客户端命令树，输入即补全，不依赖服务器安装模组
- **命令异步化**：所有网络请求后台线程执行，指令响应流畅不卡顿
- **图形界面**：`/xj gui` 信息面板（绑定 / 余额 / 在线）+ `/xj settings` 设置页（Cloth Config）
- **在线状态重构**：客户端上下线上报 + 官网服务器白名单 + TTL 过期兜底，彻底移除服务器密钥
- **绑定实时同步**：邮箱确认后游戏内自动识别已绑定
- **双版本**：支持 Minecraft 26.2（Java 25）与 1.21.11（Java 21）

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

<div align="center">

**我的世界玄剑公会** - 官方网站

*由 北域工作室 Northland Studio 出品*

</div>
