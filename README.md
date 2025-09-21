# PapiWebAPI

一个功能强大的 Minecraft Paper 服务器插件，用于将 PlaceholderAPI 变量暴露为 Web API 接口。

## 📋 功能特点

- **将 PAPI 变量转为 Web API**：轻松访问服务器中的任何 PlaceholderAPI 变量
- **自定义 API 端点**：为每个 PAPI 变量创建自定义别名（例如 `/前缀` 代替 `/luckperms_prefix`）
- **灵活的数据访问**：
    - 获取所有在线玩家的变量值
    - 获取特定玩家的变量值
    - 支持按数值或字母排序
    - 支持结果数量限制
- **强大的安全特性**：
    - 令牌鉴权，防止未授权访问
    - 详细的访问日志，记录每次请求
    - 地理位置跟踪，了解访问来源
- **实时配置**：支持热重载配置，无需重启服务器

## 🔧 安装要求

- Minecraft Paper 服务器 (1.16+)
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) 插件

## ⚙️ 安装步骤

1. 下载最新版本的 PapiWebAPI.jar
2. 将插件文件放入服务器的 `plugins` 文件夹
3. 重启服务器或使用插件管理器加载插件
4. 配置插件（详见下方配置说明）

## 🛠️ 基础配置

插件安装后，将生成以下配置文件：

### config.yml
```yaml
# PapiWebAPI 配置

server:
  host: "0.0.0.0"  # 监听地址，0.0.0.0表示所有网络接口
  port: 8088       # 监听端口

# 安全设置
security:
  # API鉴权设置
  authentication:
    # 是否启用API鉴权
    enabled: true
  
    # 访问令牌，用于验证API请求
    # 格式：http://server:port/token=YOUR_TOKEN/endpoint
    token: "your_token"
  
    # 令牌参数名称（默认为"token"）
    parameter: "token"
```

## 📝 命令指南

| 命令 | 描述 | 权限 |
|------|------|------|
| `/papiwebapi create <变量> [别名]` | 创建API端点 | papiwebapi.admin |
| `/papiwebapi delete <端点或变量>` | 删除API端点 | papiwebapi.admin |
| `/papiwebapi list` | 列出所有注册的API端点 | papiwebapi.admin |
| `/papiwebapi reload` | 重载配置和API端点 | papiwebapi.admin |
| `/papiwebapi logs [页码]` | 查看API访问日志 | papiwebapi.logs |

> 插件指令别名：`/pwapi`

## 🚀 使用示例

### 1. 创建API端点

```
/pwapi create %player_level% level
```
这将创建一个新的API端点，可以通过 `/level` 访问玩家等级变量。

```
/pwapi create %luckperms_prefix% 前缀
```
这将创建一个新的API端点，可以通过 `/前缀` 访问玩家前缀变量。

### 2. 访问API

#### 基本访问（所有在线玩家）
```
http://yourserver:8088/token=YOUR_TOKEN/level
```

#### 特定玩家访问
```
http://yourserver:8088/token=YOUR_TOKEN/level/Steve
```

#### 排序功能（从高到低，限制10名玩家）
```
http://yourserver:8088/token=YOUR_TOKEN/level/sort/htl/10
```

#### 字母排序（A-Z）
```
http://yourserver:8088/token=YOUR_TOKEN/前缀/sort/atz
```

## 🔍 访问参数说明

| 参数格式 | 描述 | 示例 |
|---------|------|------|
| `/<端点>` | 获取所有在线玩家的值 | `/level` |
| `/<端点>/<玩家名>` | 获取特定玩家的值 | `/level/Steve` |
| `/<端点>/sort/htl[/<数量>]` | 从高到低排序 | `/level/sort/htl/10` |
| `/<端点>/sort/lth[/<数量>]` | 从低到高排序 | `/level/sort/lth` |
| `/<端点>/sort/atz` | 按名称从A到Z排序 | `/level/sort/atz` |
| `/<端点>/sort/zta` | 按名称从Z到A排序 | `/level/sort/zta` |

## 📊 日志系统

插件会在 `plugins/PapiWebAPI/logs.yml` 中记录所有API访问，包括：
- 访问时间
- 访问IP
- 地理位置
- 访问路径
- 鉴权状态
- 响应内容

日志示例：
```
[2024/8/20 16:40] 127.0.0.1 本地网络 访问了http://server:8088/token=123132/player_name/Steve 鉴权正确 返回Steve
```

可以使用 `/pwapi logs [页码]` 在游戏内查看日志。

## 🔒 安全建议

1. **修改默认令牌**：使用强随机令牌替换默认令牌
2. **限制端口访问**：使用防火墙限制只有特定IP可以访问API端口
3. **定期检查日志**：监控API访问日志，查找可疑活动
4. **使用HTTPS**：考虑在API前方设置反向代理（如Nginx）提供HTTPS支持

## ❓ 常见问题

**Q: 如何修改API监听端口？**
A: 在 `config.yml` 中修改 `server.port` 值，然后使用 `/pwapi reload` 重载配置。

**Q: 如何禁用鉴权？**
A: 将 `config.yml` 中的 `security.authentication.enabled` 设置为 `false`。

**Q: 为什么我的API返回"Player not found"？**
A: 玩家可能不在线或名称拼写错误。某些PAPI变量只能用于在线玩家。

**Q: 如何获得更详细的地理位置信息？**
A: 插件默认使用免费的IP地理位置服务。如需更精确的结果，可联系开发者获取高级版本。

## 📄 权限节点

- `papiwebapi.admin` - 允许管理API端点和重载配置
- `papiwebapi.logs` - 允许查看API访问日志

## 🔄 版本更新

### v1.0.0
- 初始发布版本
- 支持创建和管理API端点
- 支持排序和玩家指定查询
- 支持令牌鉴权
- 详细访问日志系统

## 📜 许可证

此插件基于 MIT 许可证发布。详情请参阅 LICENSE 文件。

## 🌟 支持与贡献

如果您遇到问题或有改进建议，请通过以下方式联系：

- 提交 GitHub Issue
- 发送邮件至：minecraftxy@163.com

感谢您使用 PapiWebAPI！希望它能为您的服务器提供便捷的数据访问解决方案。