# Digital Garden Site Server

数字花园个人站点后端 API，基于 Spring Boot 3 构建，提供文章、标签、项目展示、评论审核、点赞与图片上传等能力。

## 技术栈

- Java 21
- Spring Boot 3.2（Web、JPA、Security、Validation）
- MySQL 8
- JWT 认证
- Maven

## 本地开发

### 前置条件

- JDK 21
- Maven 3.9+
- MySQL 8（本地运行，默认 `localhost:3306`）

### 环境变量

复制示例配置并按需修改：

```bash
cp .env.example .env
```

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_HOST` | 数据库主机 | `localhost` |
| `DB_PORT` | 数据库端口 | `3306` |
| `DB_NAME` | 数据库名 | `digital_garden` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | `root` |
| `JWT_SECRET` | JWT 签名密钥（至少 32 字节） | 见 `.env.example` |
| `ADMIN_USERNAME` | 默认管理员用户名 | `harvey` |
| `ADMIN_PASSWORD` | 默认管理员密码 | `admin123456` |
| `UPLOAD_DIR` | 上传文件目录 | `./uploads` |
| `CORS_ORIGINS` | 允许的前端来源（逗号分隔） | `http://localhost:5173` |

本地开发时，请先在 MySQL 中创建 `DB_NAME` 对应的数据库，或在 `.env` 中配置已有库。

### 启动

```bash
mvn spring-boot:run
```

服务默认监听 `http://localhost:8080`。健康检查：

```bash
curl http://localhost:8080/api/health
```

预期返回：`{"code":0,"message":"ok","data":{"status":"UP"}}`

## Docker 部署

### 前置条件

- Docker
- Docker Compose

### 步骤

1. 复制并编辑环境变量：

   ```bash
   cp .env.example .env
   ```

2. 构建并启动后端与 MySQL：

   ```bash
   docker compose --env-file .env up -d --build
   ```

3. 验证服务：

   ```bash
   curl http://localhost:8080/api/health
   ```

4. 查看日志（可选）：

   ```bash
   docker compose logs server --tail 50
   ```

5. 停止服务：

   ```bash
   docker compose down
   ```

数据通过 Docker 卷持久化（`mysql_data`、`uploads_data`），`docker compose down` 后再 `up` 数据仍会保留。

## 默认管理员账号

首次启动时，若数据库中不存在对应用户，会自动创建管理员账号（由 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 决定）。

**请在首次部署后立即修改默认密码与 `JWT_SECRET`，不要使用示例值用于生产环境。**

## API 概览

- 公开接口：`/api/posts/**`、`/api/tags/**`、`/api/projects/**`、`/api/profile`、`/api/comments`（提交）、`/api/likes`、`/api/health`
- 管理接口：`/api/admin/**`、`/api/auth/login`（需 JWT）
- 统一响应格式：`{ "code": 0, "message": "ok", "data": ... }`
