import { useState, useEffect } from 'react'
import { Button, Tag, Table, Card, Spin, Empty, Tooltip, Statistic, Row, Col, Progress, Alert, Modal, message, Drawer, Switch, Space } from 'antd'
import { ReloadOutlined, WarningOutlined, CheckCircleOutlined, DatabaseOutlined, ThunderboltOutlined, BarChartOutlined, HeartOutlined, StopOutlined } from '@ant-design/icons'
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
      {inst.status_reason && (
        <Alert message={inst.status_reason} type="warning" showIcon style={{ marginTop: 8, fontSize: 12 }} banner />
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
          </>
        )}
      </div>
    </Drawer>
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
