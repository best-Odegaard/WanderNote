# 海外 AI 行程规划（AI Trip Planner）产品现状与趋势调研

> 数据截止：2026 年 9 月。所有数字均标注年份与来源；无公开来源者写「未找到公开数据」，不做估算。

## 一、独立 AI 行程规划产品

### 1. Layla.ai（现已被 Expedia 收购）
- **定位/用户**：2023 年柏林创立，创始人 Saad Saeed、Jeremy Jauncey（Beautiful Destinations 创始人）。对话式旅行助手，整合灵感内容 + 机票 + 住宿。投资人含 Booking.com 联合创始人 Andy Phillipps、Skyscanner 联合创始人 Barry Smith、Paris Hilton、美联航创投、百度资本（[环球旅讯, 2026](https://www1.traveldaily.cn/article/190495)）。
- **规模**：累计 3000 万条旅行对话，规划行程交易总价值 > 10 亿美元，约 3 万名付费会员，年费约 50 欧元（2026，[环球旅讯](https://www1.traveldaily.cn/article/190495)）。Expedia 于 2026 年 7 月完成收购，金额未披露（[Skift, 2026](https://skift.com/2026/07/31/expedia-acquired-ai-trip-planner-layla-exclusive/)、[环球旅讯](https://m.traveldaily.cn/article/190478/)）。
- **交互路径**：纯对话式，1 步。四成用户跳过目的地搜索直接用对话发起需求（2026）。**路径最短**。
- **AI/库存**：自然语言行程搜索 + 实时机票酒店方案，可下单；未公开具体模型。混合「AI + 真人顾问」：AI 生成初稿筛客，高客单复杂行程由真人核对成交，顾问有效线索量提升至 5 倍（2026）。曾两天内成交 2.6 万美元 Safari 订单。
- **差异化**：AI+真人顾问混合模式；活跃人群意外地是 40–60 岁蜜月/多国自驾/野生动物长线客群，而非年轻人。
- **差评**：Trustpilot 评分 3.8/5，具体评论内容页面被反爬拦截，未获取（[Trustpilot](https://www.trustpilot.com/review/layla.ai)）。
- **商业**：订阅（50 欧/年）+ 佣金 + 真人顾问三轨（2026）。

### 2. Mindtrip
- **定位**：AI 旅行平台，2024 年 5 月上线，CEO Andy Moss；to C 规划 + B2B 酒店/目的地营销工具（[PhocusWire](https://www.phocuswire.com/mindtrip-ai-trip-planner-travel-startup)）。
- **规模**：累计融资 **2250 万美元**（含 2023 年 700 万种子轮；2025 年 Capital One Ventures、United Airlines Ventures 追加，Amex Ventures、Forerunner、Costanoa 在列）——[United Airlines Ventures, 2025](https://www.unitedairlinesventures.com/news/capital-one-ventures-united-airlines-ventures-invest-in-mindtrip)。用户数未找到公开数据。
- **交互路径**：对话 + 地图点击混合；移动端为「on-trip companion」，用户回答偏好问题后，App 定位并在地图推送周边推荐（[Travolution, 2025](https://www.travolution.com/news/ai-trip-planning-platform-mindtrip-adds-mobile-app-to-mix/)）。
- **AI/库存**：基于 OpenAI 技术栈，多模态（语音输入、音频回复、Magic Camera 拍照识别与菜单翻译）——OpenAI 初创负责人 Marc Manara 站台背书（2025）。知识库 >1000 万个 POI、3 万名本地专家内容（2025）。可把机票/酒店/餐厅预订与门票加入行程，**不是站内直接下单，而是组织与跳转**。
- **差异化**：10M+ POI + 3 万专家结构化内容；2025 年收购创作者行程平台 Thatch；实时地图形态。
- **差评**：未找到公开数据。

### 3. Wanderlog
- **定位**：协作式行程规划 + 地图时间轴，Chrome/移动端。创始人 Peter 与 Harry Yu（前 Stripe、Google），[创业故事访谈](https://startupfounderstories.com/stories/peter-harry-yu-wanderlog-trip-planner)。
- **规模**：Google Play 评分 4.8（2026，[Google Play](https://play.google.com/store/apps/details?id=com.wanderlog.android)）。用户数、融资额**未找到公开数据**（CB Insights 仅收录公司档案；Crustdata/Tracxn 页面均被反爬拦截）。
- **交互路径**：表单/地图点选式为主——先建 trip，再逐个加地点，系统自动排路线与时间轴；AI 用于一键生成初稿。属于「生成 + 手工编辑」范式，非 Agent。
- **库存/下单**：不直接下单，提供酒店/机票跳转与比价入口。
- **差异化**：多人实时协作编辑、地图时间轴、路线优化、离线查看，是「行程文档」而非「预订入口」。
- **商业**：免费 + Wanderlog Pro 订阅；具体价格未获取到权威公开来源，不列数字。

### 4. Sygic Travel / Tripomatic
- **定位**：地图式城市行程规划，老牌（Tripomatic 起步于 2011 前后）。
- **关键事实**：**2024 年 11 月 11 日 Sygic Travel 移动端与 Web 端从 Sygic 品牌剥离并改名 Tripomatic**，账号需迁移，Premium 更名为 Tripomatic Premium（[Tripomatic 官方支持, 2024](https://support.tripomatic.com/rebranding/migrate-sygic-travel-account.html)）。
- **规模**：用户数、融资**未找到公开数据**。
- **交互**：地图点选 + 天级行程编排（典型 3–5 步），非对话式。AI 能力薄弱，本质是 POI 数据库 + 路线工具。
- **差异化**：离线地图、景点库、行程按天排布。

### 5. TripIt（Concur / SAP）
- **定位**：**预订后**行程聚合器，不是规划器。转发确认邮件到 plans@tripit.com 自动生成行程单（[TripIt 官网](https://www.tripit.com/de/node/91)）。
- **规模**：官网称「millions of travelers use TripIt and TripIt Pro」（2026）；具体月活/用户数未找到公开数据。
- **交互**：0 表单，1 次转发邮件；行程自动解析。属「自动导入预订」范式。
- **AI 能力**：弱。核心是邮件解析与规则化提醒，并非 LLM Agent；不卖库存、不下单。
- **差异化**：跨渠道邮件聚合、实时航班提醒、改签备选、座位与登机口导航、碳足迹。**护城河是邮箱与航司数据而非模型。**
- **商业**：TripIt 免费 + **TripIt Pro 49 美元/年**（2026）。
- **差评**：未找到公开数据。

### 6. Roam Around / Trip Planner AI (tripplanner.ai) / Curiosio / Vacay
- **Roam Around**：GPT 驱动的对话式行程生成，Google Play 评分 5.0（2026，[Google Play](https://play.google.com/store/apps/details?id=dev.omnivision.roamaround)）；aitools.xyz 目录评分 4.82（2026）。用户数、融资、商业模式**未找到公开数据**（PitchBook/CB Insights 页面可查但被反爬拦截）。
- **Trip Planner AI**：网页 + iOS（App Store 名称 Routly）。用户数、融资、收入**未找到公开数据**。
- **Curiosio**：算法式路线规划器，强调「数学优化」而非 LLM 对话；[官网 How It Works](https://curiosio.com/how-it-works/) 内容为前端渲染，未能取正文。规模与价格未找到公开数据。
- **Vacay**：对话式旅行助手，[SourceForge 收录](https://sourceforge.net/app/vacay/web-app/)、[Product Hunt](https://www.producthunt.com/p/vacay)。用户与融资未找到公开数据。注意区分同名公司 Vacaay（CB Insights 收录的另一家）。

### 7. GuideGeek（Matador Network）
- **规模**：**超过 100 万消费者用户登录使用**（2025 年 12 月，[Seattle Times/AP, 2025](https://www.seattletimes.com/life/food-drink/ai-is-the-next-frontier-of-travel-for-2026/)）。
- **路径**：免费聊天入口（WhatsApp/网页），对话式，1 步。
- **商业**：B2B2C——向目的地旅游局/品牌售卖部署与流量。新西兰旅游局结合 Minecraft 的 AI 工具获 **20 万+ 独立访客**（2025）。**是目前唯一有公开用户量级的独立 AI 旅行助手。**
- **差评**：未找到公开数据。

## 二、大厂与 OTA

### 8. Google（Search AI Mode / Canvas / Flight Deals）
- **能力**：2025 年 11 月 17 日，Google 把 Gemini Canvas 扩到旅行——Canvas 侧栏调**实时航班/酒店价格数据**、Google Maps 的照片与评论、餐厅信息，按通勤时间优化，可反复追问与编辑，支持 AI Mode 历史回溯（[Android Authority, 2025](https://www.androidauthority.com/google-ai-mode-travel-planning-canvas-3616391/)、[Good Morning America, 2025](https://www.goodmorningamerica.com/travel/story/new-tools-find-cheap-flight-deals-plan-itineraries-127593267)）。
- **真实库存 + 直接下单**：**有**。Agentic 能力已在 AI Mode 内搜索并预订餐厅、活动门票、本地预约（合作方 OpenTable、Resy、Tock、Ticketmaster、StubHub、SeatGeek、Booksy、Fresha、Vagaro），并宣布可「最小干预」替用户搜并订酒店（2025）。餐厅 agentic 预订已在美国上线，酒店/门票需 Labs 开关。
- **Flight Deals**：2025 年 8 月美加印首发，2025 年 11 月 17 日全球扩展，支持 60+ 语言（2025）。
- **可用性约束**：Canvas 旅行规划仅美国、仅桌面 Web、需在 Labs 开启实验（2025）。**这是行业离「可下单」最近的产品。**
- **差评**：未找到公开数据。

### 9. OpenAI / ChatGPT
- **路径**：对话式，1 步，最轻。
- **关键变化**：2025 年 10 月 OpenAI 上线第三方 Apps in ChatGPT，用户可在会话内直接与 Booking.com、Expedia 等应用交互完成酒店预订（[BSS News](https://www.bssnews.net/news/318712)、[QNA, 2025](https://qna.org.qa/en/news/news-details?date=7/10/2025&id=openai-introduces-third-party-apps-inside-chatgpt)）。ChatGPT 由此从「生成行程文本」升级为「预订前端」。Mindtrip 等多款产品本身构建在 OpenAI 技术栈上。
- **使用率**：McKinsey 研究称**约五成旅客已用 ChatGPT 或 AI 应用做行程规划**（2025 年 12 月，[Europa Press 转述](https://www.europapress.es/consulting-news/noticia-mckinsey-cinco-cada-diez-viajeros-ya-utiliza-chatgpt-apps-ia-planificar-viajes-mckinsey-20251223113934.html)）；Accenture 2025 消费者脉搏调查称 **80% 旅客已使用生成式 AI 工具，对活跃 GenAI 用户而言 GenAI 已是旅行发现的第一入口**，超过社媒与 OTA（[Travolution 转述, 2025](https://www.travolution.com/news/gen-ai-is-becoming-a-top-source-for-travel-discovery/)）。
- **短板**：无库存所有权、无履约与售后，价格与可订性依赖第三方。

### 10. Booking.com（AI Trip Planner）
- **路径**：对话式。2023 年 6 月推出 AI Trip Planner（基于 ChatGPT），后叠加 Smart Filter；推向英语市场并计划扩至日本（[Booking.com 官方新闻稿](https://news.booking.com/download/66bd987e-9153-4042-b167-be4835fe584e/pressemitteilung-aitripplanner-booking.com.pdf)、[Travel Voice, 2025](https://www.travelvoice.jp/20250714-157907)）。
- **库存/下单**：**是**，直连自家全球住宿库存，规划即预订，闭环最完整。
- **AI 战略位置**：Booking 把 AI 放在**漏斗顶部**（提升发现与灵感转化），而非替代搜索（[Hotelier News](https://hoteliernews.com/booking-com-lleva-la-ia-a-la-parte-superior-del-embudo-de-ventas/)）。Skift 2026 年 9 月梳理其在做 5 组 AI 实验（[Skift, 2026](https://skift.com/2026/09/14/bookings-5-ai-experiments-and-why-agodas-is-next/)）。
- **差评**：未找到公开数据。

### 11. Expedia（Romie → 多 Agent 架构）
- **路径调整**：先推「全能型 AI 管家」Romie，但在 2026 年 7 月收购 Layla 后，战略转向**多个垂直专业 Agent 协同**（住宿搜索、行程规划、代理预订分拆），而非一个助手包打天下；同时已在 2025 年 11 月接入 ChatGPT 应用做目的地对话搜索与实时比价（[环球旅讯, 2026](https://www1.traveldaily.cn/article/190495)）。Vrbo 上线自然语言搜索、Hotels.com 接入 AI 代理工具。
- **规模**：收购金额、Romie 用户数均未披露（2026）。CEO Ariane Gorin 公开谈企业在 AI 聊天机器人上的常见错误（[Skift, 2026](https://skift.com/2026/05/19/expedia-ceo-ariane-gorin-on-what-companies-get-wrong-about-ai-chatbots/)）。
- **结论**：Expedia 的路径修正本身就是 2026 年最重要的行业信号——**通用旅行管家叙事遇到瓶颈，垂直 Agent 更可落地。**

### 12. Airbnb
- 2026 年 2 月宣布将在搜索、发现与客服环节内嵌 AI 能力，并测试自然语言搜索用于行程预订（[TechCrunch, 2026](https://techcrunch.com/2026/02/13/airbnb-plans-to-bake-in-ai-features-for-search-discovery-and-support/)、[Dataconomy, 2026](https://dataconomy.com/2026/02/16/airbnb-tests-natural-language-ai-search-tool-for-trip-bookings/)）。未推出独立「AI 行程规划」产品，用户数与定价未找到公开数据。

## 三、2025–2026 赛道趋势

1. **从「生成行程」转向「可执行的预订闭环」。** 生成能力已商品化：环球旅讯直言「生成一份行程本身越来越难构成长期壁垒，真正决定商业化的是实时库存、动态价格、支付、会员体系、售后履约和稳定流量」，而后者主要掌握在 OTA 手里（2026）。Google 的 agentic 酒店预订、ChatGPT 内的 Booking/Expedia 应用、Booking 的 AI Trip Planner 都在补齐同一环（2025–2026）。
2. **从「一个全能助手」转向「多垂直 Agent 协同」。** Expedia 收购 Layla 后重构架构是最明确的证据（2026）。
3. **从纯 AI 转向「AI + 真人」。** Layla 用真人顾问承接高客单复杂行程，线索转化提升 5 倍（2026）。
4. **从 B2C 工具转向 B2B2C。** Mindtrip 做目的地营销与酒店工具并收购 Thatch（2025）；GuideGeek 出售给旅游局（2025）。Skift 创始人 Rafat Ali 指出过去十年消费级旅游创业公司只有三种结局：转 B2B、被巨头连人带技术收购、或关门（2026，转引自[环球旅讯](https://www1.traveldaily.cn/article/190495)）。**Layla 被收购即为第三种结局的正面版本。**
5. **流量入口位移。** Accenture 2025 调查：对活跃 GenAI 用户，GenAI 已是旅行发现第一入口，超过社媒和 OTA；80% 旅客已用 GenAI 工具（2025）。

## 四、三大公认痛点与公开数据

1. **幻觉与不可执行（feasibility / rationality）。** 最硬的公开证据来自 ACL 2025 Findings 的 TripTailor 基准：基于 **50 万+ 真实 POI 与近 4000 条真实行程**评估，**最先进 LLM 生成的行程中不到 10% 达到人类水平**；论文点名的关键挑战正是「可行性、合理性、个性化」——[TripTailor, ACL Findings 2025](https://aclanthology.org/2025.findings-acl.503/)。
2. **不可预订 / 规划与下单之间的断层。** 行业普遍称之为「trust gap」。Accenture 2025 数据显示 **93% 活跃 GenAI 用户已用或愿用 GenAI 辅助购买决策，78% 愿意用 AI 购物助手，57% 想要能跨品牌自主完成任务的 AI 助手**——需求存在，但跨品牌 Agent 的授权与履约仍未落地（2025）。Skift 判断 agentic AI 可能把 OTA 降格为「被动接单方」，恰说明预订入口的控制权之争（[Skift, 2025](https://skift.com/es/2025/10/24/will-agentic-ai-turn-otas-into-passive-order-takers/)）。
3. **无法修改 / 不可控。** Google 的 Canvas 之所以在 2025 年 11 月大改，核心卖点就是「可追问、可改权衡、可暂停后回来编辑、有历史记录」——**能编辑被当作功能卖点而非默认能力**，反向证明修改能力此前普遍缺失（2025）。Skift 用「Travel Slop（旅行垃圾内容）」描述 AI 批量产出的低质行程，也是同一问题的文化面（[Skift, 2026](https://skift.com/2026/01/02/welcome-travel-slop-era/)）。

## 五、用户最在意什么

按可验证证据排序：

1. **可执行性/可信度**：TripTailor 把 feasibility 与 rationality 列为首要挑战（2025）；不到 10% 的 AI 行程达人类水平。
2. **真实价格与省钱**：Google Canvas 的直接卖点是接入实时航班与酒店价格、按预算筛选；Flight Deals 的定位就是「用一句模糊描述找最便宜的 getaway」（2025）。Accenture 显示旅客愿为情感体验多付 1.7 倍价格，但前提是价格本身真实（2025）。
3. **可修改、可控**：Canvas 把「反复追问 + 编辑 + 历史回溯」作为核心交互（2025）；86% 旅客希望自己塑造体验（Accenture 2025）。
4. **少输入、少决策负担**：Accenture 明确把 GenAI 的当期价值定义为「解决选择与决策过载」（2025）；Layla 四成用户直接跳过目的地搜索用对话发起（2026）。

## 六、给产品方的三条结论

1. **不要在「生成行程」上竞争**——它已被大模型商品化。壁垒在实时库存、价格、支付与履约。
2. **编辑与可执行性优先于生成质量**。可修改、可校验、可一键下单的行程，比更漂亮的行程文本更有留存价值。
3. **B2B2C 是独立产品最现实的现金流**（旅游局、酒店、目的地营销），纯 C 端订阅（Layla 3 万付费会员）在巨头入场后很难独立支撑。GuideGeek 100 万用户仍选择卖 B2B 即是明证。
