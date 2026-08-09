<div align="center">
  <img src="https://xuanjian.top/icon.png" width="200" height="200" alt="玄剑公会 Logo">
</div>

<div align="center">

# 玄剑公会官网联动模组（xuanjianmod）

Minecraft Fabric 模组：打通游戏与官网 xuanjian.top，游戏内完成签到、任务、贡献点管理

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x%20%7C%2026.x-green?style=flat-square)
![Fabric](https://img.shields.io/badge/Fabric-Loader-dbd0b4?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)

**官网**: https://xuanjian.top

</div>

---

## 项目说明

xuanjianmod 是「我的世界玄剑公会」官网 xuanjian.top 的联动模组。模组安装在公会游戏服务器上，玩家在游戏内即可完成官网的核心操作：登录自动签到、任务接取与完成、贡献点查询与转账、贡献点申报、在线玩家查看等；官网更新的日报、决策也会实时同步到游戏内。

- 采用 Fabric 模组加载器，支持双版本线（分支策略）：
  - `1.21` 分支：Minecraft 1.21.x（Java 21 + Mojang 官方映射）
  - `26` 分支：Minecraft 26.x（Java 25 + Mojang 官方映射）
- 无第三方运行时依赖，仅使用 JDK 内置 `java.net.http` 与 `com.google.gson`（随 Minecraft 提供）
- 构建产物由 GitHub Actions 自动构建发布

## 功能总览

| 编号 | 功能 | 指令 |
|:---:|:---|:---|
| 1 | 登录后自动签到官网 | 自动 |
| 2 | 游戏内 UUID 与官网账号绑定 | `/xj bind <官网账号>` |
| 3 | 查看指定服务器的在线玩家 | `/xj online` |
| 4 | 官方任务与玩家任务列表/接取/完成 | `/xj task list \| accept <id> \| verify <验证码>` |
| 5 | 官网日报、决策更新游戏内同步广播 | 自动 |
| 6 | 贡献点余额查询与转账（二次确认） | `/xj cb` `/xj cb pay <玩家> <金额>` |
| 7 | 每 30 分钟上报活跃心跳（活跃总人数统计） | 自动 |
| 8 | 游戏内贡献点申报 | `/xj claim <数量> <理由>` |
| 9 | 查看当前在线玩家 | `/xj online` |
| 10 | 新申报审核时游戏内提醒管理员 | 自动 |

## 指令说明

详见 [docs/commands.md](docs/commands.md)。

## 构建

```bash
# 1.21 分支
git checkout 1.21
./gradlew build

# 26 分支
git checkout 26
./gradlew build
```

产物位于 `build/libs/`。GitHub Actions 会在推送 `1.21` / `26` 分支时自动构建并上传 artifact。

## 安装

1. 服务器安装对应版本的 [Fabric Loader](https://fabricmc.net/use/server/) 与 [Fabric API](https://modrinth.com/mod/fabric-api)
2. 将构建出的 `xuanjianmod-*.jar` 放入 `mods/` 目录
3. 启动服务器，编辑 `config/xuanjianmod.properties` 填写官网 API 地址与服务器密钥
4. 重启服务器完成安装

## 配置

配置文件：`config/xuanjianmod.properties`（首次启动自动生成）

```properties
# 官网 API 地址（一般无需修改）
api.base=https://xuanjian.top
# 服务器唯一标识（官网管理后台生成，用于在线玩家上报与管理员提醒）
server.key=
# 服务器公网 IP（官网后台指定，用于玩家查看在线状态）
server.ip=
# 日报/决策同步检查间隔（秒）
sync.interval=60
# 活跃心跳上报间隔（秒）
heartbeat.interval=1800
```

## 开发团队

北域工作室 Northland Studio

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

<div align="center">

**我的世界玄剑公会** - 官方网站

*由 北域工作室 Northland Studio 出品*

</div>
