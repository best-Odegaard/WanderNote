# WanderNote 行笺

> 对话即行程 —— 用多轮对话把「想去哪儿」变成一份可以直接出发的行程单。

**WanderNote（行笺）** 是一个 AI 智能文旅行程规划平台。用户只需在对话中说出出发地、目的地、天数、预算和兴趣偏好，系统即可生成包含**时间安排、景点门票、开放时间、具体地址**的完整逐日行程；行程可保存、编辑、分享，也能从游记链接一键导入。项目同时提供 H5 / 微信小程序 / App 三端用户应用、独立的运营管理后台，以及一个可独立部署的 Python AI Agent 服务。

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Vue](https://img.shields.io/badge/Vue-3.4-42b883.svg)](https://vuejs.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![UniApp](https://img.shields.io/badge/UniApp-3.0-2B9939.svg)](https://uniapp.dcloud.net.cn/)
[![FastAPI](https://img.shields.io/badge/FastAPI-Python-009688.svg)](https://fastapi.tiangolo.com/)

---

## 目录

- [项目简介](#项目简介)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [AI 行程生成链路](#ai-行程生成链路)
- [目录结构](#目录结构)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [部署](#部署)
- [项目规模](#项目规模)
- [路线图](#路线图)
- [License](#license)

---

## 项目简介

传统行程规划工具要求用户填写冗长的表单，而真实的旅行决策是「聊出来」的：先定个大概，再补充偏好，最后确认细节。WanderNote 把这条链路做成了多轮对话 ——

1. 用户说「想去肇庆玩三天，喜欢自然风光，预算 2000」；
2. AI 反问并确认偏好，生成**行程骨架**（每天去哪些景点，秒级返回）；
3. 用户确认或继续调整，AI 再基于骨架**细化为完整详情**（时间段、门票、开放时间、地址）；
4. 行程保存进「我的行程」，可继续编辑、分享，或发布到社区。

后端与 AI Agent 完全解耦：Agent 是一个独立的 HTTP 服务，模型、Prompt、检索策略都可以单独替换而不影响业务系统。

---

## 功能特性

### 🤖 AI 行程规划

| 特性 | 说明 |
|---|---|
| 多轮对话规划 | 边聊边定，支持刷新页面后恢复上下文，而非一次性表单 |
| 两阶段生成 | 先 `frame` 出骨架（轻量、秒级），再 `detail` 细化详情，兼顾速度与完整度 |
| 结构化输出 | 用 Pydantic 模型约束大模型输出，行程 JSON 直接进业务链路，不做脆弱的文本解析 |
| 异步任务 + 进度 | 提交即返回 `taskId`，前端轮询阶段进度（`retrieving → generating_frame → generating_detail → done`），支持中途取消 |
| 结果缓存秒开 | 相同「城市 + 天数 + 偏好 + 预算」的行程写入 Redis，二次提交直接命中 |
| 知识库增强 | 目的地景点知识库 + 小红书笔记知识库检索后注入 Prompt，压低幻觉 |
| 链接智能导入 | 粘贴游记链接，自动识别目的地城市并生成对应行程草稿 |

### 🗺️ 行程管理

- 行程的创建 / 保存 / 编辑 / 删除 / 列表，按天、按时段组织节点
- 手动增删节点、调整顺序，支持导入的行程二次修改
- 预算统计与可视化（预算环形图）、行程路线地图展示
- 公开 / 私有可见性，分享与点赞计数

### 🏞️ 文旅内容

- 景点库：名称、开放时间、门票、地址、特色标签，支持标签筛选
- 热点活动：citywalk、节庆活动等活动的列表与详情
- 城市与首页 Banner 推荐位

### 👥 旅行社区

- 游记发布 / 详情 / 我的游记
- 点赞、收藏、评论互动

### 🛠️ 运营管理后台

- 数据看板：核心指标与趋势图（ECharts）
- 用户管理、角色与权限管理（自定义 `@RequirePerm` 注解 + 权限拦截）
- 内容管理：景点、城市、活动、首页 Banner、游记审核
- 意见反馈的查看与处理

---

## 技术栈

| 层 | 技术 |
|---|---|
| **用户端** | UniApp 3 · Vue 3.4 · TypeScript 5.4 · Vite 5 · Pinia · SCSS 设计变量体系 |
| **管理端** | Vue 3.4 · TypeScript · Vite 5 · Element Plus · Pinia · Axios · ECharts |
| **后端** | Java · Spring Boot 2.7.3 · MyBatis-Plus 3.5.6 · MySQL 8 · Redis + Redisson · Caffeine · Druid · Knife4j (Swagger) · Spring WebSocket · Hutool · FastJSON · Apache POI · 腾讯云 COS SDK · Lombok · JWT · AOP |
| **AI Agent** | Python 3 · FastAPI · Uvicorn · LangChain (`langchain-openai` / `langchain-core`) · Pydantic v2 · DeepSeek 大模型（OpenAI 兼容协议） |
| **部署** | Nginx · Shell 一键部署脚本 · 完整建表 SQL |

**工程实践**

- Maven 多模块分层：`gkv-common`（通用能力）/ `gkv-pojo`（Entity·DTO·VO·注解）/ `gkv-server`（业务）
- 自定义注解 + AOP：公共字段自动填充（`@AutoFill`）、权限校验（`@RequirePerm`）、接口限流（`@RateLimit`）
- JWT 双端鉴权：用户端与管理端独立密钥、独立拦截器
- 全局异常处理、统一 `Result` 响应封装、PageHelper 分页
- 线程池任务管理 + `ConcurrentHashMap` 任务表 + `@PreDestroy` 优雅关闭

---

## 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│  用户端  UniApp + Vue3 + TypeScript + Vite + Pinia             │
│  H5 / 微信小程序 / App 三端同构，22 个页面，自定义 TabBar        │
└───────────────────────────┬──────────────────────────────────┘
                            │ REST / JWT
┌───────────────────────────▼──────────────────────────────────┐
│  管理端  Vue3 + Element Plus + ECharts                         │
│  看板 · 用户 · 角色权限 · 内容审核 · 反馈处理                   │
└───────────────────────────┬──────────────────────────────────┘
                            │ REST / JWT
┌───────────────────────────▼──────────────────────────────────┐
│  后端  Spring Boot 2.7（Maven 三模块）                          │
│  MyBatis-Plus · MySQL 8 · Redis · Caffeine · WebSocket         │
│  JWT 双端鉴权 · AOP · 全局异常 · 限流 · 腾讯云 COS              │
└───────────────────────────┬──────────────────────────────────┘
                            │ HTTP（/api/chat 流式、/api/plan 三模式）
┌───────────────────────────▼──────────────────────────────────┐
│  AI Agent  Python + FastAPI :8002                              │
│  LangChain · DeepSeek · Pydantic 结构化输出 · 知识库检索(RAG)    │
└──────────────────────────────────────────────────────────────┘
```

---

## AI 行程生成链路

这是整个项目最核心的部分，也是投入最多工程优化的地方。

### 1. 对话阶段（流式）

```
前端 ──POST /travel/chat──► Java 后端 ──POST /api/chat──► Python Agent
                                                              │
        ◄──────── 流式纯文本（逐字返回） ◄────────────────────┘
```

后端用 `RestTemplate.execute` 流式读取 Agent 的 `StreamingResponse`，并把完整的问答落库到 `chat_history` 表，实现多轮上下文持久化。前端刷新页面后调用 `GET /travel/history?sessionId=` 即可恢复对话。

### 2. 生成阶段（异步两阶段）

浏览器对单次请求有超时限制，而大模型生成长行程可能耗时一分多钟。为此采用**异步任务 + 两阶段生成**：

```
POST /travel/generatePlan
   │
   ├─ 命中 Redis 缓存 ──► 直接返回完整行程（fromCache=true，秒开）
   │
   └─ 未命中 ──► 建任务，返回 taskId，后台线程池执行：
                    │
                    ├─ 阶段一 frame ：只生成「每天去哪几个景点」的骨架（输出小 → 快）
                    │     前端轮询 /travel/plan/status/{taskId}，先渲染骨架给用户看
                    │
                    └─ 阶段二 detail：基于已确认的骨架细化时间/门票/地址
                          完成后写 Redis 缓存，状态置 DONE
```

- **放弃长连接，改用轮询**：前端每 2.5s 查询任务状态，可展示「检索景点 → 生成框架 → 生成详情」的分步进度，避免用户面对空白页等待
- **可取消**：`POST /travel/plan/cancel/{taskId}` 中断任务
- **结构化输出**：`llm.with_structured_output(TripPlan)`，由 Pydantic 模型（`TripPlan / Day / Attraction`）约束输出 schema，杜绝 JSON 解析失败
- **知识库注入**：`AttractionKB.query_by_city_tag()` 按城市 + 偏好标签检索景点，`XhsNoteKB.search_note()` 检索笔记内容，一并拼进 System Prompt；未收录的城市返回空列表，由 Prompt 引导模型基于常识规划而非编造

---

## 目录结构

```
.
├── frontend/                    # 用户端 UniApp（Vue3 + TS + Vite）
│   ├── src/pages/               # home / trip / ai-chat / plan-wizard / community / scenic / profile
│   ├── src/components/          # TripMap / BudgetChart / ScenicCard / AppTabBar ...
│   ├── src/api/                 # 接口封装 + Mock 实现
│   ├── src/utils/               # 地图、地理编码、Markdown、格式化等工具
│   ├── UI设计图/                # HTML 高保真原型
│   └── 接口文档/
├── admin-web/                   # 运营管理后台（Vue3 + TS + Element Plus）
│   └── src/views/               # dashboard / user / role / scenic / city / activity / banner / journal / feedback
├── backend/                     # Spring Boot 多模块后端（Maven）
│   ├── gkv-common/              # 常量、异常、JWT 工具、Result 封装、HTTP 工具
│   ├── gkv-pojo/                # Entity / DTO / VO / 自定义注解
│   └── gkv-server/              # Controller / Service / Mapper / Config / Interceptor / Aspect
├── travel_self_agent/           # Python AI Agent（FastAPI，默认 :8002）
│   ├── agent/                   # TravelAgent 链路 + Prompt 模板
│   ├── knowledge_base/          # 景点知识库、小红书笔记知识库
│   ├── model/                   # Pydantic 结构化输出模型
│   └── config/                  # 模型名、Prompt、知识库配置
├── 部署表结构/                   # 建表 SQL
├── deploy/                      # 部署脚本与 Nginx 配置
└── docs/                        # 项目文档
```

---

## 快速开始

### 前置要求

| 依赖 | 版本 |
|---|---|
| JDK | 8 或以上 |
| Maven | 3.6+ |
| Node.js | 18+ |
| Python | 3.10+ |
| MySQL | 8.0 |
| Redis | 5+ |

### 1. 数据库

```bash
mysql -uroot -p -e "CREATE DATABASE wander_note DEFAULT CHARSET utf8mb4;"
mysql -uroot -p wander_note < 部署表结构/user.sql
mysql -uroot -p wander_note < 部署表结构/chat_history.sql
mysql -uroot -p wander_note < 部署表结构/travel_schedule.sql
mysql -uroot -p wander_note < 部署表结构/trip_plan_geek012.sql
```

### 2. 后端（:8080）

```bash
# 复制配置模板并填入你自己的数据库 / Redis / 对象存储信息
cp backend/gkv-server/src/main/resources/application-dev.example.yml \
   backend/gkv-server/src/main/resources/application-dev.yml

cd backend && mvn clean package -DskipTests
java -jar gkv-server/target/gkv-server-1.0-SNAPSHOT.jar
```

接口文档（Knife4j）：http://localhost:8080/doc.html

### 3. AI Agent（:8002）

```bash
cd travel_self_agent
pip install fastapi uvicorn langchain langchain-openai pydantic pyyaml

# 大模型 Key 只从环境变量读取，不要写进代码
export MAAS_API_KEY=sk-your-own-key        # Linux / macOS
# set MAAS_API_KEY=sk-your-own-key         # Windows CMD
# $env:MAAS_API_KEY="sk-your-own-key"      # Windows PowerShell

python main.py
```

Agent 默认监听 `0.0.0.0:8002`，暴露两个接口：

| 接口 | 说明 |
|---|---|
| `POST /api/chat` | 多轮对话，流式返回纯文本 |
| `POST /api/plan?mode=frame\|detail\|full` | 生成行程骨架 / 详情 / 完整行程 |

### 4. 用户端

```bash
cd frontend
npm install

# 配置你自己的腾讯地图 Key（见「配置说明」）
cp .env.example .env

npm run dev:h5          # H5 调试
npm run dev:mp-weixin   # 微信小程序
npm run dev:app         # App（或用 HBuilderX 导入本目录后运行）
```

### 5. 管理端

```bash
cd admin-web
npm install
npm run dev
```

---

## 配置说明

本项目**不含任何可用的密钥**，以下配置需要你自行申请并填入。

### 后端 `application-dev.yml`

由 `application-dev.example.yml` 模板复制而来，需要填写：

| 配置项 | 用途 |
|---|---|
| `sky.datasource.*` | MySQL 连接（host / port / database / username / password） |
| `sky.redis.*` | Redis 连接 |
| `sky.cos.*` | 腾讯云 COS —— 图片与文件上传（SecretId / SecretKey / BucketName / Region） |

### Agent 环境变量

| 变量 | 说明 |
|---|---|
| `MAAS_API_KEY` | 大模型 API Key（阿里云百炼兼容通道 / 任意 OpenAI 兼容服务） |

模型名称在 `travel_self_agent/config/agent.yml` 中配置；若使用其他厂商，同步修改 `agent/travel_agent.py` 中的 `base_url` 即可。

### 前端地图 Key

1. 复制模板并填入自己的 **腾讯位置服务 Key**（申请地址：https://lbs.qq.com/dev/console/key/manage）：

   ```bash
   cd frontend
   cp .env.example .env            # 本地开发
   # 生产构建参考 .env.production
   ```

2. 小程序 / App 端的地图组件 Key 还需填入 `frontend/src/manifest.json` 的 `h5.sdkConfigs.maps.qqmap.key` 字段。

默认使用同一把 Key 同时承担 JS API 渲染与 WebService 地理编码；个人账号下 WebService 会被腾讯拒绝，代码会自动降级走 JS API Geocoder。

> 🔐 **安全提示**：本仓库已移除所有真实密钥。`.env` 与 `application-dev.yml` 已被 `.gitignore` 忽略，请勿将真实密钥提交到仓库；腾讯地图 Key 属客户端 Key，建议在控制台配置域名白名单。

### 管理端

`admin-web/.env.development`（走 Vite 代理）与 `.env.production`（同源部署）默认均为空值，按需填写 `VITE_API_BASE_URL`。

---

## 部署

`deploy/` 下提供三个脚本，服务器地址、SSH 用户与密钥路径均通过环境变量注入，使用前请先配置：

```bash
export SSH_HOST=your-server-ip
export SSH_USER=ubuntu
export SSH_KEY=~/.ssh/your-key.pem
```

```bash
./deploy/deploy.sh            # 用户端 + 后端：自动检测改动并增量部署
./deploy/deploy_admin.sh      # 管理端 + 后端 + Nginx
```

Nginx 配置见 `deploy/admin-nginx.conf`：静态托管 + SPA 路由回退 + API 反向代理。详细步骤见 `deploy/部署说明.md`。

---

## 项目规模

| 语言 | 文件数 |
|---|---|
| Java | 189 |
| Vue | 53 |
| TypeScript | 50 |
| Python | 13 |

后端严格分层（Controller / Service / Mapper），DTO / VO / Entity 职责分离；覆盖从数据库设计、接口开发、AI 链路编排、跨端 UI 到 Nginx 部署的完整闭环。

---

## 路线图

- [x] 单城市 AI 行程生成（对话式 + 表单式）
- [x] 两阶段生成与异步任务、结果缓存
- [x] 多轮对话上下文持久化
- [x] 行程管理、链接导入、社区互动
- [x] 运营管理后台
- [ ] AI 语音导览：景点讲解稿生成 + TTS 合成（方案见 `docs/F1语音导览操作方案.md`）
- [ ] 多城市联动与跨城路径优化
- [ ] 行程协作与结伴匹配
- [ ] GPS 到达自动播报讲解

---

## 贡献

欢迎提 Issue 和 PR。提交前请确保：

1. 不提交任何真实密钥、账号、服务器地址等敏感信息；
2. 后端代码保持分层与既有命名风格；
3. 前端改动通过 `npm run type-check`。

---

## License

[MIT](LICENSE)
