// WanderNote 行笺 · H5 原型 Mock 数据

const MOCK = {
  // 城市与超级景点
  cities: [
    { id: 'zq', name: '肇庆', hotTag: '落羽杉季', hotScore: 98 },
    { id: 'cq', name: '重庆', hotTag: '山城夜景', hotScore: 95 },
    { id: 'cd', name: '成都', hotTag: '银杏季', hotScore: 92 },
    { id: 'dl', name: '大理', hotTag: '洱海红杉', hotScore: 90 },
    { id: 'xa', name: '西安', hotTag: '初雪长安', hotScore: 88 }
  ],

  superSpots: {
    zq: {
      id: 'qxy',
      name: '七星岩',
      city: '肇庆',
      reason: '全国最大溶洞群，冬季恒温 20℃，落羽杉正当观赏期',
      emoji: '🏞️',
      categories: ['food', 'experience', 'spot']
    },
    cq: {
      id: 'hydb',
      name: '洪崖洞',
      city: '重庆',
      reason: '现实版《千与千寻》，夜景天花板',
      emoji: '🌉',
      categories: ['food', 'experience', 'spot']
    },
    cd: {
      id: 'wuhou',
      name: '武侯祠',
      city: '成都',
      reason: '红墙竹影，三国文化最佳体验地',
      emoji: '🎋',
      categories: ['food', 'experience', 'spot']
    },
    dl: {
      id: 'erhai',
      name: '洱海',
      city: '大理',
      reason: '冬日红杉与海鸥同框，骑行环海最舒服',
      emoji: '🌊',
      categories: ['food', 'experience', 'spot']
    },
    xa: {
      id: 'bingmayong',
      name: '兵马俑',
      city: '西安',
      reason: '世界第八大奇迹，初雪后更有穿越感',
      emoji: '🗿',
      categories: ['food', 'experience', 'spot']
    }
  },

  // 城市详情内容（吃 / 玩 / 景点）
  cityContents: {
    zq: {
      food: [
        { id: 'f1', name: '肇庆裹蒸粽', reason: '本地过年必吃，糯米配绿豆肥猪肉', emoji: '🍙' },
        { id: 'f2', name: '鼎湖山茶', reason: '北回归线上的绿宝石，喝完去爬山', emoji: '🍵' },
        { id: 'f3', name: '岩前村河鲜', reason: '星湖边现捞现做，日落时分最佳', emoji: '🐟' }
      ],
      experience: [
        { id: 'e1', name: '星湖竹筏夜游', reason: '夜间灯光版竹筏，看岩山倒影', emoji: '🛶' },
        { id: 'e2', name: '鼎湖山氧吧徒步', reason: '负离子爆表，亲子友好轻徒步', emoji: '🥾' },
        { id: 'e3', name: '端砚文化村手作', reason: '亲手刻一方端砚带走', emoji: '✒️' }
      ],
      spot: [
        { id: 's1', name: '七星岩落羽杉', reason: '12-1 月变红，限定版秋色', emoji: '🌲' },
        { id: 's2', name: '仙女湖丹顶鹤', reason: '每天固定放飞，仙气十足', emoji: '🦩' },
        { id: 's3', name: '石室岩溶洞', reason: '千年诗廊，恒温避暑', emoji: '🕳️' }
      ]
    },
    cq: {
      food: [
        { id: 'f1', name: '重庆小面', reason: '早餐灵魂，二两起叫', emoji: '🍜' },
        { id: 'f2', name: '珮姐老火锅', reason: '本地人排队也要吃的九宫格', emoji: '🍲' },
        { id: 'f3', name: '磁器口陈麻花', reason: '酥脆香甜，手信首选', emoji: '🥨' }
      ],
      experience: [
        { id: 'e1', name: '长江索道', reason: '空中看两江交汇', emoji: '🚡' },
        { id: 'e2', name: '李子坝轻轨穿楼', reason: '8D 城市必打卡', emoji: '🚇' },
        { id: 'e3', name: '南山一棵树夜景观景', reason: '俯瞰渝中半岛璀璨夜景', emoji: '🌃' }
      ],
      spot: [
        { id: 's1', name: '洪崖洞夜景', reason: '千厮门大桥机位最佳', emoji: '🏮' },
        { id: 's2', name: '解放碑步行街', reason: '城市原点，购物美食集中', emoji: '🏬' },
        { id: 's3', name: '鹅岭二厂', reason: '旧厂房改造的文创园区', emoji: '🏭' }
      ]
    },
    cd: {
      food: [
        { id: 'f1', name: '钟水饺', reason: '红油飘香，甜辣平衡', emoji: '🥟' },
        { id: 'f2', name: '龙抄手', reason: '皮薄馅嫩，鸡汤版更鲜', emoji: '🍲' },
        { id: 'f3', name: '麻辣兔头', reason: '成都人夜宵仪式感', emoji: '🐰' }
      ],
      experience: [
        { id: 'e1', name: '人民公园掏耳朵', reason: '最巴适的成都体验', emoji: '🍵' },
        { id: 'e2', name: '大熊猫繁育基地', reason: '顶流花花，早起才看得到', emoji: '🐼' },
        { id: 'e3', name: '宽窄巷子喝茶', reason: '变脸表演 + 盖碗茶', emoji: '🎭' }
      ],
      spot: [
        { id: 's1', name: '武侯祠红墙', reason: '竹影斑驳，拍照圣地', emoji: '🎋' },
        { id: 's2', name: '锦里古街', reason: '夜游灯笼街，小吃集中', emoji: '🏮' },
        { id: 's3', name: '杜甫草堂', reason: '诗意园林，初冬银杏极美', emoji: '📜' }
      ]
    },
    dl: {
      food: [
        { id: 'f1', name: '过桥米线', reason: '热汤汆生料，仪式感早餐', emoji: '🍜' },
        { id: 'f2', name: '乳扇', reason: '白族特色奶皮子，烤着吃更香', emoji: '🧀' },
        { id: 'f3', name: '酸辣鱼', reason: '洱海鲫鱼 + 酸木瓜', emoji: '🐟' }
      ],
      experience: [
        { id: 'e1', name: '洱海骑行', reason: '海西生态廊道，风景最开阔', emoji: '🚲' },
        { id: 'e2', name: '苍山索道', reason: '俯瞰洱海全景', emoji: '🚡' },
        { id: 'e3', name: '喜洲扎染', reason: '亲手做一块蓝印花布', emoji: '🎨' }
      ],
      spot: [
        { id: 's1', name: '龙龛码头红杉', reason: '日出 + 海鸥 + 红杉', emoji: '🌅' },
        { id: 's2', name: '双廊古镇', reason: '洱海东岸最佳观海小镇', emoji: '🏘️' },
        { id: 's3', name: '崇圣寺三塔', reason: '大理地标，倒影绝美', emoji: '🛕' }
      ]
    },
    xa: {
      food: [
        { id: 'f1', name: '肉夹馍', reason: '腊汁肉肥瘦相间，馍酥肉香', emoji: '🥙' },
        { id: 'f2', name: '羊肉泡馍', reason: '自己掰馍，越碎越入味', emoji: '🍲' },
        { id: 'f3', name: '凉皮', reason: '酸辣爽口，四季皆宜', emoji: '🍜' }
      ],
      experience: [
        { id: 'e1', name: '城墙骑行', reason: '13.7km 环城，看尽古今', emoji: '🚲' },
        { id: 'e2', name: '大唐不夜城', reason: '沉浸式唐风夜游', emoji: '🏮' },
        { id: 'e3', name: '华清宫长恨歌', reason: '实景演出，震撼值回票价', emoji: '🎭' }
      ],
      spot: [
        { id: 's1', name: '兵马俑', reason: '一号坑军阵最壮观', emoji: '🗿' },
        { id: 's2', name: '大雁塔', reason: '北广场音乐喷泉必看', emoji: '🛕' },
        { id: 's3', name: '钟鼓楼', reason: '古城中心，夜景更佳', emoji: '🥁' }
      ]
    }
  },

  // 酒店
  hotels: {
    zq: [
      { id: 'h1', name: '星湖假日酒店', price: 428, tags: ['亲子友好', '有泳池'], dist: '距七星岩 1.2km', emoji: '🏨' },
      { id: 'h2', name: '七星岩温泉度假村', price: 680, tags: ['温泉', '近景区'], dist: '距七星岩 0.8km', emoji: '♨️' },
      { id: 'h3', name: '岩前村民宿', price: 288, tags: ['湖景', '文艺'], dist: '距七星岩 2.0km', emoji: '🏡' },
      { id: 'h4', name: '肇庆大酒店', price: 360, tags: ['市中心', '交通便利'], dist: '距七星岩 3.5km', emoji: '🏢' }
    ],
    cq: [
      { id: 'h1', name: '解放碑威斯汀', price: 820, tags: ['江景', '核心商圈'], dist: '距洪崖洞 0.6km', emoji: '🏨' },
      { id: 'h2', name: '南滨路江景民宿', price: 420, tags: ['夜景', '江景'], dist: '距洪崖洞 1.5km', emoji: '🏡' },
      { id: 'h3', name: '洪崖洞江景酒店', price: 560, tags: ['近景区', '观景'], dist: '距洪崖洞 0.3km', emoji: '🏨' },
      { id: 'h4', name: '观音桥商圈酒店', price: 380, tags: ['商圈', '美食多'], dist: '距洪崖洞 4km', emoji: '🏢' }
    ],
    cd: [
      { id: 'h1', name: '春熙路亚朵', price: 480, tags: ['市中心', '交通便利'], dist: '距武侯祠 3km', emoji: '🏨' },
      { id: 'h2', name: '宽窄巷子精品民宿', price: 360, tags: ['文艺', '老成都'], dist: '距武侯祠 1.8km', emoji: '🏡' },
      { id: 'h3', name: '锦里客栈', price: 320, tags: ['古街', '特色'], dist: '距武侯祠 0.5km', emoji: '🏮' },
      { id: 'h4', name: '熊猫主题亲子酒店', price: 520, tags: ['亲子', '熊猫'], dist: '距熊猫基地 2km', emoji: '🐼' }
    ],
    dl: [
      { id: 'h1', name: '洱海海景酒店', price: 620, tags: ['海景', '日出'], dist: '距龙龛码头 1km', emoji: '🏨' },
      { id: 'h2', name: '古城白族小院', price: 280, tags: ['特色', '安静'], dist: '距古城南门 0.3km', emoji: '🏡' },
      { id: 'h3', name: '双廊海景民宿', price: 580, tags: ['海景', '日落'], dist: '距双廊 0.5km', emoji: '🌅' },
      { id: 'h4', name: '苍山观景酒店', price: 460, tags: ['山景', '静谧'], dist: '距感通索道 2km', emoji: '⛰️' }
    ],
    xa: [
      { id: 'h1', name: '钟楼亚朵', price: 420, tags: ['市中心', '交通便利'], dist: '距钟楼 0.3km', emoji: '🏨' },
      { id: 'h2', name: '大唐不夜城酒店', price: 520, tags: ['唐风', '夜游'], dist: '距大雁塔 0.8km', emoji: '🏮' },
      { id: 'h3', name: '临潼温泉酒店', price: 480, tags: ['温泉', '近兵马俑'], dist: '距兵马俑 3km', emoji: '♨️' },
      { id: 'h4', name: '回民街民宿', price: 260, tags: ['美食', '烟火气'], dist: '距钟楼 0.5km', emoji: '🏡' }
    ]
  },

  // 示例行程：肇庆 2 日游
  sampleTrip: {
    id: 'trip001',
    title: '肇庆 2 日亲子游',
    city: '肇庆',
    days: 2,
    startDate: '2026-12-20',
    hotel: { name: '星湖假日酒店', emoji: '🏨' },
    dayPlans: [
      {
        day: 1,
        schedules: [
          { time: '08:30-09:00', title: '从星湖假日酒店出发', type: 'hotel', emoji: '🏨', location: '肇庆星湖假日酒店', openTime: '-', ticket: '-' },
          { time: '09:00-11:30', title: '七星岩落羽杉', type: 'spot', emoji: '🌲', location: '肇庆市端州区七星岩景区', openTime: '08:00-18:00', ticket: '¥70' },
          { time: '11:45-12:30', title: '岩前村河鲜午餐', type: 'food', emoji: '🐟', location: '岩前村', openTime: '10:00-22:00', ticket: '人均 ¥80' },
          { time: '14:00-16:30', title: '鼎湖山氧吧徒步', type: 'experience', emoji: '🥾', location: '鼎湖山国家级自然保护区', openTime: '08:00-18:00', ticket: '¥70' },
          { time: '18:00-19:30', title: '星湖竹筏夜游', type: 'experience', emoji: '🛶', location: '星湖游船码头', openTime: '19:00-21:00', ticket: '¥50' },
          { time: '20:00-20:30', title: '返回星湖假日酒店', type: 'hotel', emoji: '🏨', location: '肇庆星湖假日酒店', openTime: '-', ticket: '-' }
        ]
      },
      {
        day: 2,
        schedules: [
          { time: '08:30-09:00', title: '从星湖假日酒店出发', type: 'hotel', emoji: '🏨', location: '肇庆星湖假日酒店', openTime: '-', ticket: '-' },
          { time: '09:00-10:30', title: '仙女湖丹顶鹤放飞', type: 'spot', emoji: '🦩', location: '仙女湖景区', openTime: '09:00-17:00', ticket: '含套票' },
          { time: '10:45-12:00', title: '石室岩溶洞探秘', type: 'spot', emoji: '🕳️', location: '七星岩石室岩', openTime: '08:00-18:00', ticket: '含套票' },
          { time: '12:15-13:00', title: '肇庆裹蒸粽体验', type: 'food', emoji: '🍙', location: '岩前村', openTime: '09:00-21:00', ticket: '人均 ¥30' },
          { time: '14:00-16:00', title: '端砚文化村手作', type: 'experience', emoji: '✒️', location: '白石村端砚文化村', openTime: '09:00-17:00', ticket: '¥120' },
          { time: '16:30-17:00', title: '返回星湖假日酒店', type: 'hotel', emoji: '🏨', location: '肇庆星湖假日酒店', openTime: '-', ticket: '-' }
        ]
      }
    ]
  },

  // 社区游记
  journals: [
    { id: 'j1', title: '肇庆 2 天 1 夜：落羽杉、竹筏、裹蒸粽', author: '旅行酱', likes: 128, emoji: '🌲' },
    { id: 'j2', title: '重庆 48 小时：不吃火锅就白来了', author: '辣妹子', likes: 256, emoji: '🌉' },
    { id: 'j3', title: '大理洱海骑行：红杉与海鸥的冬天', author: '海风', likes: 189, emoji: '🌊' },
    { id: 'j4', title: '西安初雪：长安十二时辰', author: '古道', likes: 312, emoji: '🗿' }
  ],

  // 二级提问问题树（简化版）
  questionTree: [
    {
      slot: 'days',
      text: '你想玩几天？',
      hint: '多数人玩 2 天刚好',
      options: [
        { label: '1 天', value: 1 },
        { label: '2 天', value: 2 },
        { label: '3 天', value: 3 },
        { label: '5 天', value: 5 }
      ]
    },
    {
      slot: 'companion',
      text: '和谁一起去？',
      hint: '会影响节奏和酒店推荐',
      options: [
        { label: '亲子', value: '亲子' },
        { label: '情侣 / 夫妻', value: '情侣' },
        { label: '朋友结伴', value: '朋友' },
        { label: '一个人', value: '独游' }
      ]
    },
    {
      slot: 'pace',
      text: '想要什么样的节奏？',
      hint: '每天排 2-3 个重点即可',
      options: [
        { label: '轻松，不想爬山', value: '轻松' },
        { label: '适中，经典都要', value: '适中' },
        { label: '紧凑，多看多走', value: '紧凑' }
      ]
    },
    {
      slot: 'budget',
      text: '人均预算大概多少？',
      hint: '不含大交通',
      options: [
        { label: '¥500 以内', value: 500 },
        { label: '¥500-1000', value: 800 },
        { label: '¥1000-2000', value: 1500 },
        { label: '¥2000 以上', value: 2500 }
      ]
    },
    {
      slot: 'hotelStyle',
      text: '酒店偏好？',
      hint: '用于生成闭环路线',
      options: [
        { label: '亲子友好 / 有泳池', value: '亲子' },
        { label: '近景区 / 方便', value: '近景区' },
        { label: '性价比高', value: '经济' },
        { label: '有特色民宿', value: '民宿' }
      ]
    },
    {
      slot: 'departCity',
      text: '从哪里出发？',
      hint: '用于查车票和首日路线',
      options: [
        { label: '广州', value: '广州' },
        { label: '深圳', value: '深圳' },
        { label: '佛山', value: '佛山' },
        { label: '其他城市', value: '其他' }
      ]
    }
  ],

  // 槽位权重
  slotWeights: {
    destination: 25,
    days: 20,
    departCity: 10,
    dateRange: 10,
    people: 10,
    budget: 10,
    hotelStyle: 10,
    companion: 5
  }
};

// 工具函数
const MOCK_UTILS = {
  getCityById(id) {
    return MOCK.cities.find(c => c.id === id);
  },
  getHotels(cityId) {
    return MOCK.hotels[cityId] || MOCK.hotels.zq;
  },
  getCityContents(cityId) {
    return MOCK.cityContents[cityId] || MOCK.cityContents.zq;
  },
  getSampleTrip() {
    return MOCK.sampleTrip;
  },
  computeCompleteness(slots) {
    let filled = 0;
    let total = 0;
    for (const [key, weight] of Object.entries(MOCK.slotWeights)) {
      total += weight;
      if (slots[key] != null && slots[key] !== '') filled += weight;
    }
    return Math.min(1, filled / total);
  }
};
