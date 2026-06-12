import { useState, useEffect } from 'react'
import { Button, Tag, Table, Card, Spin, Empty, Tooltip, Statistic, Row, Col, Progress, Alert, Drawer, Switch, Space } from 'antd'
import { ReloadOutlined, FileSearchOutlined, BarChartOutlined, ApiOutlined, WarningOutlined, GlobalOutlined, ClockCircleOutlined } from '@ant-design/icons'

import { apiCall } from '../api/client'

interface Props {
  open: boolean
  onClose: () => void
}

export default function NginxDashboard({ open, onClose }: Props) {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(false)

  const load = async () => {
    if (!open) return
    setLoading(true)
    setError('')
    try {
      const d = await apiCall<any>('/nginx/status')
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

  const analysis = data?.analysis || {}
  const qpsTrend = data?.qps_trend || []

  const qps = analysis?.qps ?? 0
  const peakQps = analysis?.peak_qps ?? 0
  const statusCodes = analysis?.status_codes || {}
  const responseTime = analysis?.response_time || {}
  const topUrls = analysis?.top_urls || []
  const topIps = analysis?.top_ips || []
  const errorPaths = analysis?.error_paths || []

  const totalCount = topIps.length > 0 ? topIps.reduce((acc: number, ip: any) => acc + (ip.count || 0), 0) : 0

  return (
    <Drawer
      title={<span><FileSearchOutlined /> Nginx 分析</span>}
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
              image={<FileSearchOutlined style={{ fontSize: 48, color: 'var(--text2)' }} />}
              description={
                <span>
                  <div className="db-empty-title">未配置 Nginx 连接</div>
                  <div className="db-empty-desc">
                    请在系统设置中添加一个 Nginx 类型的连接
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
            {/* Row 1: Stat Cards - QPS & Latency */}
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={4}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><BarChartOutlined /> QPS</span>}
                    value={qps}
                    valueStyle={{ fontSize: 22, color: qps > 2000 ? '#ef4444' : qps > 1000 ? '#f59e0b' : '#34c759' }}
                    suffix="req/s"
                  />
                </Card>
              </Col>
              <Col span={4}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><BarChartOutlined /> 峰值 QPS</span>}
                    value={peakQps}
                    valueStyle={{ fontSize: 22, color: peakQps > 3000 ? '#ef4444' : '#f59e0b' }}
                    suffix="req/s"
                  />
                </Card>
              </Col>
              <Col span={4}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><ClockCircleOutlined /> P50 延迟</span>}
                    value={responseTime?.p50 ?? '-'}
                    valueStyle={{ fontSize: 22, color: responseTime?.p50 > 200 ? '#ef4444' : '#34c759' }}
                    suffix="ms"
                  />
                </Card>
              </Col>
              <Col span={4}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><ClockCircleOutlined /> P95 延迟</span>}
                    value={responseTime?.p95 ?? '-'}
                    valueStyle={{ fontSize: 22, color: responseTime?.p95 > 500 ? '#ef4444' : '#f59e0b' }}
                    suffix="ms"
                  />
                </Card>
              </Col>
              <Col span={4}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><ClockCircleOutlined /> P99 延迟</span>}
                    value={responseTime?.p99 ?? '-'}
                    valueStyle={{ fontSize: 22, color: responseTime?.p99 > 1000 ? '#ef4444' : '#f59e0b' }}
                    suffix="ms"
                  />
                </Card>
              </Col>
              <Col span={4}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><ClockCircleOutlined /> 最大延迟</span>}
                    value={responseTime?.max ?? '-'}
                    valueStyle={{ fontSize: 22, color: '#ef4444' }}
                    suffix="ms"
                  />
                </Card>
              </Col>
            </Row>

            {/* Row 2: Status Code Distribution */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><ApiOutlined /> 状态码分布</span>}
              style={{ marginBottom: 16 }}
            >
              <Row gutter={[16, 8]}>
                <Col span={6}>
                  <div style={{ marginBottom: 4 }}>
                    <span style={{ color: 'var(--text2)', fontSize: 12 }}>2xx</span>
                    <span style={{ float: 'right', fontWeight: 600, color: '#34c759' }}>{statusCodes['2xx'] ?? 0}%</span>
                  </div>
                  <Progress percent={statusCodes['2xx'] ?? 0} strokeColor="#34c759" size="small" showInfo={false} />
                </Col>
                <Col span={6}>
                  <div style={{ marginBottom: 4 }}>
                    <span style={{ color: 'var(--text2)', fontSize: 12 }}>3xx</span>
                    <span style={{ float: 'right', fontWeight: 600, color: '#1677ff' }}>{statusCodes['3xx'] ?? 0}%</span>
                  </div>
                  <Progress percent={statusCodes['3xx'] ?? 0} strokeColor="#1677ff" size="small" showInfo={false} />
                </Col>
                <Col span={6}>
                  <div style={{ marginBottom: 4 }}>
                    <span style={{ color: 'var(--text2)', fontSize: 12 }}>4xx</span>
                    <span style={{ float: 'right', fontWeight: 600, color: '#f59e0b' }}>{statusCodes['4xx'] ?? 0}%</span>
                  </div>
                  <Progress percent={statusCodes['4xx'] ?? 0} strokeColor="#f59e0b" size="small" showInfo={false} />
                </Col>
                <Col span={6}>
                  <div style={{ marginBottom: 4 }}>
                    <span style={{ color: 'var(--text2)', fontSize: 12 }}>5xx</span>
                    <span style={{ float: 'right', fontWeight: 600, color: '#ef4444' }}>{statusCodes['5xx'] ?? 0}%</span>
                  </div>
                  <Progress percent={statusCodes['5xx'] ?? 0} strokeColor="#ef4444" size="small" showInfo={false} />
                </Col>
              </Row>
            </Card>

            {/* Row 3: Top URLs Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><GlobalOutlined /> Top URLs</span>}
              extra={<Tag color="blue">{topUrls.length} 条</Tag>}
              style={{ marginBottom: 16 }}
            >
              {topUrls.length === 0 ? (
                <Empty description="无 URL 数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={topUrls}
                  columns={[
                    { title: 'URL', dataIndex: 'url', key: 'url' },
                    {
                      title: '请求次数', dataIndex: 'count', key: 'count', width: 120,
                      render: (v: number) => v?.toLocaleString(),
                      sorter: (a: any, b: any) => a.count - b.count,
                      defaultSortOrder: 'descend',
                    },
                    {
                      title: '平均耗时 (ms)', dataIndex: 'avg_time', key: 'avg_time', width: 130,
                      render: (v: number) => <span style={{ color: v > 100 ? '#ef4444' : v > 50 ? '#f59e0b' : '#34c759', fontWeight: v > 100 ? 600 : undefined }}>{v}</span>,
                      sorter: (a: any, b: any) => a.avg_time - b.avg_time,
                    },
                    {
                      title: '总耗时 (ms)', dataIndex: 'total_time', key: 'total_time', width: 130,
                      render: (v: number) => v?.toLocaleString(),
                    },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无数据' }}
                />
              )}
            </Card>

            {/* Row 4: Error Paths Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><WarningOutlined /> 错误路径</span>}
              extra={<Tag color="red">{errorPaths.length} 条</Tag>}
              style={{ marginBottom: 16 }}
            >
              {errorPaths.length === 0 ? (
                <Empty description="无错误路径" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={errorPaths}
                  columns={[
                    { title: '路径', dataIndex: 'path', key: 'path' },
                    {
                      title: '状态码', dataIndex: 'status_code', key: 'status_code', width: 100,
                      render: (v: number) => <Tag color={v >= 500 ? 'red' : 'orange'}>{v}</Tag>,
                    },
                    {
                      title: '次数', dataIndex: 'count', key: 'count', width: 100,
                      render: (v: number) => v?.toLocaleString(),
                      sorter: (a: any, b: any) => a.count - b.count,
                      defaultSortOrder: 'descend',
                    },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无数据' }}
                />
              )}
            </Card>

            {/* Row 5: Top IPs Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><GlobalOutlined /> Top 客户端 IP</span>}
              extra={<Tag color="blue">{topIps.length} 个</Tag>}
              style={{ marginBottom: 16 }}
            >
              {topIps.length === 0 ? (
                <Empty description="无 IP 数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={topIps}
                  columns={[
                    { title: 'IP 地址', dataIndex: 'ip', key: 'ip', width: 180 },
                    {
                      title: '请求次数', dataIndex: 'count', key: 'count', width: 120,
                      render: (v: number) => v?.toLocaleString(),
                      sorter: (a: any, b: any) => a.count - b.count,
                      defaultSortOrder: 'descend',
                    },
                    {
                      title: '占比', key: 'percentage', width: 100,
                      render: (_: any, record: any) => {
                        const pct = totalCount > 0 ? ((record.count / totalCount) * 100).toFixed(1) : '0.0'
                        return <span style={{ fontWeight: 600 }}>{pct}%</span>
                      },
                    },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无数据' }}
                />
              )}
            </Card>

            {/* QPS Trend Mini Table */}
            {qpsTrend.length > 0 && (
              <Card
                className="db-card full-width"
                size="small"
                title={<span><BarChartOutlined /> QPS 趋势（24h）</span>}
                extra={<Tag color="green">{qpsTrend.length} 个点</Tag>}
                style={{ marginBottom: 16 }}
              >
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={qpsTrend}
                  columns={[
                    { title: '小时', dataIndex: 'hour', key: 'hour', width: 80, render: (v: number) => `${String(v).padStart(2, '0')}:00` },
                    {
                      title: 'QPS', dataIndex: 'qps', key: 'qps', width: 120,
                      render: (v: number) => (
                        <span style={{ color: v > 2500 ? '#ef4444' : v > 1500 ? '#f59e0b' : '#34c759', fontWeight: v > 1500 ? 600 : undefined }}>
                          {v?.toLocaleString()}
                        </span>
                      ),
                      sorter: (a: any, b: any) => a.qps - b.qps,
                    },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无数据' }}
                />
              </Card>
            )}
          </>
        )}
      </div>
    </Drawer>
  )
}
