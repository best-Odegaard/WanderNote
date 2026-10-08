-- ============================================================
-- V2.7 酒店候选库 hotel_candidate
-- 创建于 2026-10-08
--
-- 用途：支撑「选酒店」这一步（GET /hotel/search）。
--   携程没有公开的开放 API（见 产品项目文档 5.7.2），所以策略是：
--   本站用**自建精选候选库**保证「档次筛选 / 位置筛选」的体验，
--   交易跳转由携程深链承接。价格字段一律标注「参考价，以携程为准」。
--
-- 设计说明：
--   1. hotel_code 是业务主键（如 zq-sf-1），不是自增 id。
--      前端 hotel.vue / utils/hotels.ts 的本地兜底表用的是同一套 code，
--      接口挂了走本地兜底时，用户「上次选的那家」还能按 code 对上号。
--   2. 坐标（lng/lat）是必需的，不是装饰：
--      路线页靠它把当天行程补成「酒店出发 → 各站 → 返回酒店」的闭环，
--      没有坐标就得回退到地理编码，多一次外部请求且可能定位失败。
--   3. nearby_landmark 附近地标同时服务两处：
--        · 携程深链的 keyword = 酒店名 + 附近地标（用户要求「把地点和酒店名一起搜」）；
--        · 位置筛选（?area=）按地标做模糊匹配。
--   4. style_tags 逗号分隔（如「湖景房,含双早」），前端拆成 tag 列表展示。
--   5. 这张表是**运营可维护**的：加城市就是加行，不用发版。
--      未收录的城市由前端 utils/hotels.ts 的通用兜底候选顶上（坐标为空 → 不闭环）。
--   6. 幂等：hotel_code 上唯一索引 + insert ... on duplicate key update，
--      重复执行等于「用脚本里的值刷新这几家」，不会插出重复行。
--
-- ★ 执行前必读 ★
--   库名前缀 geek012 按服务器实际库名调整。
-- ============================================================

create table if not exists geek012.hotel_candidate
(
    id              bigint auto_increment comment '主键' primary key,
    hotel_code      varchar(40)                        not null comment '业务编码（前端按它对上本地兜底表）',
    city            varchar(40)                        not null comment '所在城市（与目的地城市名一致）',
    name            varchar(120)                       not null comment '酒店名称',
    level           varchar(20)                        not null comment '档次：经济型/舒适型/高档型/特色民宿',
    rating          decimal(2, 1)  default 4.5         null comment '评分 0~5',
    ref_price       decimal(10, 2)                     null comment '参考价（元/晚，以携程为准，非实时价）',
    address         varchar(255)                       null comment '详细地址（导航与地理编码兜底都直接用它）',
    nearby_landmark varchar(120)                       null comment '附近地标（深链关键词 + 位置筛选）',
    distance_km     decimal(6, 2)                      null comment '距市中心/主要景区的直线距离（公里，列表展示量级用）',
    style_tags      varchar(255)                       null comment '风格标签，逗号分隔',
    cover           varchar(500)                       null comment '封面图 URL',
    lng             decimal(10, 6)                     null comment '经度',
    lat             decimal(10, 6)                     null comment '纬度',
    intro           varchar(500)                       null comment '一句话简介',
    sort_order      int         default 0              null comment '排序（小的在前，同城市内用）',
    status          int         default 1              null comment '状态：0下架 1上架',
    create_time     datetime    default CURRENT_TIMESTAMP null comment '创建时间',
    update_time     datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_hotel_candidate_code (hotel_code),
    key idx_hotel_candidate_city (city, status, sort_order)
) comment '酒店候选库（自建精选，运营维护；交易跳携程深链）';

