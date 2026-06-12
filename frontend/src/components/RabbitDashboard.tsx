import { useState, useEffect } from 'react'
import { Button, Tag, Table, Card, Spin, Empty, Tooltip, Statistic, Row, Col, Progress, Alert, Drawer, Switch, Space } from 'antd'
import { ReloadOutlined, ApiOutlined, WarningOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { apiCall } from '../api/client'

interface Props {
  open: boolean
  onClose: () => void
}

export default function RabbitDashboard({ open, onClose }: Props) {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(false)

  const load = async () => {
    if (!open) return
    setLoading(true)
    setError('')
    try {
      const d = await apiCall<any>('/rabbitmq/status')
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

  const queueTotals = data?.queue_totals || {}
  const messageStats = data?.message_stats || {}
  const queues: any[] = data?.queues || []
  const nodes: any[] = data?.nodes || []

  const publishRate = messageStats?.publish_details?.rate || 0
  const deliverRate = messageStats?.deliver_details?.rate || 0

  const totalMessages = queueTotals.messages || 0
  const totalReady = queueTotals.messages_ready || 0
  const totalUnacked = queueTotals.messages_unacknowledged || 0

  const hasHighBacklog = queues.some((q: any) => q.ready > 200)

  return (
    <Drawer
      title={<span><ApiOutlined /> RabbitMQ 监控</span>}
      placement="bottom"
      height="100vh"
      open={open}
      onClose={onClose}
      extra={
        <Space>
          <Tag icon={hasHighBacklog ? <WarningOutlined /> : <CheckCircleOutlined />} color={hasHighBacklog ? 'red' : 'green'}>
            {hasHighBacklog ? '积压告警' : '运行正常'}
          </Tag>
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

        {!loading && !error && !data && (
          <Card className="db-card full-width" style={{ textAlign: 'center' }}>
            <Empty
              image={<ApiOutlined style={{ fontSize: 48, color: 'var(--text2)' }} />}
              description={<span>暂无 RabbitMQ 数据</span>}
            />
          </Card>
        )}

        {!loading && !error && data && (
          <>
            {renderOverviewCards(totalMessages, totalReady, totalUnacked, publishRate, deliverRate)}
            {renderTopBackloggedQueues(queues)}
            {renderQueueTable(queues)}
            {renderNodeCards(nodes)}
          </>
        )}
      </div>
    </Drawer>
  )
}

/** Overview stat cards row */
function renderOverviewCards(
  totalMessages: number,
  totalReady: number,
  totalUnacked: number,
  publishRate: number,
  deliverRate: number,
) {
  return (
    <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
      <Col span={4}>
        <Card className="db-card" size="small">
          <Statistic title="总消息数" value={totalMessages} valueStyle={{ color: '#0d9488', fontSize: 24 }} />
        </Card>
      </Col>
      <Col span={5}>
        <Card className="db-card" size="small">
          <Statistic
            title="待消费"
            value={totalReady}
            valueStyle={{ color: totalReady > 200 ? '#ef4444' : totalReady > 100 ? '#f59e0b' : '#34c759', fontSize: 24 }}
          />
        </Card>
      </Col>
      <Col span={5}>
        <Card className="db-card" size="small">
          <Statistic
            title="未确认"
            value={totalUnacked}
            valueStyle={{ color: totalUnacked > 50 ? '#ef4444' : totalUnacked > 20 ? '#f59e0b' : '#34c759', fontSize: 24 }}
          />
        </Card>
      </Col>
      <Col span={5}>
        <Card className="db-card" size="small">
          <Statistic title="发布速率" value={publishRate} suffix="/s" valueStyle={{ color: '#34c759', fontSize: 24 }} />
        </Card>
      </Col>
      <Col span={5}>
        <Card className="db-card" size="small">
          <Statistic title="消费速率" value={deliverRate} suffix="/s" valueStyle={{ color: '#0d9488', fontSize: 24 }} />
        </Card>
      </Col>
    </Row>
  )
}

/** Top backlogged queues highlight card */
function renderTopBackloggedQueues(queues: any[]) {
  if (!queues || queues.length === 0) return null

  const sorted = [...queues].sort((a: any, b: any) => (b.ready || 0) - (a.ready || 0))
  const top5 = sorted.slice(0, 5)
  const maxReady = Math.max(...top5.map((q: any) => q.ready || 0), 1)

  return (
    <Card
      className="db-card full-width"
      size="small"
      title={<span><WarningOutlined /> 积压队列 TOP 5</span>}
      style={{ marginBottom: 12 }}
    >
      {top5.map((q: any, i: number) => {
        const pct = Math.min(Math.round(((q.ready || 0) / maxReady) * 100), 100)
        const barColor = q.ready > 200 ? '#ef4444' : q.ready > 100 ? '#f59e0b' : '#0d9488'
        return (
          <div key={i} style={{ marginBottom: 8 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 2 }}>
              <span>
                {q.name}
                {q.ready > 200 && <Tag color="red" style={{ marginLeft: 6, fontSize: 10 }}>严重积压</Tag>}
              </span>
              <span style={{ color: 'var(--text2)' }}>{q.ready} 条待消费 · {q.consumers} 消费者</span>
            </div>
            <Progress percent={pct} strokeColor={barColor} size="small" />
          </div>
        )
      })}
    </Card>
  )
}

/** Full queue table */
function renderQueueTable(queues: any[]) {
  if (!queues || queues.length === 0) {
    return (
      <Card className="db-card full-width" size="small" title={<span><ApiOutlined /> 队列详情</span>}>
        <Empty description="无队列数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </Card>
    )
  }

  const columns = [
    { title: '队列名称', dataIndex: 'name', key: 'name' },
    {
      title: '待消费', dataIndex: 'ready', key: 'ready',
      sorter: (a: any, b: any) => (a.ready || 0) - (b.ready || 0),
      render: (v: number) => (
        <span
          style={{
            color: v > 200 ? '#ef4444' : v > 100 ? '#f59e0b' : '#34c759',
            fontWeight: v > 100 ? 600 : 400,
          }}
        >
          {v}
        </span>
      ),
    },
    { title: '未确认', dataIndex: 'unacked', key: 'unacked' },
    { title: '总量', dataIndex: 'total', key: 'total' },
    { title: '消费者', dataIndex: 'consumers', key: 'consumers' },
    {
      title: '内存', dataIndex: 'memory', key: 'memory',
      render: (v: number) => `${v}MB`,
    },
  ]

  const hasHighBacklog = queues.some((q: any) => q.ready > 200)

  return (
    <Card
      className="db-card full-width"
      size="small"
      title={<span><ApiOutlined /> 队列详情</span>}
      extra={<Tag color={hasHighBacklog ? 'red' : 'blue'}>{queues.length} 个队列</Tag>}
    >
      <Table
        rowKey={(_, i) => String(i)}
        dataSource={queues}
        columns={columns}
        pagination={false}
        size="small"
        locale={{ emptyText: '无队列数据' }}
      />
    </Card>
  )
}

/** Node health cards */
function renderNodeCards(nodes: any[]) {
  if (!nodes || nodes.length === 0) return null

  return (
    <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
      {nodes.map((node: any, i: number) => {
        const memPct = node.memory_limit ? Math.round((node.memory_used / node.memory_limit) * 100) : 0
        const diskFreeGb = node.disk_free >= 1024 ? (node.disk_free / 1024).toFixed(1) : node.disk_free.toFixed(1)
        const diskUnit = node.disk_free >= 1024 ? 'GB' : 'MB'
        const fdPct = node.fd_total ? Math.round((node.fd_used / node.fd_total) * 100) : 0

        return (
          <Col span={12} key={i}>
            <Card
              className="db-card"
              size="small"
              title={<span><ApiOutlined /> {node.name}</span>}
            >
              <Row gutter={16}>
                <Col span={8}>
                  <Statistic
                    title="内存"
                    value={node.memory_used}
                    suffix={`MB (${memPct}%)`}
                    valueStyle={{
                      fontSize: 18,
                      color: memPct > 80 ? '#ef4444' : memPct > 60 ? '#f59e0b' : '#34c759',
                    }}
                  />
                </Col>
                <Col span={8}>
                  <Statistic
                    title="磁盘剩余"
                    value={diskFreeGb}
                    suffix={diskUnit}
                    valueStyle={{
                      fontSize: 18,
                      color: node.disk_free < 1024 ? '#ef4444' : node.disk_free < 5120 ? '#f59e0b' : '#34c759',
                    }}
                  />
                </Col>
                <Col span={8}>
                  <Statistic
                    title="文件描述符"
                    value={`${node.fd_used}/${node.fd_total}`}
                    valueStyle={{
                      fontSize: 18,
                      color: fdPct > 80 ? '#ef4444' : fdPct > 60 ? '#f59e0b' : '#34c759',
                    }}
                  />
                </Col>
              </Row>
              <Progress
                percent={memPct}
                status={memPct > 80 ? 'exception' : memPct > 60 ? 'active' : 'success'}
                size="small"
                style={{ marginTop: 8 }}
              />
              <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                运行队列: {node.run_queue ?? '-'}
              </div>
            </Card>
          </Col>
        )
      })}
    </Row>
  )
}
