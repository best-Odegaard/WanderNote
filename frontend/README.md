# WanderNote 行笺 - 前端

基于 UniApp + Vue3 + TypeScript 的智能文旅行程规划平台移动端。

## 技术栈

- UniApp (Vue3)
- TypeScript
- Pinia
- Sass

## 快速开始

```bash
# 安装依赖
npm install

# H5 开发
npm run dev:h5

# 微信小程序
npm run dev:mp-weixin

# Android APP
npm run dev:app
```

## 项目结构

```
src/
├── api/          # 接口层
├── components/   # 公共组件
├── hooks/        # 组合式函数
├── pages/        # 页面
├── store/        # Pinia 状态管理
├── styles/       # 全局样式
├── utils/        # 工具函数
├── static/       # 静态资源
├── App.vue
├── main.ts
├── pages.json
└── manifest.json
```

## 页面说明（竞品风格）

| 模块 | 路径 | 说明 |
|------|------|------|
| 行程首页 | pages/home/index | 精选专题横滑 + 我的计划卡片 |
| 探索 | pages/discover/index | 景点 / 活动 / 专题 |
| 智能规划 | pages/plan/wizard | 目的地、天数、偏好芯片、智能规划 |
| 智能导入 | pages/plan/import | 粘贴链接/文本、上传识别 |
| 创建菜单 | 底部 + 浮层 | 新建计划 / 智能导入 / 采集识别 |

底部 Tab：**行程** | **+** | **探索**（自定义 TabBar）

## API 配置

后端地址在 `.env` 中配置：

```
VITE_API_BASE_URL=http://localhost:8080
VITE_USE_MOCK=true
```

- `VITE_USE_MOCK=true`（默认）：所有 `src/api/*.ts` 走 `src/api/mock/handlers.ts` 本地数据，**不会发起网络请求**，避免「网络异常」提示
- 后端就绪后设 `VITE_USE_MOCK=false`，将请求真实接口

Token 请求头名称：`Authentication`（与后端一致）

## 说明

- Mock 数据定义：`src/utils/mock.ts`；接口实现：`src/api/mock/handlers.ts`（与真实 REST 路径注释对应）
- TabBar 图标为占位图，可替换 `src/static/tabbar/` 下的 PNG 文件