-- ------------------------------------------------------------
-- 种子数据：与前端 frontend/src/utils/hotels.ts 的本地兜底表一一对应。
-- 两边同值时，接口可用/不可用的表现一致，不会出现「联网看到的和断网看到的不是一批」。
-- ------------------------------------------------------------
insert into geek012.hotel_candidate
(hotel_code, city, name, level, rating, ref_price, address, nearby_landmark, distance_km, style_tags, cover, lng, lat, intro, sort_order, status)
values
    -- ── 肇庆 ──
    ('zq-jj-1', '肇庆', '星湖假日酒店', '经济型', 4.5, 218, '肇庆市端州区星湖大道 12 号', '七星岩', 1.2, '近星湖,免费停车',
     'https://picsum.photos/seed/zhaoqing-hotel-1/480/320', 112.474200, 23.062100,
     '步行 10 分钟到七星岩牌坊，性价比高，适合一天两晚的轻装出行。', 10, 1),
    ('zq-sf-1', '肇庆', '星湖景畔酒店', '舒适型', 4.7, 368, '肇庆市端州区星湖西路 8 号', '星湖', 0.8, '湖景房,含双早',
     'https://picsum.photos/seed/zhaoqing-hotel-2/480/320', 112.468800, 23.058800,
     '正对星湖，房间能看到湖面晨雾，出门就是环湖绿道。', 20, 1),
    ('zq-gd-1', '肇庆', '七星岩温德姆酒店', '高档型', 4.8, 688, '肇庆市端州区七星岩景区南门旁', '七星岩', 0.3, '景区门口,泳池',
     'https://picsum.photos/seed/zhaoqing-hotel-3/480/320', 112.480100, 23.054900,
     '就在景区南门口，早上不用赶路，适合带老人小孩的家庭。', 30, 1),
    ('zq-ms-1', '肇庆', '岩前村文创民宿', '特色民宿', 4.6, 328, '肇庆市高要区岩前村 3 巷', '岩前村', 2.4, '文艺,窑烤面包',
     'https://picsum.photos/seed/zhaoqing-hotel-4/480/320', 112.489500, 23.045100,
     '由老宅改造，院子里有咖啡馆，晚上安静得只听到虫鸣。', 40, 1),

    -- ── 广州 ──
    ('gz-jj-1', '广州', '广州塔旁如家精选', '经济型', 4.4, 258, '广州市海珠区阅江中路 88 号', '广州塔', 1.1, '近地铁,近广州塔',
     'https://picsum.photos/seed/guangzhou-hotel-1/480/320', 113.321500, 23.106500,
     '出门 5 分钟到地铁站，去珠江新城和上下九都方便。', 10, 1),
    ('gz-sf-1', '广州', '珠江新城希尔顿花园', '舒适型', 4.7, 528, '广州市天河区华夏路 22 号', '花城广场', 0.6, 'CBD,健身房',
     'https://picsum.photos/seed/guangzhou-hotel-2/480/320', 113.323200, 23.121900,
     '城市中心位置，晚上散步就能看小蛮腰灯光秀。', 20, 1),
    ('gz-gd-1', '广州', '白天鹅宾馆', '高档型', 4.9, 1088, '广州市荔湾区沙面南街 1 号', '沙面', 3.2, '江景,老牌五星',
     'https://picsum.photos/seed/guangzhou-hotel-3/480/320', 113.240100, 23.104300,
     '沙面岛上的老牌江景酒店，早茶和中庭园林都值得专程体验。', 30, 1),
    ('gz-ms-1', '广州', '永庆坊骑楼民宿', '特色民宿', 4.6, 398, '广州市荔湾区恩宁路 99 号', '永庆坊', 2.8, '骑楼,老西关',
     'https://picsum.photos/seed/guangzhou-hotel-4/480/320', 113.237900, 23.113900,
     '住在西关骑楼里，楼下就是肠粉与糖水铺。', 40, 1),

    -- ── 桂林 ──
    ('gl-jj-1', '桂林', '阳朔西街青旅', '经济型', 4.5, 158, '桂林市阳朔县西街 66 号', '阳朔西街', 0.4, '西街口,可拼车',
     'https://picsum.photos/seed/guilin-hotel-1/480/320', 110.496600, 24.778100,
     '就在西街入口，晚上热闹，白天可以约人拼车去遇龙河。', 10, 1),
    ('gl-sf-1', '桂林', '漓江畔观景酒店', '舒适型', 4.7, 418, '桂林市象山区滨江路 15 号', '象鼻山', 0.9, '江景房,象鼻山',
     'https://picsum.photos/seed/guilin-hotel-2/480/320', 110.298700, 25.263100,
     '阳台正对漓江与象鼻山，日出时分最好看。', 20, 1),
    ('gl-gd-1', '桂林', '阳朔悦榕庄', '高档型', 4.9, 1680, '桂林市阳朔县遇龙河畔', '遇龙河', 6.5, '山景泳池,度假',
     'https://picsum.photos/seed/guilin-hotel-3/480/320', 110.438800, 24.810300,
     '喀斯特峰林环抱的度假村，适合把行程排得慢一点。', 30, 1),
    ('gl-ms-1', '桂林', '遇龙河竹院民宿', '特色民宿', 4.8, 468, '桂林市阳朔县旧县村 42 号', '遇龙河', 4.2, '田园,骑行',
     'https://picsum.photos/seed/guilin-hotel-4/480/320', 110.452200, 24.803100,
     '院里能看到稻田与峰林，免费借自行车沿河骑行。', 40, 1),

    -- ── 成都 ──
    ('cd-jj-1', '成都', '春熙路7天优品', '经济型', 4.3, 198, '成都市锦江区红星路三段 8 号', '春熙路', 0.5, '近地铁,夜市',
     'https://picsum.photos/seed/chengdu-hotel-1/480/320', 104.081500, 30.657200,
     '走两步就是春熙路与太古里，吃宵夜不用打车。', 10, 1),
    ('cd-sf-1', '成都', '宽窄巷子亚朵酒店', '舒适型', 4.7, 428, '成都市青羊区长顺上街 21 号', '宽窄巷子', 0.6, '近宽窄巷子,书店',
     'https://picsum.photos/seed/chengdu-hotel-2/480/320', 104.055300, 30.669900,
     '巷子口的位置，早起可以直接进宽窄巷子拍空镜。', 20, 1),
    ('cd-gd-1', '成都', '成都博舍', '高档型', 4.9, 1288, '成都市锦江区笔帖式街 81 号', '太古里', 1.0, '设计酒店,太古里',
     'https://picsum.photos/seed/chengdu-hotel-3/480/320', 104.083600, 30.654300,
     '藏在太古里里的清代院落改造酒店，安静与服务都在线。', 30, 1),
    ('cd-ms-1', '成都', '锦里川西小院', '特色民宿', 4.6, 358, '成都市武侯区武侯祠大街 231 号', '锦里', 2.2, '川西民居,盖碗茶',
     'https://picsum.photos/seed/chengdu-hotel-4/480/320', 104.042700, 30.645900,
     '院子里的盖碗茶免费喝，晚上能听到川剧票友吊嗓子。', 40, 1),

    -- ── 重庆 ──
    ('cq-jj-1', '重庆', '解放碑城市便捷', '经济型', 4.4, 188, '重庆市渝中区民权路 12 号', '解放碑', 0.3, '解放碑,轻轨站',
     'https://picsum.photos/seed/chongqing-hotel-1/480/320', 106.577100, 29.557300,
     '出门就是解放碑步行街，去洪崖洞步行 15 分钟。', 10, 1),
    ('cq-sf-1', '重庆', '洪崖洞江景亚朵', '舒适型', 4.7, 458, '重庆市渝中区嘉滨路 88 号', '洪崖洞', 0.2, '江景,夜景',
     'https://picsum.photos/seed/chongqing-hotel-2/480/320', 106.581700, 29.564800,
     '房间正对千厮门大桥，晚上不用挤人群也能看夜景。', 20, 1),
    ('cq-gd-1', '重庆', '重庆尼依格罗酒店', '高档型', 4.8, 1188, '重庆市江北区北滨一路 1 号', '江北嘴', 2.6, '高楼层,两江夜景',
     'https://picsum.photos/seed/chongqing-hotel-3/480/320', 106.566500, 29.571800,
     '60 层以上的落地窗，两江交汇夜景一览无遗。', 30, 1),
    ('cq-ms-1', '重庆', '山城步道吊脚楼民宿', '特色民宿', 4.5, 338, '重庆市渝中区中兴路 155 号', '山城步道', 1.4, '吊脚楼,步道',
     'https://picsum.photos/seed/chongqing-hotel-4/480/320', 106.569100, 29.552700,
     '建在崖壁上的吊脚楼，推窗就是长江与轻轨穿楼。', 40, 1),

    -- ── 杭州 ──
    ('hz-jj-1', '杭州', '西湖湖滨如家', '经济型', 4.4, 238, '杭州市上城区平海路 55 号', '西湖', 0.7, '近西湖,近地铁',
     'https://picsum.photos/seed/hangzhou-hotel-1/480/320', 120.167800, 30.253200,
     '走到湖滨三公园 8 分钟，晚上看音乐喷泉方便。', 10, 1),
    ('hz-sf-1', '杭州', '西子湖四季酒店', '高档型', 4.9, 1588, '杭州市西湖区灵隐路 5 号', '杨公堤', 2.1, '园林,临湖',
     'https://picsum.photos/seed/hangzhou-hotel-2/480/320', 120.131100, 30.248900,
     '园林式布局，从酒店后门可以直接走到杨公堤。', 20, 1),
    ('hz-ms-1', '杭州', '龙井茶园民宿', '特色民宿', 4.7, 528, '杭州市西湖区龙井村 168 号', '龙井村', 5.4, '茶山,安静',
     'https://picsum.photos/seed/hangzhou-hotel-3/480/320', 120.114700, 30.222100,
     '住在茶山里，早上在露台喝茶看云雾散去。', 30, 1),
    ('hz-jj-2', '杭州', '河坊街青年旅舍', '经济型', 4.5, 168, '杭州市上城区河坊街 118 号', '河坊街', 1.5, '老街,可拼车',
     'https://picsum.photos/seed/hangzhou-hotel-4/480/320', 120.168600, 30.240200,
     '老街上，小吃与南宋御街都在步行圈内。', 40, 1)
