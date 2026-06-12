import { useState, useEffect, useRef } from 'react'
import { Button, Tag, Table, Card, Spin, Empty, Tooltip, Statistic, Row, Col, Progress, Alert, Modal, message, Drawer, Switch, Space, Input, Descriptions, Typography } from 'antd'
import { ReloadOutlined, WarningOutlined, CheckCircleOutlined, DatabaseOutlined, ThunderboltOutlined, BarChartOutlined, HeartOutlined, StopOutlined, SearchOutlined, LockOutlined, ExperimentOutlined, SoundOutlined, LineChartOutlined } from '@ant-design/icons'
import ReactEChartsCore from 'echarts-for-react/lib/core'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

import { apiCall } from '../api/client'

interface Props {
  open: boolean
  onClose: () => void
}

export default function DbDashboard({ open, onClose }: Props) {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [silencedAlerts, setSilencedAlerts] = useState<Record<string, number>>(() => {
    try { return JSON.parse(localStorage.getItem('silenced_alerts') || '{}') } catch { return {} }
  })

  const load = async () => {
    if (!open) return
    setLoading(true)
    setError('')
    try {
      const d = await apiCall<any>('/database/status')
      setData(d)
    } catch (e: any) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [open])

  useEffect(() => {
    if (autoRefresh && open) {
      const id = window.setInterval(load, 30000)
      return () => window.clearInterval(id)
    }
  }, [autoRefresh, open])

  // 保存静默状态到 localStorage
  useEffect(() => {
    localStorage.setItem('silenced_alerts', JSON.stringify(silencedAlerts))
  }, [silencedAlerts])

  const silenceAlert = (alertType: string, minutes: number = 60) => {
    const expiry = Date.now() + minutes * 60 * 1000
    setSilencedAlerts(prev => ({ ...prev, [alertType]: expiry }))
    message.success(`告警已静默 ${minutes} 分钟`)
  }

  const isSilenced = (alertType: string): boolean => {
    const expiry = silencedAlerts[alertType]
    if (!expiry) return false
    if (Date.now() > expiry) {
      setSilencedAlerts(prev => { const { [alertType]: _, ...rest } = prev; return rest })
      return false
    }
    return true
  }

  const silencePoolKey = 'connection_pool_high_usage'

  if (!open) return null

  // Keep for Drawer compatibility — the header is now in Drawer's title/extra
  const inst = data?.instances?.[0]
  const pool = inst?.connection_pool || {}
  const usage = pool.usage_percent || 0
  const status = inst?.status || 'UNKNOWN'

  const statusTag = (
    <Tag
      icon={status === 'HEALTHY' ? <CheckCircleOutlined /> : <WarningOutlined />}
      color={status === 'HEALTHY' ? 'green' : status === 'DEGRADED' ? 'orange' : 'red'}
    >
      {status === 'HEALTHY' ? '健康' : status === 'DEGRADED' ? '异常' : error ? '错误' : '未配置'}
    </Tag>
  )

  const poolAlertSilenced = isSilenced(silencePoolKey)

  const poolCard = data?.configured && inst ? (
    <Card
      className="db-card"
      size="small"
      title={<span><ThunderboltOutlined /> 连接池</span>}
      extra={<Tag>{pool.active || 0}/{pool.max || 0}</Tag>}
    >
      <Row gutter={16}>
        <Col span={8}>
          <Statistic
            title="使用率"
            value={usage}
            suffix="%"
            valueStyle={{ color: usage > 80 ? '#ef4444' : usage > 60 ? '#f59e0b' : '#34c759' }}
          />
        </Col>
        <Col span={8}>
          <Statistic title="活跃连接" value={pool.active || 0} valueStyle={{ color: '#34c759' }} />
        </Col>
        <Col span={8}>
          <Statistic title="最大连接" value={pool.max || 0} />
        </Col>
      </Row>
      <Progress
        percent={Math.min(usage, 100)}
        status={usage > 80 ? 'exception' : usage > 60 ? 'active' : 'success'}
        style={{ marginTop: 12 }}
      />
      {inst.status_reason && !poolAlertSilenced && (
        <Alert
          message={inst.status_reason}
          type="warning"
          showIcon
          style={{ marginTop: 8, fontSize: 12 }}
          banner
          action={
            <Button size="small" type="text" onClick={() => silenceAlert(silencePoolKey)} style={{ color: '#fff' }}>
              静默 1h
            </Button>
          }
        />
      )}
      {poolAlertSilenced && inst.status_reason && (
        <Alert message="此告警已被静默" type="success" showIcon style={{ marginTop: 8, fontSize: 12 }} banner />
      )}
      <div className="db-instance-info">
        实例: {inst.name || '-'} · {inst.host || '-'} · {inst.type || '-'}
      </div>
    </Card>
  ) : null

  return (
    <Drawer
      title={<span><DatabaseOutlined /> 数据库监控</span>}
      placement="bottom"
      height="100vh"
      open={open}
      onClose={onClose}
      extra={
        <Space>
          {statusTag}
          <Tooltip title="自动刷新 30s">
            <Switch checked={autoRefresh} onChange={setAutoRefresh} size="small" />
          </Tooltip>
          <Tooltip title="刷新">
            <Button type="text" icon={<ReloadOutlined />} onClick={load} />
          </Tooltip>
        </Space>
      }
    >
      <div className="db-body">
        {loading && (
          <div className="db-loading">
            <Spin size="large" />
          </div>
        )}

        {!loading && !data?.configured && (
          <Card className="db-card full-width" style={{ textAlign: 'center' }}>
            <Empty
              image={<DatabaseOutlined style={{ fontSize: 48, color: 'var(--text2)' }} />}
              description={
                <span>
                  <div className="db-empty-title">未配置 MySQL 连接</div>
                  <div className="db-empty-desc">
                    请在 ⚙ 系统设置 → 服务连接 中添加一个 MySQL 类型的连接
                  </div>
                </span>
              }
            />
          </Card>
        )}

        {!loading && error && (
          <Alert
            message="查询失败"
            description={error}
            type="error"
            showIcon
            className="db-card full-width"
            style={{ textAlign: 'center' }}
          />
        )}

        {!loading && data?.configured && inst && (
          <>
            {poolCard}

            {/* Trend Chart */}
            {renderTrendChart()}

            {/* Card 2: Running Queries */}
            {renderQueriesCard(inst.running_queries, () => load())}

            {/* Card 3: Slow Log Recent */}
            {renderSlowLogCard(inst.slow_log_recent)}

            {/* Card 4: Database Sizes */}
            {renderDbSizeCard(inst.databases)}

            {/* Card 5: SQL Analysis */}
            {renderSqlCard(inst.slow_queries_analysis)}

            {/* Card 6: Table Health */}
            {renderTableHealthCard(inst.table_health)}

            {/* Card 7: Explain Plan */}
            {renderExplainPlanCard()}

            {/* Card 8: Deadlock */}
            {renderDeadlockCard(inst.deadlocks)}

            {/* Silenced Alerts */}
            {renderSilencedAlerts(silencedAlerts, setSilencedAlerts)}
          </>
        )}
      </div>
    </Drawer>
  )
}

