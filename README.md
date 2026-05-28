# 荒天享物商城 · 后端

> 基于 Spring Boot 3 构建的电商商城后端，提供商品、订单、用户、商家等核心接口，并集成 AI 购物助手。
> 配套前端仓库：[online-mall](https://github.com/scutmmq/online-mall)

## 技术栈

| 分类 | 技术 |
|------|------|
| 框架 | Spring Boot 3.5 |
| ORM | MyBatis-Plus + PageHelper |
| 数据库 | MySQL 8 |
| 缓存 | Redis + Redisson |
| 对象存储 | 阿里云 OSS |
| HTTP 客户端 | Spring WebFlux WebClient |
| AI 接入 | OpenAI 兼容接口（DeepSeek / ChatGPT 等） |

## 功能模块

| 模块 | 说明 |
|------|------|
| 用户 | 注册、登录（JWT Token）、个人信息、收货地址、收藏、关注 |
| 商品 | 分类管理、商品上下架、搜索、详情、评价 |
| 购物车 | 增删改查 |
| 订单 | 下单、订单列表、订单详情、状态管理 |
| 商家 | 注册店铺、资料管理、库存、发货、退款审核 |
| AI 助手 | 对话会话管理、Agent 工具调用编排、操作草稿确认 |

## AI 助手模块

基于 OpenAI 兼容协议（Chat Completions + Function Calling）实现，支持 DeepSeek、GPT-4o 等模型。

**核心流程：**

```
用户消息 → Agent 编排器 → 模型推理
                              ↓ tool_calls
                         执行商城工具（最多 8 轮）
                              ↓ 无 tool_calls
                         返回自然语言回复
```

**内置工具（10 个）：**

| 工具 | 类型 | 说明 |
|------|------|------|
| `search_products` | 只读 | 商品搜索，含中文同义词模糊兜底 |
| `get_product_detail` | 只读 | 商品详情 |
| `get_my_orders` | 只读 | 查询当前用户订单 |
| `get_my_addresses` | 只读 | 查询收货地址列表 |
| `get_my_merchant` | 只读 | 查询当前用户店铺信息 |
| `draft_create_order` | 草稿 | 生成下单确认草稿（含自家商品拦截） |
| `draft_add_cart_item` | 草稿 | 生成加购确认草稿 |
| `draft_register_merchant` | 草稿 | 生成注册店铺确认草稿 |
| `draft_update_merchant` | 草稿 | 生成修改店铺资料确认草稿 |
| `draft_update_user_profile` | 草稿 | 生成修改用户资料确认草稿 |

## 项目结构

```
src/main/java/com/scutmmq/
├── ai/
│   ├── client/         # OpenAI 兼容 HTTP 客户端
│   ├── config/         # AI 配置（API Key、模型、参数）
│   ├── controller/     # AI 对话 REST 接口
│   ├── entity/         # 会话、消息、草稿实体
│   ├── service/        # 对话服务、编排器、草稿服务
│   ├── skill/          # 系统提示词、工具注册表
│   └── tool/           # 工具接口定义及 10 个实现
├── controller/         # 商城业务接口
├── service/            # 业务逻辑
├── mapper/             # MyBatis Mapper
├── entity/             # 数据库实体
├── dto/ vo/            # 请求/响应对象
├── config/             # Spring 配置
├── interceptor/        # JWT 拦截器
└── utils/              # 工具类
```

## 快速开始

### 前置要求

- JDK 21+
- MySQL 8
- Redis

### 1. 初始化数据库

```bash
mysql -u root -p < src/main/resources/online_mall.sql
```

### 2. 配置环境变量

复制 `env.example` 为 `.env`，填写实际值：

```env
MYSQL_URL=jdbc:mysql://localhost:3306/online_mall?...
MYSQL_USERNAME=root
MYSQL_PASSWORD=your_password
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=1
OSS_ACCESS_KEY_ID=your_key
OSS_ACCESS_KEY_SECRET=your_secret
AI_API_KEY=your_deepseek_or_openai_key
AI_API_URL=https://api.deepseek.com/chat/completions
AI_API_MODEL=deepseek-chat
```

### 3. 启动

```bash
# 开发模式（使用 application-dev.yaml）
./mvnw spring-boot:run
```

服务默认运行在 `http://localhost:8080`。

## Docker 部署

```bash
# 构建镜像
docker build -t online-mall-app .

# 使用 env 文件启动
docker run -d -p 8080:8080 --env-file .env online-mall-app
```

或使用项目根目录的 `run.sh` 一键启动（含 MySQL + Redis 容器编排）：

```bash
bash run.sh
```

## 环境变量说明

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `MYSQL_URL` | 数据库连接串 | — |
| `MYSQL_USERNAME` | 数据库用户名 | — |
| `MYSQL_PASSWORD` | 数据库密码 | — |
| `REDIS_HOST` | Redis 地址 | — |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `REDIS_DATABASE` | Redis 库编号 | `1` |
| `OSS_ACCESS_KEY_ID` | 阿里云 OSS Key | — |
| `OSS_ACCESS_KEY_SECRET` | 阿里云 OSS Secret | — |
| `AI_API_KEY` | AI 模型 API Key | — |
| `AI_API_URL` | AI 接口地址 | `https://api.deepseek.com/chat/completions` |
| `AI_API_MODEL` | 使用的模型名 | `deepseek-chat` |
