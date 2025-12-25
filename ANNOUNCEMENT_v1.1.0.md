# 🎉 v1.1.0 版本更新指南

## 📅 发布日期

2025年12月26日

## 🎯 这次更新对您意味着什么？

### ⚡ 更快的跟单响应速度
- **实时链上监听**：通过 WebSocket 实时监听链上交易，跟单响应速度提升 50% 以上
- **双重保障**：链上监听和轮询同时运行，确保不错过任何交易机会
- **自动去重**：系统自动处理重复数据，确保每笔交易只执行一次

### 💰 更准确的盈亏统计
- **实际成交价追踪**：系统会自动更新卖出订单的实际成交价，而不是下单时的价格
- **自动价格更新**：每 5 秒自动查询并更新订单的实际成交价
- **精确盈亏计算**：基于实际成交价计算盈亏，统计数据更准确

### 🔧 更灵活的系统配置
- **RPC 节点管理**：可以添加、编辑、删除自定义 RPC 节点
- **节点启用/禁用**：可以随时启用或禁用节点，无需删除配置
- **智能节点选择**：系统自动选择可用的节点，禁用的节点会被忽略

### 🐛 问题修复
- **修复卖出订单失败问题**：修复了部分情况下卖出订单因 API 凭证问题导致的失败
- **修复订单精度错误**：修复了卖出订单因精度问题导致的 API 错误

## 📦 如何更新

### 方式一：使用 Docker（推荐）

#### 步骤 1：备份数据（强烈推荐）

**备份不是必须的，但强烈推荐！**

**为什么需要备份？**
- Docker 更新不会删除数据（数据存储在独立的数据卷中）
- 但数据库结构可能会变更（本次更新会添加 `price_updated` 字段）
- 如果迁移失败或出现问题，备份可以帮助恢复数据
- 生产环境建议必须备份，开发环境可以跳过

**如何备份？**

```bash
# 方式 1：使用 mysqldump 备份数据库（推荐）
docker exec polyhermes-mysql mysqldump -u root -p polyhermes > backup_$(date +%Y%m%d_%H%M%S).sql

# 方式 2：备份整个 MySQL 数据卷
docker run --rm -v polyhermes_mysql-data:/data -v $(pwd):/backup alpine tar czf /backup/mysql_backup_$(date +%Y%m%d_%H%M%S).tar.gz /data

# 方式 3：如果已有定期备份，可以跳过此步骤
```

**什么情况下可以跳过备份？**
- ✅ 开发环境或测试环境
- ✅ 数据不重要或可以重新生成
- ✅ 已经有定期自动备份
- ✅ 确定迁移不会失败（查看更新日志确认）

**什么情况下必须备份？**
- ⚠️ 生产环境
- ⚠️ 包含重要交易数据
- ⚠️ 不确定迁移是否安全

#### 步骤 2：停止当前服务

```bash
# 进入部署目录
cd /path/to/polyhermes

# 停止服务
docker-compose -f docker-compose.prod.yml down
```

#### 步骤 3：拉取新版本

```bash
# 拉取 v1.1.0 版本
docker pull wrbug/polyhermes:v1.1.0

# 或者拉取最新版本（推荐）
docker pull wrbug/polyhermes:latest
```

#### 步骤 4：更新配置（如果需要）

如果您想固定使用 v1.1.0 版本，可以编辑 `docker-compose.prod.yml`：

```yaml
services:
  app:
    image: wrbug/polyhermes:v1.1.0  # 修改这里
```

#### 步骤 5：启动服务

```bash
# 启动服务
docker-compose -f docker-compose.prod.yml up -d

# 查看日志，确认服务正常启动
docker-compose -f docker-compose.prod.yml logs -f
```

#### 步骤 6：验证更新

1. 访问系统首页，查看页面标题是否显示 `v1.1.0`
2. 检查系统功能是否正常
3. 查看日志是否有错误信息

### 方式二：一键更新脚本

如果您使用 `latest` 标签，可以使用以下命令一键更新：

```bash
# 进入部署目录
cd /path/to/polyhermes

# 拉取最新镜像并重启
docker-compose -f docker-compose.prod.yml pull
docker-compose -f docker-compose.prod.yml up -d

# 查看日志
docker-compose -f docker-compose.prod.yml logs -f
```

## ⚠️ 更新注意事项

### 1. 数据库迁移
- 本次更新会自动执行数据库迁移（添加 `price_updated` 字段）
- 迁移过程不会影响现有数据，只是添加新字段
- 迁移是安全的，但如果迁移失败，请检查数据库权限和连接
- **如果迁移失败，可以使用备份恢复数据**

### 2. 服务中断时间
- 更新过程中服务会短暂中断（通常 1-2 分钟）
- 建议在低峰期进行更新
- 更新期间正在进行的跟单操作可能会受影响

### 3. 配置检查
- 更新后请检查 RPC 节点配置是否正确
- 如果使用自定义 RPC 节点，请确认节点状态正常