/** Trend chart for connection pool usage */
function renderTrendChart() {
  const [trendData, setTrendData] = useState<any[]>([])
  const [loading, setLoading] = useState(false)

  const fetchTrend = async () => {
    setLoading(true)
    try {
      const data = await apiCall<any[]>('/database/trend')
      setTrendData(data || [])
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchTrend() }, [])

  const times = trendData.map((d: any) => d.time)
  const usages = trendData.map((d: any) => d.usage)
  const actives = trendData.map((d: any) => d.active)

  const option = {
    grid: { left: 40, right: 20, top: 20, bottom: 25 },
    xAxis: {
      type: 'category' as const,
      data: times,
      axisLabel: { fontSize: 10, color: '#999' },
    },
    yAxis: [
      { type: 'value' as const, name: '使用率 %', max: 100, axisLabel: { fontSize: 10 } },
      { type: 'value' as const, name: '连接数', axisLabel: { fontSize: 10 } },
    ],
    series: [
      {
        name: '使用率 %',
        type: 'line' as const,
        data: usages,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#0d9488', width: 2 },
        itemStyle: { color: '#0d9488' },
        areaStyle: { color: 'rgba(13, 148, 136, 0.1)' },
        markLine: {
          data: [{ yAxis: 80, label: { formatter: '阈值 80%', color: '#ef4444', fontSize: 10 } }],
          lineStyle: { color: '#ef4444', type: 'dashed' },
        },
      },
      {
        name: '活跃连接',
        type: 'line' as const,
        yAxisIndex: 1,
        data: actives,
        smooth: true,
        symbol: 'diamond',
        symbolSize: 6,
        lineStyle: { color: '#f59e0b', width: 1.5 },
        itemStyle: { color: '#f59e0b' },
      },
    ],
    tooltip: { trigger: 'axis' as const },
  }

  return (
    <Card
      className="db-card full-width"
      size="small"
      title={<span><LineChartOutlined /> 连接池趋势（最近 20 次采样）</span>}
      extra={
        <Space>
          {loading && <Spin size="small" />}
          <Button type="text" size="small" icon={<ReloadOutlined />} onClick={fetchTrend} />
        </Space>
      }
    >
      {trendData.length === 0 && !loading && (
        <Empty description="暂无趋势数据，启动后将自动采集" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}
      {trendData.length > 0 && (
        <ReactEChartsCore echarts={echarts} option={option} style={{ height: 200 }} notMerge />
      )}
    </Card>
  )
}

function renderQueriesCard(queries: any[], onRefresh?: () => void) {
  if (!queries || queries.length === 0) {
    return (
      <Card className="db-card" size="small" title={<span><ThunderboltOutlined /> 运行中查询</span>}>
        <Empty description="当前无活跃查询" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </Card>
    )
  }

  const doKill = (id: number) => {
    Modal.confirm({
      title: '确认终止连接',
      content: `确定要终止连接 ID=${id} 的查询吗？`,
      okText: '终止',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiCall(`/database/kill/${id}`, { method: 'POST' })
          message.success(`连接 ${id} 已终止`)
          onRefresh?.()
        } catch (e: any) {
          message.error('终止失败: ' + e.message)
        }
      },
    })
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id' },
    { title: '用户', key: 'user', render: (_: any, r: any) => `${r.user}@${r.host}` },
    { title: '数据库', dataIndex: 'db', key: 'db', render: (v: string) => v || '-' },
    {
      title: '耗时', dataIndex: 'time_sec', key: 'time_sec',
      render: (v: number) => <span style={{ color: v > 30 ? '#ef4444' : v > 10 ? '#f59e0b' : undefined, fontWeight: v > 10 ? 600 : undefined }}>{v}s</span>,
    },
    { title: '状态', dataIndex: 'state', key: 'state' },
    {
      title: 'SQL', dataIndex: 'sql', key: 'sql',
      render: (v: string) => <Tooltip title={v}><span className="sql-preview">{(v || '').substring(0, 80)}</span></Tooltip>,
    },
    {
      title: '操作', key: 'action',
      render: (_: any, r: any) => (
        <Button type="link" danger size="small" icon={<StopOutlined />} onClick={() => doKill(r.id)}>
          终止
        </Button>
      ),
    },
  ]

  return (
    <Card
      className="db-card full-width"
      size="small"
      title={<span><ThunderboltOutlined /> 运行中查询</span>}
      extra={<Tag color={queries.some((q: any) => q.time_sec > 30) ? 'red' : queries.some((q: any) => q.time_sec > 10) ? 'orange' : 'blue'}>{queries.length} 条</Tag>}
    >
      <Table
        rowKey={(_, i) => String(i)}
        dataSource={queries}
        columns={columns}
        pagination={false}
        size="small"
        locale={{ emptyText: '当前无活跃查询' }}
      />
    </Card>
  )
}

