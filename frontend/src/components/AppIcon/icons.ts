/**
 * AppIcon 图标字典（线性风格，24×24 viewBox）
 * - paths: SVG path 数组（fill=none, stroke=currentColor）
 * - fallback: 小程序端降级 emoji
 * 命名统一小驼峰；如需新图标在此追加即可，组件模板无需改动。
 */

export interface IconDef {
  paths: string[]
  fallback: string
}

export const ICONS: Record<string, IconDef> = {
  // ── TabBar ──────────────────────────────────────────────
  home: {
    paths: ['M3 10.5 12 3l9 7.5', 'M5 9.5V21h5v-6h4v6h5V9.5'],
    fallback: '🏠'
  },
  calendar: {
    paths: [
      'M4 6h16v14H4z',
      'M4 10h16',
      'M8 3v4',
      'M16 3v4'
    ],
    fallback: '📅'
  },
  compass: {
    paths: [
      'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z',
      'M15.5 8.5 13.4 13.4l-4.9 2.1 2.1-4.9 4.9-2.1z'
    ],
    fallback: '🧭'
  },
  user: {
    paths: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M4 21c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5'],
    fallback: '👤'
  },
  plus: {
    paths: ['M12 5v14', 'M5 12h14'],
    fallback: '➕'
  },

  // ── 通用功能 ────────────────────────────────────────────
  search: {
    paths: ['M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z', 'M21 21l-4.3-4.3'],
    fallback: '🔍'
  },
  bell: {
    paths: ['M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6z', 'M10 19a2 2 0 0 0 4 0'],
    fallback: '🔔'
  },
  star: {
    paths: ['m12 3 2.7 5.5 6 .9-4.3 4.2 1 6-5.4-2.9-5.4 2.9 1-6L3.3 9.4l6-.9L12 3z'],
    fallback: '⭐'
  },
  'chevron-right': {
    paths: ['M9 5l7 7-7 7'],
    fallback: '›'
  },
  'chevron-left': {
    paths: ['M15 5l-7 7 7 7'],
    fallback: '‹'
  },
  heart: {
    paths: ['M12 20s-7-4.4-9.3-8.5C1 8.4 2.6 5 6 5c2 0 3.5 1.2 4.2 2.6h3.6C14.5 6.2 16 5 18 5c3.4 0 5 3.4 3.3 6.5C19 15.6 12 20 12 20z'],
    fallback: '❤️'
  },
  'map-pin': {
    paths: ['M12 21s-7-5.7-7-11a7 7 0 0 1 14 0c0 5.3-7 11-7 11z', 'M12 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z'],
    fallback: '📍'
  },
  clock: {
    paths: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 7v5l3.5 2'],
    fallback: '🕒'
  },
  send: {
    paths: ['M4 12 20 4l-6 16-3-6-7-2z'],
    fallback: '➤'
  },
  sparkles: {
    paths: [
      'M12 3v4',
      'M12 17v4',
      'M3 12h4',
      'M17 12h4',
      'M5.6 5.6l2.8 2.8',
      'M15.6 15.6l2.8 2.8',
      'M18.4 5.6l-2.8 2.8',
      'M8.4 15.6l-2.8 2.8'
    ],
    fallback: '✦'
  },
  link: {
    paths: [
      'M9.5 14.5 14.5 9.5',
      'M6.5 17.5l-1.2 1.2a3.2 3.2 0 0 1-4.5-4.5l1.2-1.2',
      'M17.5 6.5l1.2-1.2a3.2 3.2 0 0 1 4.5 4.5l-1.2 1.2'
    ],
    fallback: '🔗'
  },
  camera: {
    paths: ['M4 8h3l2-2.5h6L17 8h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z', 'M12 17a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z'],
    fallback: '📷'
  },
  map: {
    paths: ['M9 4 3.5 6.5V20L9 17.5l6 2.5 5.5-2.5V4L15 6.5 9 4z', 'M9 4v13.5', 'M15 6.5V20'],
    fallback: '🗺️'
  },
  eye: {
    paths: ['M2 12s3.5-6.5 10-6.5S22 12 22 12s-3.5 6.5-10 6.5S2 12 2 12z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z'],
    fallback: '👁️'
  },
  message: {
    paths: ['M21 12a8 8 0 0 1-8 8H4l2.3-3.2A8 8 0 1 1 21 12z'],
    fallback: '💬'
  },
  info: {
    paths: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 11v5', 'M12 8h.01'],
    fallback: 'ℹ️'
  },
  settings: {
    paths: [
      'M4 6h6',
      'M14 6h6',
      'M4 12h2',
      'M10 12h10',
      'M4 18h8',
      'M16 18h4',
      'M10 4v4',
      'M16 10v4'
    ],
    fallback: '⚙️'
  },
  globe: {
    paths: [
      'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z',
      'M3 12h18',
      'M12 3a14 14 0 0 1 0 18 14 14 0 0 1 0-18z'
    ],
    fallback: '🌍'
  },
  box: {
    paths: ['M4 7.5 12 3l8 4.5v9L12 21l-8-4.5v-9z', 'M4 7.5 12 12l8-4.5', 'M12 12v9'],
    fallback: '📦'
  },
  robot: {
    paths: [
      'M12 6a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
      'M7 11h-3a1 1 0 0 0-1 1v4a1 1 0 0 0 1 1h3',
      'M17 11h3a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-3',
      'M7 9h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z',
      'M9 14v1',
      'M15 14v1',
      'M9.5 19h5'
    ],
    fallback: '🤖'
  },
  alert: {
    paths: ['M12 3 2.5 20h19L12 3z', 'M12 10v4', 'M12 17.5h.01'],
    fallback: '⚠️'
  },
  walk: {
    paths: [
      'M13 4a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z',
      'M10 10l-1 8 3 3',
      'M10 10l3-2 2 3 3 1',
      'M13 16v5',
      'M6 21l4-2'
    ],
    fallback: '🚶'
  },
  edit: {
    paths: ['M4 20h4L20 8a2.8 2.8 0 0 0-4-4L4 16v4z', 'M13.5 6.5l4 4'],
    fallback: '📝'
  },
  delete: {
    paths: ['M4 7h16', 'M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2', 'M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13', 'M10 11v6', 'M14 11v6'],
    fallback: '🗑️'
  },
  share: {
    paths: ['M12 3v12', 'M8 7l4-4 4 4', 'M5 10v9a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-9'],
    fallback: '📤'
  },

  // ── 兜底 ────────────────────────────────────────────────
  help: {
    paths: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M9.5 9.5a2.5 2.5 0 1 1 3.4 2.3c-.6.3-.9.8-.9 1.4V14', 'M12 17h.01'],
    fallback: '❓'
  }
}