### 4. 功能验证
更新后建议验证以下功能：
- ✅ 跟单功能是否正常
- ✅ 订单价格更新是否正常
- ✅ RPC 节点管理是否正常
- ✅ 盈亏统计是否准确

## 🆕 新功能使用指南

### 1. RPC 节点管理

#### 如何添加自定义 RPC 节点？

1. 登录系统，进入 **系统设置** → **RPC 节点设置**
2. 点击 **添加节点** 按钮
3. 填写节点信息：
   - **节点名称**：给节点起个名字（如：Alchemy Polygon）
   - **RPC URL**：节点的 HTTP/HTTPS 地址
   - **启用状态**：默认启用，可以稍后禁用
4. 点击 **保存**

#### 如何启用/禁用节点？

1. 在 RPC 节点列表中，找到要操作的节点
2. 点击 **启用/禁用** 开关
3. 系统会自动更新节点状态

**提示**：
- 禁用的节点不会被使用，但配置会保留
- 可以随时重新启用禁用的节点
- 建议至少保留一个启用的节点

### 2. 实际成交价追踪

#### 这个功能做什么？

系统会自动追踪卖出订单的实际成交价，而不是使用下单时的价格。这样可以：
- 更准确地计算盈亏
- 了解订单的实际执行情况
- 优化卖出策略

#### 如何查看实际成交价？

1. 进入 **跟单统计** → **卖出订单**
2. 查看订单列表，**卖出价格** 列显示的是实际成交价
3. 如果价格旁边有更新标记，说明价格已从订单详情中获取

**注意**：
- 价格更新是自动的，每 5 秒检查一次
- 如果订单还未成交，价格可能还是下单时的价格
- 部分成交的订单会使用加权平均价格

## 🔍 常见问题

### Q1: 更新后无法启动怎么办？

**A:** 请按以下步骤排查：

1. 检查日志：`docker-compose -f docker-compose.prod.yml logs`
2. 检查数据库连接是否正常
3. 检查端口是否被占用
4. 如果问题持续，可以回退到之前的版本：
   ```bash
   # 修改 docker-compose.prod.yml 中的镜像标签为之前的版本
   # 例如：image: wrbug/polyhermes:v1.0.3
   docker-compose -f docker-compose.prod.yml up -d
   ```

### Q2: 更新后跟单不工作怎么办？

**A:** 请检查：

1. RPC 节点是否正常（进入 RPC 节点设置查看）
2. 跟单配置是否启用
3. 账户 API 凭证是否有效
4. 查看系统日志是否有错误信息

### Q3: 如何回退到之前的版本？

**A:** 按以下步骤操作：

1. 停止当前服务：`docker-compose -f docker-compose.prod.yml down`
2. 修改 `docker-compose.prod.yml` 中的镜像标签为之前的版本
3. 启动服务：`docker-compose -f docker-compose.prod.yml up -d`

**注意**：回退版本时，数据库结构可能不兼容，建议先备份数据库。

### Q4: 更新后数据会丢失吗？

**A:** 不会。更新过程不会删除任何数据。但为了安全起见，建议更新前备份数据库。

### Q5: 如何查看当前版本？

**A:** 有两种方式：

1. **页面查看**：登录系统后，页面标题会显示版本号（如：PolyHermes v1.1.0）
2. **命令行查看**：
   ```bash
   docker inspect polyhermes | grep -i version
   ```

## 📚 相关文档

- [完整更新日志](https://github.com/WrBug/PolyHermes/compare/v1.0.3...v1.1.0)
- [部署文档](docs/zh/DEPLOYMENT.md)
- [版本管理文档](docs/zh/VERSION_MANAGEMENT.md)

## 🔗 获取帮助

如果更新过程中遇到问题：

1. 查看 [GitHub Issues](https://github.com/WrBug/PolyHermes/issues)
2. 查看系统日志：`docker-compose -f docker-compose.prod.yml logs -f`
3. 在 GitHub 上提交 Issue，描述您的问题

## ⚠️ 安全提醒

**请务必使用官方 Docker 镜像源，避免财产损失！**

### ✅ 官方 Docker Hub 镜像

**官方镜像地址**：`wrbug/polyhermes`

```bash
# ✅ 正确：使用官方镜像
docker pull wrbug/polyhermes:v1.1.0

# ❌ 错误：不要使用其他来源的镜像
# 任何非官方来源的镜像都可能包含恶意代码，导致您的私钥和资产被盗
```

### 🔗 官方渠道

请通过以下**唯一官方渠道**获取 PolyHermes：

* **GitHub 仓库**：https://github.com/WrBug/PolyHermes
* **Twitter**：@polyhermes
* **Telegram 群组**：加入群组

---

**⭐ 如果这个项目对您有帮助，请给个 Star 支持一下！**

**💬 如有问题或建议，欢迎在 GitHub Issues 中反馈。**