function renderSlowLogCard(slowLogs: any[]) {
  if (!slowLogs || slowLogs.length === 0) {
    return (
      <Card className="db-card" size="small" title={<span><WarningOutlined /> 慢查询日志</span>}>
        <Empty description="最近 30 分钟无慢查询" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </Card>
    )
  }

  const columns = [
    { title: '时间', dataIndex: 'start_time', key: 'start_time', width: 160 },
    { title: '用户', dataIndex: 'user_host', key: 'user_host', width: 120 },
    { title: '耗时', dataIndex: 'query_time', key: 'query_time', width: 80, render: (v: string) => <span style={{ color: '#ef4444', fontWeight: 600 }}>{v}</span> },
    { title: '扫描行', dataIndex: 'rows_examined', key: 'rows_examined', width: 80 },
    { title: '数据库', dataIndex: 'db', key: 'db', width: 100 },
    {
      title: 'SQL', dataIndex: 'sql_text', key: 'sql_text',
      render: (v: string) => <Tooltip title={v}><span className="sql-preview">{(v || '').substring(0, 100)}</span></Tooltip>,
    },
  ]

  return (
    <Card
      className="db-card full-width"
      size="small"
      title={<span><WarningOutlined /> 慢查询日志（最近 30 分钟）</span>}
      extra={<Tag color="red">{slowLogs.length} 条</Tag>}
    >
      <Table
        rowKey={(_, i) => String(i)}
        dataSource={slowLogs}
        columns={columns}
        pagination={false}
        size="small"
        locale={{ emptyText: '无慢查询' }}
      />
    </Card>
  )
}

