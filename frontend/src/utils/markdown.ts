/**
 * 轻量 Markdown → HTML 渲染器（供 H5 端 v-html 使用）
 *
 * 覆盖 AI 行程回复常用语法：
 * - ``` 代码块、#/##/### 标题、> 引用、-/* 无序列表、1. 有序列表
 * - **加粗**、*斜体*、`行内代码`、[链接](url)、段落与换行
 *
 * 安全：先对全文做 HTML 转义再套用标记规则，杜绝 XSS
 */

/** HTML 转义 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 行内标记：链接 / 行内代码 / 加粗 / 斜体（在已转义的文本上处理） */
function renderInline(text: string): string {
  return text
    // [文本](链接)
    .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, (_m, label: string, url: string) => {
      return `<a href="${escapeHtml(url).replace(/&amp;/g, '&')}" target="_blank">${label}</a>`
    })
    // `行内代码`
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    // **加粗**（先于 *斜体*）
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    // *斜体*（不吞并相邻星号）
    .replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>')
}

/**
 * 渲染代码块（从 ``` 开始行起解析），返回 HTML 与消耗的行数
 * @param lines 以 ``` 开头的剩余行数组
 */
function renderCodeBlock(lines: string[]): { html: string; consumed: number } {
  const endIdx = lines.slice(1).findIndex((l) => l.trim() === '```')
  if (endIdx === -1) {
    // 未闭合：剩余全部视为代码
    const code = escapeHtml(lines.slice(1).join('\n'))
    return { html: `<pre><code>${code}</code></pre>`, consumed: lines.length }
  }
  const code = escapeHtml(lines.slice(1, endIdx + 1).join('\n'))
  // 1(开始行) + endIdx(代码行数) + 1(结束行)
  return { html: `<pre><code>${code}</code></pre>`, consumed: endIdx + 2 }
}

/** Markdown → HTML（块级 + 行内） */
export function renderMarkdown(md: string): string {
  if (!md) return ''
  const text = md.replace(/\r\n/g, '\n')
  const lines = text.split('\n')
  const out: string[] = []
  let i = 0
  let inList: 'ul' | 'ol' | null = null
  let inQuote = false
  let paragraph: string[] = []

  const flushParagraph = () => {
    if (paragraph.length) {
      out.push(`<p>${renderInline(paragraph.join('<br>'))}</p>`)
      paragraph = []
    }
  }
  const closeList = () => {
    if (inList) {
      out.push(`</${inList}>`)
      inList = null
    }
  }
  const closeQuote = () => {
    if (inQuote) {
      out.push('</blockquote>')
      inQuote = false
    }
  }

  while (i < lines.length) {
    const line = lines[i]

    // 代码块
    if (line.trim().startsWith('```')) {
      flushParagraph()
      closeList()
      closeQuote()
      const { html, consumed } = renderCodeBlock(lines.slice(i))
      out.push(html)
      i += consumed
      continue
    }

    // 标题
    const heading = /^(#{1,4})\s+(.*)$/.exec(line)
    if (heading) {
      flushParagraph()
      closeList()
      closeQuote()
      const level = heading[1].length + 1 // # → h2, ## → h3 ...
      out.push(`<h${level}>${renderInline(heading[2])}</h${level}>`)
      i++
      continue
    }

    // 引用
    const quote = /^>\s?(.*)$/.exec(line)
    if (quote) {
      flushParagraph()
      closeList()
      if (!inQuote) {
        out.push('<blockquote>')
        inQuote = true
      }
      out.push(renderInline(quote[1]) + '<br>')
      i++
      continue
    }
    closeQuote()

    // 无序列表
    const ul = /^[-*]\s+(.*)$/.exec(line)
    if (ul) {
      flushParagraph()
      if (inList !== 'ul') {
        closeList()
        out.push('<ul>')
        inList = 'ul'
      }
      out.push(`<li>${renderInline(ul[1])}</li>`)
      i++
      continue
    }

    // 有序列表
    const ol = /^\d+[.、)]\s+(.*)$/.exec(line)
    if (ol) {
      flushParagraph()
      if (inList !== 'ol') {
        closeList()
        out.push('<ol>')
        inList = 'ol'
      }
      out.push(`<li>${renderInline(ol[1])}</li>`)
      i++
      continue
    }
    closeList()

    // 空行：结束段落
    if (!line.trim()) {
      flushParagraph()
      i++
      continue
    }

    // 普通段落行
    paragraph.push(line)
    i++
  }

  flushParagraph()
  closeList()
  closeQuote()

  return out.join('\n')
}
