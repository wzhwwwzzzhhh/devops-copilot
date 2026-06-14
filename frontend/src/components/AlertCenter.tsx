// 告警中心：展示所有服务告警，支持筛选与确认
import { useState, useEffect, useMemo } from 'react'
import {
  Button, Tag, Table, Card, Spin, Empty, Tooltip, Statistic, Row, Col,
  Drawer, Switch, Space, Select, message,
} from 'antd'
import {
  AlertOutlined, ReloadOutlined,
  CloseCircleOutlined, WarningOutlined, InfoCircleOutlined, BellOutlined,
} from '@ant-design/icons'
import { apiCall } from '../api/client'

interface Props {
  open: boolean
  onClose: () => void
}

interface AlertItem {
  id: number
  source: string
  type: string
  severity: string
  message: string
  timestamp: string
  status: string
}

export default function AlertCenter({ open, onClose }: Props) {
  const [alerts, setAlerts] = useState<AlertItem[]>([])
  const [summary, setSummary] = useState<Record<string, number>>({})
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [sourceFilter, setSourceFilter] = useState<string>('all')
  const [severityFilter, setSeverityFilter] = useState<string>('all')
  const [silencing, setSilencing] = useState<number | null>(null)

  const load = async () => {
    if (!open) return
    setLoading(true)
    setError('')
    try {
      const d = await apiCall<any>('/alerts/status')
      setAlerts((d.alerts || []) as AlertItem[])
      setSummary({
        total: d.total_alerts ?? 0,
        critical: d.critical_count ?? 0,
        warning: d.warning_count ?? 0,
        info: d.info_count ?? 0,
        silenced: d.silenced_count ?? 0,
      })
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

  const handleSilence = async (id: number) => {
    setSilencing(id)
    try {
      await apiCall(`/alerts/silence/${id}`, { method: 'POST' })
      message.success(`告警 ${id} 已静音`)
      await load()
    } catch (e: any) {
      message.error(`静音失败: ${e.message}`)
    } finally {
      setSilencing(null)
    }
  }

  const handleUnsilence = async (id: number) => {
    setSilencing(id)
    try {
      await apiCall(`/alerts/unsilence/${id}`, { method: 'POST' })
      message.success(`告警 ${id} 已取消静音`)
      await load()
    } catch (e: any) {
      message.error(`取消静音失败: ${e.message}`)
    } finally {
      setSilencing(null)
    }
  }

  const filteredAlerts = useMemo(() => {
    let list = [...alerts]
    if (sourceFilter !== 'all') {
      list = list.filter(a => a.source === sourceFilter)
    }
    if (severityFilter !== 'all') {
      list = list.filter(a => a.severity === severityFilter)
    }
    return list
  }, [alerts, sourceFilter, severityFilter])

  // Collect unique sources from data
  const sources = useMemo(() => {
    const set = new Set(alerts.map(a => a.source))
    return Array.from(set).sort()
  }, [alerts])

  // ---------- Severity tag color ----------
  const severityColor = (s: string) => {
    switch (s) {
      case 'critical': return '#ef4444'
      case 'warning':  return '#f59e0b'
      case 'info':     return '#3b82f6'
      default:         return '#8c8c8c'
    }
  }

  const severityLabel = (s: string) => {
    switch (s) {
      case 'critical': return 'CRITICAL'
      case 'warning':  return 'WARNING'
      case 'info':     return 'INFO'
      default:         return s.toUpperCase()
    }
  }

  const statusColor = (s: string) => {
    return s === 'silenced' ? '#8c8c8c' : '#52c41a'
  }

  const statCardStyle = (bg: string) => ({
    borderRadius: 8,
    background: bg,
  } as React.CSSProperties)

  if (!open) return null

  return (
    <Drawer
      title={<span><AlertOutlined style={{ color: '#f59e0b' }} /> 告警中心</span>}
      placement="bottom"
      height="100vh"
      open={open}
      onClose={onClose}
      extra={
        <Space>
          <Tooltip title="自动刷新 30s">
            <span style={{ fontSize: 13, color: 'var(--text2)', marginRight: 4 }}>自动刷新</span>
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
          <Card className="db-card full-width" style={{ textAlign: 'center' }}>
            <Empty
              image={<AlertOutlined style={{ fontSize: 48, color: 'var(--text2)' }} />}
              description={
                <span>
                  <div className="db-empty-title">查询失败</div>
                  <div className="db-empty-desc">{error}</div>
                </span>
              }
            />
          </Card>
        )}

        {!loading && !error && (
          <>
            {/* Row 1: Stat cards */}
            <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
              <Col span={6}>
                <Card size="small" style={statCardStyle('#fef2f2')} bordered={false}>
                  <Statistic
                    title={<span style={{ color: '#991b1b', fontWeight: 600 }}>Critical</span>}
                    value={summary.critical ?? 0}
                    valueStyle={{ color: '#ef4444', fontSize: 28 }}
                    prefix={<CloseCircleOutlined />}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small" style={statCardStyle('#fffbeb')} bordered={false}>
                  <Statistic
                    title={<span style={{ color: '#92400e', fontWeight: 600 }}>Warning</span>}
                    value={summary.warning ?? 0}
                    valueStyle={{ color: '#f59e0b', fontSize: 28 }}
                    prefix={<WarningOutlined />}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small" style={statCardStyle('#eff6ff')} bordered={false}>
                  <Statistic
                    title={<span style={{ color: '#1e40af', fontWeight: 600 }}>Info</span>}
                    value={summary.info ?? 0}
                    valueStyle={{ color: '#3b82f6', fontSize: 28 }}
                    prefix={<InfoCircleOutlined />}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small" style={statCardStyle('#f5f5f5')} bordered={false}>
                  <Statistic
                    title={<span style={{ color: '#595959', fontWeight: 600 }}>Silenced</span>}
                    value={summary.silenced ?? 0}
                    valueStyle={{ color: '#8c8c8c', fontSize: 28 }}
                    prefix={<BellOutlined />}
                  />
                </Card>
              </Col>
            </Row>

            {/* Row 2: Filters */}
            <Row gutter={[12, 12]} style={{ marginBottom: 16 }} align="middle">
              <Col>
                <Space>
                  <span style={{ fontWeight: 500, fontSize: 13 }}>来源</span>
                  <Select
                    value={sourceFilter}
                    onChange={setSourceFilter}
                    style={{ width: 130 }}
                    size="small"
                    options={[
                      { value: 'all', label: '全部来源' },
                      ...sources.map(s => ({ value: s, label: s })),
                    ]}
                  />
                </Space>
              </Col>
              <Col>
                <Space>
                  <span style={{ fontWeight: 500, fontSize: 13 }}>级别</span>
                  <Select
                    value={severityFilter}
                    onChange={setSeverityFilter}
                    style={{ width: 120 }}
                    size="small"
                    options={[
                      { value: 'all', label: '全部级别' },
                      { value: 'critical', label: 'Critical' },
                      { value: 'warning',  label: 'Warning' },
                      { value: 'info',     label: 'Info' },
                    ]}
                  />
                </Space>
              </Col>
              <Col flex="auto">
                <span style={{ fontSize: 12, color: 'var(--text2)' }}>
                  共 {filteredAlerts.length} 条告警
                </span>
              </Col>
            </Row>

            {/* Row 3: Alerts table */}
            <Card className="db-card full-width" size="small" bodyStyle={{ padding: 0 }}>
              <Table<AlertItem>
                rowKey="id"
                dataSource={filteredAlerts}
                columns={[
                  {
                    title: '时间',
                    dataIndex: 'timestamp',
                    key: 'timestamp',
                    width: 160,
                    render: (v: string) => (
                      <span style={{ fontSize: 12, whiteSpace: 'nowrap' }}>{v}</span>
                    ),
                  },
                  {
                    title: '来源',
                    dataIndex: 'source',
                    key: 'source',
                    width: 80,
                    render: (v: string) => <Tag>{v}</Tag>,
                  },
                  {
                    title: '级别',
                    dataIndex: 'severity',
                    key: 'severity',
                    width: 100,
                    render: (v: string) => (
                      <Tag color={severityColor(v)} style={{ fontWeight: 600, border: 'none' }}>
                        {severityLabel(v)}
                      </Tag>
                    ),
                  },
                  {
                    title: '类型',
                    dataIndex: 'type',
                    key: 'type',
                    width: 160,
                    render: (v: string) => (
                      <Tooltip title={v}>
                        <span style={{ fontSize: 12, fontFamily: 'monospace' }}>{v}</span>
                      </Tooltip>
                    ),
                  },
                  {
                    title: '告警内容',
                    dataIndex: 'message',
                    key: 'message',
                    render: (v: string) => (
                      <Tooltip title={v}>
                        <span style={{ fontSize: 13 }}>{v}</span>
                      </Tooltip>
                    ),
                  },
                  {
                    title: '状态',
                    dataIndex: 'status',
                    key: 'status',
                    width: 80,
                    render: (v: string) => (
                      <Tag color={statusColor(v)} style={{ border: 'none' }}>
                        {v === 'silenced' ? '已静音' : '活跃'}
                      </Tag>
                    ),
                  },
                  {
                    title: '操作',
                    key: 'action',
                    width: 100,
                    render: (_: unknown, record: AlertItem) => (
                      record.status === 'silenced' ? (
                        <Button
                          type="link"
                          size="small"
                          loading={silencing === record.id}
                          onClick={() => handleUnsilence(record.id)}
                          style={{ padding: 0 }}
                        >
                          取消静音
                        </Button>
                      ) : (
                        <Button
                          type="link"
                          size="small"
                          loading={silencing === record.id}
                          onClick={() => handleSilence(record.id)}
                          style={{ padding: 0 }}
                        >
                          静音
                        </Button>
                      )
                    ),
                  },
                ]}
                pagination={{ pageSize: 20, size: 'small', showSizeChanger: false }}
                size="small"
                locale={{ emptyText: '无告警数据' }}
              />
            </Card>
          </>
        )}
      </div>
    </Drawer>
  )
}