function renderDbSizeCard(databases: any[]) {
  if (!databases || databases.length === 0) {
    return (
      <Card className="db-card" size="small" title={<span><BarChartOutlined /> 数据库大小</span>}>
        <Empty description="无数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </Card>
    )
  }
  const maxSize = Math.max(...databases.map((d: any) => d.size_mb), 1)

  return (
    <Card
      className="db-card"
      size="small"
      title={<span><BarChartOutlined /> 数据库大小</span>}
    >
      <div>
        {databases.map((d: any, i: number) => (
          <div key={i} style={{ marginBottom: 12 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 2 }}>
              <span>{d.name}</span>
              <span style={{ color: 'var(--text2)' }}>{d.size_mb}MB / {d.table_count}表</span>
            </div>
            <Progress
              percent={Math.min(Math.round((d.size_mb / maxSize) * 100), 100)}
              strokeColor={d.size_mb > 100 ? '#ef4444' : d.size_mb > 10 ? '#f59e0b' : '#0d9488'}
              size="small"
            />
          </div>
        ))}
      </div>
    </Card>
  )
}

function renderSqlCard(sqls: any[]) {
  if (!sqls || sqls.length === 0) {
    return (
      <Card className="db-card" size="small" title={<span><BarChartOutlined /> SQL 性能分析</span>}>
        <Empty description="暂无性能数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </Card>
    )
  }

  const columns = [
    { title: 'SQL 模板', dataIndex: 'sql_pattern', key: 'sql_pattern', render: (v: string) => <Tooltip title={v}><span className="sql-preview">{v}</span></Tooltip> },
    { title: '执行次数', dataIndex: 'execution_count', key: 'execution_count' },
    { title: '总耗时', dataIndex: 'total_time_sec', key: 'total_time_sec', render: (v: number) => `${v}s` },
    { title: '平均耗时', dataIndex: 'avg_time_ms', key: 'avg_time_ms', render: (v: number) => `${v}ms` },
    { title: '扫描/返回比', key: 'ratio', render: (_: any, r: any) => r.rows_examined_per_row_returned || 0 },
    {
      title: '告警', key: 'warn',
      render: (_: any, r: any) => {
        const ratio = r.rows_examined_per_row_returned || 0
        if (r.warning) return <Tag color="orange">缺索引</Tag>
        if (ratio > 10) return <Tag color="orange">扫描量大</Tag>
        if (r.no_index_count > 0) return <Tag color="orange">无索引</Tag>
        return '-'
      },
    },
  ]

  return (
    <Card
      className="db-card full-width"
      size="small"
      title={<span><BarChartOutlined /> SQL 性能分析</span>}
      extra={<Tag color="blue">TOP {sqls.length}</Tag>}
    >
      <Table
        rowKey={(_, i) => String(i)}
        dataSource={sqls}
        columns={columns}
        pagination={false}
        size="small"
        locale={{ emptyText: '暂无性能数据' }}
      />
    </Card>
  )
}

