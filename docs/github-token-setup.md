# GitHub Token 获取和配置指南

## 📋 概述

GitHub Personal Access Token (PAT) 用于提高 API 限流容量：
- **未认证**：60 次/小时
- **使用 Token**：5,000 次/小时（REST API）或 5,000 点/小时（GraphQL API）

---

## 🔑 获取 GitHub Token

### 方法 1：通过 GitHub 网站创建（推荐）

#### 步骤 1：登录 GitHub
1. 访问 [GitHub](https://github.com)
2. 登录您的账户

#### 步骤 2：进入开发者设置
1. 点击右上角头像
2. 选择 **Settings**（设置）
3. 在左侧菜单中，滚动到底部
4. 点击 **Developer settings**（开发者设置）

#### 步骤 3：创建 Personal Access Token
1. 在左侧菜单中，点击 **Personal access tokens**
2. 选择 **Tokens (classic)** 或 **Fine-grained tokens**

**推荐使用 Fine-grained tokens（更安全）：**
- 点击 **Generate new token** → **Generate new token (fine-grained)**
- 填写 Token 名称（如：`PolyHermes Announcements API`）
- 设置过期时间（建议：90 天或自定义）
- 选择资源所有者（Repository access）：
  - 如果公告在您的仓库：选择 **Only select repositories**，然后选择 `WrBug/PolyHermes`
  - 如果公告在公共仓库：选择 **Public repositories (read-only)**
- 设置权限（Repository permissions）：
  - **Metadata**: Read（必需）
  - **Contents**: Read（如果需要读取 Issue 内容）
  - **Issues**: Read（必需，用于读取 Issue 和评论）
- 点击 **Generate token**

**或使用 Classic tokens（更简单）：**
- 点击 **Generate new token (classic)**
- 填写 Token 名称（如：`PolyHermes Announcements API`）
- 设置过期时间
- 选择权限（Scopes）：
  - ✅ **public_repo**（读取公共仓库的 Issue 和评论）
  - 如果仓库是私有的，需要选择 **repo**
- 点击 **Generate token**

#### 步骤 4：复制并保存 Token
⚠️ **重要**：Token 只会显示一次，请立即复制并保存到安全的地方！

```
ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

---

### 方法 2：通过 GitHub CLI 创建

如果您安装了 GitHub CLI (`gh`)，可以使用命令行创建：

```bash
# 登录 GitHub CLI
gh auth login

# 创建 Token
gh auth token
```

---

## 🔐 所需权限说明

### Fine-grained Token 权限
- **Metadata**: Read（必需，读取仓库基本信息）
- **Contents**: Read（可选，读取仓库内容）
- **Issues**: Read（必需，读取 Issue 和评论）

### Classic Token 权限
- **public_repo**（公共仓库）
- **repo**（私有仓库，如果需要）

---

## ⚙️ 在项目中使用 Token

### 方式 1：环境变量（推荐）

#### 1. 在配置文件中添加 Token 配置

编辑 `backend/src/main/resources/application.properties`：

```properties
# GitHub 配置（用于公告功能）
github.repo.owner=WrBug
github.repo.name=PolyHermes
github.announcement.issue.number=1
github.token=${GITHUB_TOKEN:}  # 从环境变量读取，如果未设置则为空
```

#### 2. 设置环境变量

**Linux/macOS：**
```bash
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

**Windows (PowerShell)：**
```powershell
$env:GITHUB_TOKEN="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

**Windows (CMD)：**
```cmd
set GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

#### 3. 在 Docker 中使用

在 `docker-compose.yml` 或启动命令中添加：
```yaml
environment:
  - GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

或在启动命令中：
```bash
docker run -e GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx ...
```

---

### 方式 2：直接配置（不推荐，仅用于测试）

⚠️ **不推荐**：Token 会暴露在配置文件中，存在安全风险。

编辑 `backend/src/main/resources/application.properties`：

```properties
github.token=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

---

## 💻 代码实现

### 更新 RetrofitFactory

在 `RetrofitFactory.kt` 中添加 Token 支持：

```kotlin
fun createGitHubApi(): GitHubApi {
    val baseUrl = "https://api.github.com"
    
    // 从配置读取 Token
    val githubToken = githubToken  // 从 @Value 注入
    
    // 添加拦截器
    val githubInterceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val requestBuilder = chain.request().newBuilder()
                .header("Accept", "application/vnd.github+json")
            
            // 如果配置了 Token，添加认证头
            if (githubToken.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $githubToken")
            }
            
            return chain.proceed(requestBuilder.build())
        }
    }
    
    val okHttpClient = createClient()
        .addInterceptor(githubInterceptor)
        .build()
    
    // ... 其余代码
}
```

---

## 🔒 安全注意事项

### 1. Token 存储
- ✅ **推荐**：使用环境变量存储 Token
- ✅ **推荐**：使用密钥管理服务（如 AWS Secrets Manager、HashiCorp Vault）
- ❌ **禁止**：将 Token 提交到 Git 仓库
- ❌ **禁止**：在日志中输出 Token

### 2. Token 权限
- ✅ **最小权限原则**：只授予必要的权限
- ✅ **定期轮换**：建议每 90 天更新一次 Token
- ✅ **监控使用**：定期检查 Token 的使用情况

### 3. 配置文件
- ✅ 将 `application.properties` 添加到 `.gitignore`（如果包含 Token）
- ✅ 使用 `application-local.properties` 存储本地配置
- ✅ 使用环境变量覆盖配置

---

## 🧪 测试 Token

### 使用 curl 测试

```bash
# 测试 REST API
curl -H "Authorization: Bearer YOUR_TOKEN" \
     -H "Accept: application/vnd.github+json" \
     https://api.github.com/repos/WrBug/PolyHermes/issues/1

# 测试 GraphQL API
curl -X POST \
     -H "Authorization: Bearer YOUR_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"query": "query { viewer { login } }"}' \
     https://api.github.com/graphql
```

### 检查限流

响应头中包含限流信息：
```
X-RateLimit-Limit: 5000
X-RateLimit-Remaining: 4999
X-RateLimit-Used: 1
X-RateLimit-Reset: 1701964800
```

---

## 📝 完整配置示例

### application.properties
```properties
# GitHub 配置（用于公告功能）
github.repo.owner=WrBug
github.repo.name=PolyHermes
github.announcement.issue.number=1
github.token=${GITHUB_TOKEN:}  # 从环境变量读取
```

### .env 文件（用于本地开发）
```env
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### docker-compose.yml
```yaml
services:
  backend:
    environment:
      - GITHUB_TOKEN=${GITHUB_TOKEN}
```

---

## 🚨 常见问题

### Q1: Token 过期了怎么办？
**A:** 重新生成新的 Token，更新环境变量或配置文件。

### Q2: Token 泄露了怎么办？
**A:** 立即在 GitHub 设置中删除该 Token，然后生成新 Token。

### Q3: 如何查看 Token 的使用情况？
**A:** 在 GitHub Settings → Developer settings → Personal access tokens 中查看 Token 的最后使用时间。

### Q4: 可以使用 GitHub App 吗？
**A:** 可以，GitHub App 的限流更高（组织应用 10,000 点/小时），但实现更复杂。

### Q5: Token 需要哪些权限？
**A:** 对于公共仓库，只需要 `public_repo` 权限；对于私有仓库，需要 `repo` 权限。

---

## 📚 参考链接

- [GitHub Personal Access Tokens 文档](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token)
- [GitHub API 认证文档](https://docs.github.com/en/rest/authentication/authenticating-to-the-rest-api)
- [GitHub API 限流文档](https://docs.github.com/en/rest/overview/resources-in-the-rest-api#rate-limiting)

---

## ✅ 检查清单

- [ ] 已创建 GitHub Personal Access Token
- [ ] Token 已保存到安全的地方
- [ ] 已在环境变量中配置 Token
- [ ] 已更新 `application.properties` 配置
- [ ] 已更新代码支持 Token 认证
- [ ] 已测试 Token 是否生效
- [ ] 已检查限流是否提升（从 60 → 5,000）
- [ ] 已将 Token 相关配置添加到 `.gitignore`

