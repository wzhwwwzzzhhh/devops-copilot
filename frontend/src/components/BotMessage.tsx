// 机器人消息组件：Markdown 渲染 + ECharts 图表可视化（连接池/数据库/健康状态）
import { useState, useMemo } from 'react'
import { Card } from 'antd'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import ReactEChartsCore from 'echarts-for-react/lib/core'
import * as echarts from 'echarts/core'
import { GaugeChart, BarChart, PieChart } from 'echarts/charts'
import { TooltipComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([GaugeChart, BarChart, PieChart, TooltipComponent, GridComponent, CanvasRenderer])

interface BotMessageProps {
  content: string
  messageId?: string
}

type ParseResult =
  | { type: 'structured'; data: Record<string, unknown>; text: string }
  | { type: 'extracted'; pool?: { usage: number; active: number; max: number }; text: string }
  | { type: 'text'; text: string }

/**
 * 从文本中提取连接池数据
 * 匹配"使用率 X%"、"活跃 X"、"最大 X" 等模式
 */
function extractPoolMetrics(text: string): { usage: number; active: number; max: number } | null {
  // 中文模式: "使用率 3%" / "活跃 6" / "最大 151"
  const usageMatch = text.match(/(?:使用率|连接池).*?(\d+)%/i) || text.match(/(\d+)%\s*[（(].*?(?:活跃|连接)/)
  const usage = usageMatch ? parseInt(usageMatch[1]) : NaN

  const activeMatch = text.match(/活跃连接[：:]\s*(\d+)/) || text.match(/(?:active|活跃)[：:\s]*(\d+)/i)
  const active = activeMatch ? parseInt(activeMatch[1]) : NaN

  const maxMatch = text.match(/最大连接[：:]\s*(\d+)/) || text.match(/(?:max|最大)[：:\s]*(\d+)/i)
  const max = maxMatch ? parseInt(maxMatch[1]) : NaN

  if (!isNaN(usage) && !isNaN(active) && !isNaN(max)) {
    return { usage, active, max }
  }
  if (!isNaN(usage)) {
    return { usage, active: active || 0, max: max || 100 }
  }
  return null
}

/**
 * 检测文本是否包含可供图形化的结构化数据关键词
 * 中英文均支持
 */
function hasStructuredData(text: string): boolean {
  const keywords = ['连接池', '使用率', '运行中查询', '数据库大小', 'SQL 性能', '表健康',
    '每秒请求', '错误率', '延迟', 'P99', '慢查询', '总耗时', '平均耗时', '扫描/返回比',
    'connection_pool', 'usage_percent', 'running_queries', 'slow_queries', 'table_health',
    'databases', 'slow_log_recent']
  return keywords.some(k => text.includes(k))
}

function parseContent(content: string): ParseResult {
  // 1) 尝试 JSON 代码块
  const jsonMatch = content.match(/```json\n?([\s\S]*?)\n?```/)
  if (jsonMatch) {
    try {
      const data = JSON.parse(jsonMatch[1])
      return { type: 'structured', data, text: content.replace(/```json[\s\S]*?```/, '').trim() }
    } catch { /* ignore */ }
  }

  // 1b) 尝试直接解析文本中嵌入的 JSON（工具返回的原始数据）
  // 查找以 { 开头、包含 "instances" 的 JSON 块
  const rawJsonMatch = content.match(/\{[^{]*"instances"\s*:/)
  if (rawJsonMatch) {
    try {
      // 从匹配位置向后找完整的 JSON
      const startIdx = rawJsonMatch.index!
      let depth = 0
      let endIdx = startIdx
      for (let i = startIdx; i < content.length; i++) {
        if (content[i] === '{') depth++
        else if (content[i] === '}') { depth--; if (depth === 0) { endIdx = i + 1; break } }
      }
      if (depth === 0 && endIdx > startIdx) {
        const rawJson = content.substring(startIdx, endIdx)
        const data = JSON.parse(rawJson)
        // 确保含有 instances 且至少有一个 connection_pool
        if (data.instances && Array.isArray(data.instances) && data.instances.length > 0) {
          return { type: 'structured', data, text: content }
        }
      }
    } catch { /* ignore */ }
  }

  // 2) 尝试从文本中提取连接池数据（支持中文和英文字段名）
  const pool = extractPoolMetrics(content)
  if (pool) {
    return { type: 'extracted', pool, text: content }
  }

  // 3) 检测是否包含结构化关键词
  if (hasStructuredData(content)) {
    return { type: 'extracted', text: content }
  }

  return { type: 'text', text: content }
}

/** 纯文本图表 —— 从提取的数据构建可视化 */
function buildTextCharts(parsed: ParseResult): React.ReactNode[] {
  const charts: React.ReactNode[] = []

  // 连接池仪表盘
  if (parsed.type === 'extracted' && parsed.pool) {
    const { usage, active, max } = parsed.pool
    charts.push(
      <Card key="pool" size="small" className="chart-card"
        title={<span className="chart-title">连接池使用率</span>}
      >
        <ReactEChartsCore
          echarts={echarts}
          option={{
            series: [{
              type: 'gauge',
              center: ['50%', '60%'],
              radius: '80%',
              startAngle: 200, endAngle: -20,
              min: 0, max: 100, splitNumber: 5,
              progress: { show: true, width: 12 },
              axisLine: {
                lineStyle: {
                  width: 12,
                  color: [[0.6, '#0d9488'], [0.8, '#f59e0b'], [1, '#ef4444']],
                },
              },
              axisTick: { show: false },
              splitLine: { show: false },
              detail: { fontSize: 24, fontWeight: 'bold', formatter: '{value}%' },
              data: [{ value: usage }],
            }],
          }}
          style={{ height: 200 }}
        />
        <div className="chart-meta">{active} / {max} 活跃连接</div>
      </Card>,
    )
  }

  // 数据库大小 —— 尝试从表格行提取
  const dbLines = parsed.text.split('\n').filter(l =>
    l.includes('MB') && (l.includes('张表') || (l.match(/\d+\.?\d*\s*MB/) && l.match(/\d+\.?\d*\s*MB/)![0].length < 10))
  )
  if (dbLines.length > 0) {
    const dbData: { name: string; size: number; tables: number }[] = []
    for (const line of dbLines) {
      const nameMatch = line.match(/(\S[^：:]*?)[：:]\s*/)
      const sizeMatch = line.match(/(\d+\.?\d*)\s*MB/)
      const tableMatch = line.match(/(\d+)\s*张?表/)
      if (nameMatch && sizeMatch) {
        dbData.push({
          name: nameMatch[1].trim().replace(/[|]/g, '').trim(),
          size: parseFloat(sizeMatch[1]),
          tables: tableMatch ? parseInt(tableMatch[1]) : 0,
        })
      }
    }
    if (dbData.length > 0) {
      const maxSize = Math.max(...dbData.map(d => d.size), 1)
      charts.push(
        <Card key="dbsize" size="small" className="chart-card"
          title={<span className="chart-title">数据库大小 (MB)</span>}
        >
          <ReactEChartsCore
            echarts={echarts}
            option={{
              tooltip: { trigger: 'axis' },
              grid: { left: 80, right: 20, top: 10, bottom: 30 },
              xAxis: { type: 'category', data: dbData.map(d => d.name), axisLabel: { rotate: 20, fontSize: 10 } },
              yAxis: { type: 'value', name: 'MB' },
              series: [{
                type: 'bar',
                data: dbData.map(d => ({
                  value: d.size,
                  itemStyle: { color: d.size > maxSize * 0.5 ? '#ef4444' : d.size > maxSize * 0.2 ? '#f59e0b' : '#0d9488' },
                })),
                barWidth: '50%',
              }],
            }}
            style={{ height: 200 }}
          />
        </Card>,
      )
    }
  }

  // 健康状态饼图 —— 从"健康"/"异常" 等关键词计数
  const healthyCount = (parsed.text.match(/✅|健康|HEALTHY|正常/g) || []).length
  const warnCount = (parsed.text.match(/⚠️|异常|DEGRADED|告警|警告|WARN/g) || []).length
  if (healthyCount > 0 || warnCount > 0) {
    charts.push(
      <Card key="health" size="small" className="chart-card"
        title={<span className="chart-title">状态概览</span>}
      >
        <ReactEChartsCore
          echarts={echarts}
          option={{
            tooltip: { trigger: 'item' },
            series: [{
              type: 'pie',
              radius: ['45%', '70%'],
              center: ['50%', '55%'],
              data: [
                { value: Math.max(healthyCount, 1), name: '健康', itemStyle: { color: '#0d9488' } },
                { value: Math.max(warnCount, 1), name: '异常', itemStyle: { color: '#ef4444' } },
              ],
              label: { fontSize: 12 },
            }],
          }}
          style={{ height: 200 }}
        />
      </Card>,
    )
  }

  return charts
}

/** 从 JSON 结构化数据构建 ECharts */
function buildJsonCharts(data: Record<string, unknown>): React.ReactNode[] {
  const charts: React.ReactNode[] = []
  const instances = (data.instances as Array<Record<string, unknown>>) || []

  for (const inst of instances) {
    // 连接池
    const pool = inst.connection_pool as Record<string, number> | undefined
    if (pool?.usage_percent != null) {
      charts.push(
        <Card key="pool" size="small" className="chart-card"
          title={<span className="chart-title">连接池使用率</span>}
        >
          <ReactEChartsCore
            echarts={echarts}
            option={{
              series: [{
                type: 'gauge',
                center: ['50%', '60%'], radius: '80%',
                startAngle: 200, endAngle: -20,
                min: 0, max: 100, splitNumber: 5,
                progress: { show: true, width: 12 },
                axisLine: {
                  lineStyle: {
                    width: 12,
                    color: [[0.6, '#0d9488'], [0.8, '#f59e0b'], [1, '#ef4444']],
                  },
                },
                axisTick: { show: false }, splitLine: { show: false },
                detail: { fontSize: 24, fontWeight: 'bold', formatter: '{value}%' },
                data: [{ value: pool.usage_percent }],
              }],
            }}
            style={{ height: 200 }}
          />
          <div className="chart-meta">{pool.active} / {pool.max} 活跃连接</div>
        </Card>,
      )
    }

    // 数据库大小
    const dbs = inst.databases as Array<Record<string, number | string>> | undefined
    if (dbs && dbs.length > 0) {
      charts.push(
        <Card key="dbsize" size="small" className="chart-card"
          title={<span className="chart-title">数据库大小 (MB)</span>}
        >
          <ReactEChartsCore
            echarts={echarts}
            option={{
              tooltip: { trigger: 'axis' },
              grid: { left: 80, right: 20, top: 10, bottom: 30 },
              xAxis: { type: 'category', data: dbs.map(d => d.name), axisLabel: { rotate: 30, fontSize: 11 } },
              yAxis: { type: 'value', name: 'MB' },
              series: [{
                type: 'bar',
                data: dbs.map(d => ({
                  value: d.size_mb,
                  itemStyle: { color: (d.size_mb as number) > 100 ? '#ef4444' : (d.size_mb as number) > 10 ? '#f59e0b' : '#0d9488' },
                })),
                barWidth: '50%',
              }],
            }}
            style={{ height: 220 }}
          />
        </Card>,
      )
    }

    // 表健康饼图
    const tables = inst.table_health as Array<Record<string, unknown>> | undefined
    if (tables && tables.length > 0) {
      const withIssues = tables.filter(t => t.warning).length
      charts.push(
        <Card key="health" size="small" className="chart-card"
          title={<span className="chart-title">表健康</span>}
        >
          <ReactEChartsCore
            echarts={echarts}
            option={{
              tooltip: { trigger: 'item' },
              series: [{
                type: 'pie', radius: ['45%', '70%'], center: ['50%', '55%'],
                data: [
                  { value: tables.length - withIssues, name: '健康', itemStyle: { color: '#0d9488' } },
                  { value: withIssues, name: '异常', itemStyle: { color: '#ef4444' } },
                ],
                label: { fontSize: 12 },
              }],
            }}
            style={{ height: 200 }}
          />
        </Card>,
      )
    }
  }

  return charts
}

export default function BotMessage({ content, messageId: _mid }: BotMessageProps) {
  const [mode, setMode] = useState<'text' | 'mixed'>('mixed')
  const [copied, setCopied] = useState(false)
  const parsed = useMemo(() => parseContent(content), [content])

  // 判断是否有可图形化的数据
  const hasCharts = parsed.type === 'structured' || parsed.type === 'extracted'

  // 构建图表
  const charts = useMemo(() => {
    if (parsed.type === 'structured') return buildJsonCharts(parsed.data)
    if (parsed.type === 'extracted') return buildTextCharts(parsed)
    return []
  }, [parsed])

  const copyContent = async () => {
    try {
      await navigator.clipboard.writeText(content)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch { /* */ }
  }

  return (
    <div className="msg bot">
      <div className="msg-toolbar">
        <div className="toolbar-group">
          <button
            className={`toolbar-btn ${mode === 'text' ? 'active' : ''}`}
            onClick={() => setMode('text')}
            title="纯文字模式"
          >📄</button>
          <button
            className={`toolbar-btn ${mode === 'mixed' ? 'active' : ''}`}
            onClick={() => { if (hasCharts) setMode('mixed') }}
            title={hasCharts ? '图文混排（默认）' : '无图表数据'}
            style={!hasCharts ? { opacity: 0.4, cursor: 'not-allowed' } : undefined}
          >📊&#xFE0F;</button>
        </div>
        <button className="toolbar-btn" onClick={copyContent} title={copied ? '已复制' : '复制'}>
          {copied ? '✓' : '📋'}
        </button>
      </div>

      {/* 文字区域：两种模式都显示 */}
      <div className="msg-content"><ReactMarkdown remarkPlugins={[remarkGfm]}>{parsed.text}</ReactMarkdown></div>

      {/* 图表区域：仅在混排模式且有图表时显示 */}
      {mode === 'mixed' && hasCharts && charts.length > 0 && (
        <div className="msg-charts">
          {charts}
        </div>
      )}
    </div>
  )
}
