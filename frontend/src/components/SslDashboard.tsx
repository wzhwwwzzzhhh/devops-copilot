// SSL 证书监控面板：证书到期、域名、签发机构等
import { useState, useEffect } from 'react'
import { Button, Tag, Table, Card, Spin, Empty, Tooltip, Statistic, Row, Col, Alert, Drawer, Space, Switch } from 'antd'
import { ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons'

import { apiCall } from '../api/client'

interface Props {
  open: boolean
  onClose: () => void
}

export default function SslDashboard({ open, onClose }: Props) {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(false)

  const load = async () => {
    if (!open) return
    setLoading(true)
    setError('')
    try {
      const d = await apiCall<any>('/ssl/status')
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

  const summary = data?.summary || {}
  const domains = data?.domains || []
  const total = summary?.total || 0
  const okCount = summary?.ok || 0
  const warningCount = summary?.warning || 0
  const criticalCount = summary?.critical || 0
  const expiredCount = summary?.expired || 0

  const statusTagColor = (status: string) => {
    switch (status) {
      case 'ok': return 'green'
      case 'warning': return 'orange'
      case 'critical': return 'red'
      case 'expired': return 'default'
      default: return 'default'
    }
  }

  const daysColor = (days: number) => {
    if (days > 60) return '#34c759'
    if (days >= 30) return '#f59e0b'
    return '#ef4444'
  }

  const statusLabel = (status: string) => {
    switch (status) {
      case 'ok': return '正常'
      case 'warning': return '即将过期'
      case 'critical': return '紧急'
      case 'expired': return '已过期'
      default: return status
    }
  }

  const columns = [
    {
      title: '域名', dataIndex: 'domain', key: 'domain', width: 180,
      render: (v: string) => <code style={{ fontWeight: 600 }}>{v}</code>,
    },
    {
      title: '颁发者', dataIndex: 'issuer', key: 'issuer', width: 140,
    },
    {
      title: '生效时间', dataIndex: 'valid_from', key: 'valid_from', width: 180,
      render: (v: string) => v ? new Date(v).toLocaleDateString('zh-CN') : '-',
    },
    {
      title: '到期时间', dataIndex: 'valid_to', key: 'valid_to', width: 180,
      render: (v: string) => v ? new Date(v).toLocaleDateString('zh-CN') : '-',
    },
    {
      title: '剩余天数', dataIndex: 'days_remaining', key: 'days_remaining', width: 100,
      sorter: (a: any, b: any) => a.days_remaining - b.days_remaining,
      render: (v: number) => (
        <span style={{ color: daysColor(v), fontWeight: 600 }}>
          {v !== undefined && v !== null ? `${v} 天` : '-'}
        </span>
      ),
    },
    {
      title: 'SAN 数量', dataIndex: 'san_count', key: 'san_count', width: 100,
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 110,
      render: (v: string) => <Tag color={statusTagColor(v)}>{statusLabel(v)}</Tag>,
    },
  ]

  return (
    <Drawer
      title={<span><SafetyCertificateOutlined /> SSL 证书</span>}
      placement="bottom"
      height="100vh"
      open={open}
      onClose={onClose}
      extra={
        <Space>
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
              image={<SafetyCertificateOutlined style={{ fontSize: 48, color: 'var(--text2)' }} />}
              description={
                <span>
                  <div className="db-empty-title">未配置 SSL 监控</div>
                  <div className="db-empty-desc">
                    请在系统设置中添加需要监控的域名
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

        {!loading && data?.configured && (
          <>
            {/* Stat Cards Row */}
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={6}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><SafetyCertificateOutlined /> 域名总数</span>}
                    value={total}
                    valueStyle={{ fontSize: 22, color: '#0d9488' }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    已配置 {total} 个域名
                  </div>
                </Card>
              </Col>
              <Col span={6}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><SafetyCertificateOutlined style={{ color: '#34c759' }} /> 正常</span>}
                    value={okCount}
                    valueStyle={{ fontSize: 22, color: '#34c759' }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    证书状态正常
                  </div>
                </Card>
              </Col>
              <Col span={6}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><SafetyCertificateOutlined style={{ color: '#f59e0b' }} /> 即将过期</span>}
                    value={warningCount}
                    valueStyle={{ fontSize: 22, color: '#f59e0b' }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    剩余不足 60 天
                  </div>
                </Card>
              </Col>
              <Col span={6}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><SafetyCertificateOutlined style={{ color: '#ef4444' }} /> 紧急</span>}
                    value={criticalCount + expiredCount}
                    valueStyle={{ fontSize: 22, color: '#ef4444' }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    剩余不足 30 天{expiredCount > 0 ? ` (已过期 ${expiredCount})` : ''}
                  </div>
                </Card>
              </Col>
            </Row>

            {/* Domain Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><SafetyCertificateOutlined /> 域名证书列表</span>}
              extra={<Tag color="blue">{total} 个域名</Tag>}
              style={{ marginBottom: 16 }}
            >
              {domains.length === 0 ? (
                <Empty description="无域名数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={domains}
                  columns={columns}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无数据' }}
                />
              )}
            </Card>
          </>
        )}
      </div>
    </Drawer>
  )
}