on duplicate key update
    city            = values(city),
    name            = values(name),
    level           = values(level),
    rating          = values(rating),
    ref_price       = values(ref_price),
    address         = values(address),
    nearby_landmark = values(nearby_landmark),
    distance_km     = values(distance_km),
    style_tags      = values(style_tags),
    cover           = values(cover),
    lng             = values(lng),
    lat             = values(lat),
    intro           = values(intro),
    sort_order      = values(sort_order),
    status          = values(status);

-- ------------------------------------------------------------
-- 执行后核对：
--   select city, count(*) c from geek012.hotel_candidate
--    where status = 1 group by city order by c desc;
--     → 刚上线应为：肇庆/广州/桂林/成都/重庆 各 4 家，杭州 4 家（合计 24）。
--
--   select hotel_code, name, nearby_landmark, lng, lat from geek012.hotel_candidate
--    where city = '肇庆' order by sort_order;
--     → 坐标不应为 0；为 0 的行会导致路线页无法闭环。
--
-- 加城市：插新行即可（保持 hotel_code 唯一），不用发版。
-- 下架：改 status = 0（不要 delete）—— 历史行程的 trip_hotel 里存了冗余名称与坐标，
--       删掉候选行不会影响老行程的展示，但运营侧会失去「这家下架过」的记录。
-- ------------------------------------------------------------
