export interface SurveyQuestion {
  id: string
  text: string
  emoji?: string
  options: string[]
}

export const BUDGET_OPTIONS = [
  '500元人民币以内（穷游）',
  '500-1000元人民币（舒适经济型）',
  '1000-2000元人民币（品质休闲）',
  '2000元人民币以上（高端度假）'
] as const

export const PACE_OPTIONS = [
  '快节奏，多走多看打卡更多点位',
  '慢节奏，每天只走1-2个点深度放松',
  '中等节奏，一半打卡一半休闲',
  '随机节奏，走到哪算哪不赶时间'
] as const

export const INTEREST_OPTIONS = [
  '火锅美食与夜市小吃',
  '山城风光与城市漫步',
  '历史文化与在地民俗',
  '网红地标与拍照打卡'
] as const

export const COMPANION_OPTIONS = [
  '独自出行',
  '情侣/夫妻双人游',
  '亲子家庭（带小孩）',
  '朋友结伴出行'
] as const

export function buildSurveyQuestions(destination: string, days: string): SurveyQuestion[] {
  return [
    {
      id: 'budget',
      text: `你本次${destination}${days}旅行的总预算大概是多少范围呢？`,
      emoji: '💰',
      options: [...BUDGET_OPTIONS]
    },
    {
      id: 'pace',
      text: '你偏好的旅行节奏是什么样的呢？',
      options: [...PACE_OPTIONS]
    },
    {
      id: 'interest',
      text: `你这次最想在${destination}体验什么？`,
      emoji: '🎯',
      options: [...INTEREST_OPTIONS]
    },
    {
      id: 'companion',
      text: '本次出行同行人员构成是？',
      emoji: '👥',
      options: [...COMPANION_OPTIONS]
    }
  ]
}

export function budgetToAmount(budget: string): number {
  if (budget.includes('500元') && budget.includes('以内')) return 500
  if (budget.includes('500-1000')) return 800
  if (budget.includes('1000-2000')) return 1500
  if (budget.includes('2000元') && budget.includes('以上')) return 3000
  return 1500
}

export function paceToTag(pace: string): string {
  if (pace.includes('快')) return '暴走'
  if (pace.includes('慢')) return '悠闲'
  if (pace.includes('中等')) return '常规'
  return '常规'
}