/** Explain plan analysis card with inline SQL input */
function renderExplainPlanCard() {
  const [sql, setSql] = useState('')
  const [result, setResult] = useState<any>(null)
  const [loading, setLoading] = useState(false)

  const doExplain = async () => {
    if (!sql.trim()) return
    setLoading(true)
    setResult(null)
    try {
      const res = await apiCall<any>('/database/explain', {
        method: 'POST',
        body: JSON.stringify({ sql: sql.trim() }),
      })
      setResult(res)
    } catch (e: any) {
      setResult({ error: e.message })
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card
      className="db-card full-width"
      size="small"
      title={<span><SearchOutlined /> 执行计划分析</span>}
      extra={
        <Space>
          <Input.TextArea
            rows={1}
            placeholder="输入 SQL 并分析执行计划..."
            value={sql}
            onChange={e => setSql(e.target.value)}
            style={{ width: 300, fontSize: 12 }}
          />
          <Button type="primary" size="small" icon={<SearchOutlined />} loading={loading} onClick={doExplain}>
            EXPLAIN
          </Button>
        </Space>
      }
    >
      {!result && !loading && (
        <Empty description="输入 SQL 语句，点击 EXPLAIN 分析执行计划" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}
      {loading && <Spin />}
      {result && result.error && <Alert message="分析失败" description={result.error} type="error" showIcon />}
      {result && result.explain && (
        <div style={{ fontSize: 13 }}>
          <Descriptions size="small" column={1} bordered>
            <Descriptions.Item label="SQL">{result.query}</Descriptions.Item>
            {result.explain?.query_block?.cost_info?.query_cost && (
              <Descriptions.Item label="查询成本">{result.explain.query_block.cost_info.query_cost}</Descriptions.Item>
            )}
            {result.explain?.query_block?.table && (
              <>
                <Descriptions.Item label="访问表">{result.explain.query_block.table.table_name}</Descriptions.Item>
                <Descriptions.Item label="访问类型">
                  <Tag color={result.explain.query_block.table.access_type === 'ALL' ? 'red' : 'green'}>
                    {result.explain.query_block.table.access_type}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="扫描行数">{result.explain.query_block.table.rows_examined_per_scan}</Descriptions.Item>
                {result.explain.query_block.table.Extra && (
                  <Descriptions.Item label="Extra">{result.explain.query_block.table.Extra}</Descriptions.Item>
                )}
              </>
            )}
          </Descriptions>
          {result.analysis && (
            <Alert message="优化建议" description={result.analysis} type="warning" showIcon style={{ marginTop: 8 }} />
          )}
          {result.note && <div style={{ color: 'var(--text2)', fontSize: 11, marginTop: 4 }}>{result.note}</div>}
        </div>
      )}
    </Card>
  )
}

/** Deadlock detection card */
function renderDeadlockCard(deadlocks: any[]) {
  const [deadlockData, setDeadlockData] = useState<any>(null)
  const [loading, setLoading] = useState(false)

  const checkDeadlock = async () => {
    setLoading(true)
    try {
      const res = await apiCall<any>('/database/deadlocks')
      setDeadlockData(res)
    } catch (e: any) {
      setDeadlockData({ error: e.message })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { checkDeadlock() }, [])

  const hasDeadlock = deadlockData?.deadlocks && deadlockData.deadlocks.length > 0
  const hasLockWaits = deadlockData?.lock_waits && deadlockData.lock_waits.length > 0

  return (
    <Card
      className="db-card full-width"
      size="small"
      title={<span><LockOutlined /> 死锁与锁等待</span>}
      extra={
        <Space>
          {hasDeadlock && <Tag color="red">{deadlockData.deadlocks.length} 个死锁</Tag>}
          {hasLockWaits && <Tag color="orange">{deadlockData.lock_waits.length} 个锁等待</Tag>}
          {!hasDeadlock && !hasLockWaits && deadlockData && <Tag color="green">无死锁</Tag>}
          <Button type="text" size="small" icon={<ReloadOutlined />} loading={loading} onClick={checkDeadlock} />
        </Space>
      }
    >
      {loading && <Spin />}
      {!deadlockData && !loading && <Empty description="正在检测死锁..." image={Empty.PRESENTED_IMAGE_SIMPLE} />}
      {deadlockData?.error && <Alert message="检测失败" description={deadlockData.error} type="error" showIcon />}

      {hasDeadlock && deadlockData.deadlocks.map((d: any, i: number) => (
        <Card key={i} size="small" type="inner" title={`死锁 #${i + 1}`} style={{ marginBottom: 8 }}>
          <Descriptions size="small" column={1} bordered>
            <Descriptions.Item label="发生时间">{d.time || '-'}</Descriptions.Item>
            <Descriptions.Item label="涉及事务">{d.transactions?.join(', ') || '-'}</Descriptions.Item>
            <Descriptions.Item label="等待资源">{d.waiting_resource || '-'}</Descriptions.Item>
            <Descriptions.Item label="回滚事务">{d.rolled_back || '-'}</Descriptions.Item>
          </Descriptions>
          {d.latest_sql && (
            <div style={{ marginTop: 4 }}>
              <Typography.Text code>{d.latest_sql}</Typography.Text>
            </div>
          )}
        </Card>
      ))}

      {hasLockWaits && (
        <Table
          rowKey={(_, i) => String(i)}
          dataSource={deadlockData.lock_waits}
          columns={[
            { title: '等待事务', dataIndex: 'waiting_trx_id', key: 'waiting_trx_id' },
            { title: '等待锁', dataIndex: 'waiting_lock', key: 'waiting_lock' },
            { title: '阻塞事务', dataIndex: 'blocking_trx_id', key: 'blocking_trx_id' },
            { title: '阻塞锁', dataIndex: 'blocking_lock', key: 'blocking_lock' },
            { title: '等待时长', dataIndex: 'wait_age', key: 'wait_age' },
          ]}
          pagination={false}
          size="small"
        />
      )}

      {!hasDeadlock && !hasLockWaits && deadlockData && !deadlockData.error && (
        <Empty description="当前无死锁或锁等待" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}
    </Card>
  )
}

/** Silenced alerts management */
function renderSilencedAlerts(silenced: Record<string, number>, setSilenced: (updater: any) => void) {
  const now = Date.now()
  const entries = Object.entries(silenced).filter(([_, expiry]) => expiry > now)

  if (entries.length === 0) return null

  const unsilence = (key: string) => {
    setSilenced((prev: Record<string, number>) => {
      const { [key]: _, ...rest } = prev
      return rest
    })
    message.success('已取消静默')
  }

  return (
    <Card
      className="db-card full-width"
      size="small"
      title={<span><SoundOutlined /> 已静默告警</span>}
      extra={<Tag>{entries.length} 条</Tag>}
    >
      {entries.map(([key, expiry]) => {
        const remaining = Math.round((expiry - now) / 60000)
        return (
          <Alert
            key={key}
            type="info"
            showIcon
            message={key}
            description={`剩余 ${remaining} 分钟`}
            style={{ marginBottom: 4, fontSize: 12 }}
            action={
              <Button size="small" onClick={() => unsilence(key)}>
                取消静默
              </Button>
            }
          />
        )
      })}
    </Card>
  )
}

function renderTableHealthCard(tables: any[]) {
  if (!tables || tables.length === 0) {
    return (
      <Card className="db-card full-width" size="small" title={<span><HeartOutlined /> 表健康检查</span>}>
        <Empty description="无数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </Card>
    )
  }

  const columns = [
    { title: '库', dataIndex: 'schema', key: 'schema' },
    { title: '表', dataIndex: 'table', key: 'table' },
    { title: '引擎', dataIndex: 'engine', key: 'engine', render: (v: string) => v || '-' },
    { title: '行数', dataIndex: 'rows', key: 'rows' },
    { title: '大小', dataIndex: 'size_mb', key: 'size_mb', render: (v: number) => `${v}MB` },
    { title: '碎片', key: 'frag', render: (_: any, r: any) => r.fragmentation_mb ? `${r.fragmentation_mb}MB` : '-' },
    {
      title: '告警', key: 'warn',
      render: (_: any, r: any) => r.warning
        ? <Tag color="red">{r.warning}</Tag>
        : <span style={{ color: '#34c759' }}><CheckCircleOutlined /></span>,
    },
  ]

  return (
    <Card
      className="db-card full-width"
      size="small"
      title={<span><HeartOutlined /> 表健康检查</span>}
      extra={<Tag>{tables.length} 张表</Tag>}
    >
      <Table
        rowKey={(_, i) => String(i)}
        dataSource={tables}
        columns={columns}
        pagination={false}
        size="small"
        locale={{ emptyText: '无数据' }}
      />
    </Card>
  )
}
