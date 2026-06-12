import { useState, useEffect } from 'react'
import { Button, Tag, Table, Card, Spin, Empty, Tooltip, Statistic, Row, Col, Drawer, Switch, Space } from 'antd'
import { ReloadOutlined, DashboardOutlined } from '@ant-design/icons'
import { apiCall } from '../api/client'

interface Props {
  open: boolean
  onClose: () => void
}

export default function DockerDashboard({ open, onClose }: Props) {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(false)

  const load = async () => {
    if (!open) return
    setLoading(true)
    setError('')
    try {
      const d = await apiCall<any>('/docker/status')
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

  const containers: any[] = data?.containers || []
  const images: any[] = data?.images || []
  const stats: any[] = data?.stats || []

  const runningContainers = data?.running_containers ?? containers.filter((c: any) => {
    const s = (c.Status || '').toLowerCase()
    return s.startsWith('up') || s.includes('running')
  }).length
  const stoppedContainers = data?.stopped_containers ?? (containers.length - runningContainers)
  const totalContainers = data?.total_containers ?? containers.length
  const totalImages = data?.total_images ?? images.length

  return (
    <Drawer
      title={<span><DashboardOutlined /> Docker 管理</span>}
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

        {!loading && error && (
          <Card className="db-card full-width" style={{ textAlign: 'center' }}>
            <Empty
              image={<DashboardOutlined style={{ fontSize: 48, color: 'var(--text2)' }} />}
              description={
                <span>
                  <div className="db-empty-title">查询失败</div>
                  <div className="db-empty-desc">{error}</div>
                </span>
              }
            />
          </Card>
        )}

        {!loading && !error && data && (
          <>
            {/* Stat Cards Row */}
            <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
              <Col span={6}>
                <Card className="db-card" size="small">
                  <Statistic
                    title="运行中"
                    value={runningContainers}
                    valueStyle={{ color: '#34c759', fontSize: 28 }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card className="db-card" size="small">
                  <Statistic
                    title="已停止"
                    value={stoppedContainers}
                    valueStyle={{ color: stoppedContainers > 0 ? '#ef4444' : '#34c759', fontSize: 28 }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card className="db-card" size="small">
                  <Statistic
                    title="容器总数"
                    value={totalContainers}
                    valueStyle={{ color: '#0d9488', fontSize: 28 }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card className="db-card" size="small">
                  <Statistic
                    title="镜像数"
                    value={totalImages}
                    valueStyle={{ color: '#0d9488', fontSize: 28 }}
                  />
                </Card>
              </Col>
            </Row>

            {/* Containers Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><DashboardOutlined /> 容器列表</span>}
              extra={<Tag color="blue">{containers.length} 个</Tag>}
              style={{ marginBottom: 16 }}
            >
              <Table
                rowKey={(_, i) => String(i)}
                dataSource={containers}
                columns={[
                  { title: 'ID', dataIndex: 'ID', key: 'ID', width: 140, render: (v: string) => v ? v.substring(0, 12) : '-' },
                  { title: '镜像', dataIndex: 'Image', key: 'Image', width: 150 },
                  {
                    title: '状态', dataIndex: 'Status', key: 'Status', width: 190,
                    render: (v: string) => {
                      const s = (v || '').toLowerCase()
                      let color = 'green'
                      if (s.startsWith('up')) color = 'green'
                      else if (s.startsWith('exited') || s.startsWith('created')) color = 'orange'
                      else if (s.includes('unhealthy') || s.includes('dead')) color = 'red'
                      else color = 'default'
                      return <Tag color={color}>{v || '-'}</Tag>
                    },
                  },
                  { title: '端口', dataIndex: 'Ports', key: 'Ports', width: 200, render: (v: string) => v || '-' },
                  { title: '创建时间', dataIndex: 'CreatedAt', key: 'CreatedAt', width: 180 },
                  { title: '名称', dataIndex: 'Names', key: 'Names' },
                ]}
                pagination={false}
                size="small"
                locale={{ emptyText: '无容器' }}
              />
            </Card>

            {/* Resource Usage Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><DashboardOutlined /> 资源使用</span>}
              extra={<Tag color="blue">{stats.length} 个</Tag>}
              style={{ marginBottom: 16 }}
            >
              {stats.length === 0 ? (
                <Empty description="无资源使用数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={stats}
                  columns={[
                    { title: '容器名', dataIndex: 'Name', key: 'Name', width: 120 },
                    {
                      title: 'CPU%', dataIndex: 'CPUPerc', key: 'CPUPerc', width: 100,
                      render: (v: string) => {
                        const num = parseFloat(v || '0')
                        return <span style={{ color: num > 10 ? '#ef4444' : num > 5 ? '#f59e0b' : '#34c759', fontWeight: 600 }}>{v}</span>
                      },
                    },
                    {
                      title: 'Mem%', dataIndex: 'MemPerc', key: 'MemPerc', width: 100,
                      render: (v: string) => {
                        const num = parseFloat(v || '0')
                        return <span style={{ color: num > 80 ? '#ef4444' : num > 50 ? '#f59e0b' : '#34c759', fontWeight: 600 }}>{v}</span>
                      },
                    },
                    { title: 'MemUsage', dataIndex: 'MemUsage', key: 'MemUsage', width: 200 },
                    { title: 'NetIO', dataIndex: 'NetIO', key: 'NetIO', width: 180 },
                    { title: 'BlockIO', dataIndex: 'BlockIO', key: 'BlockIO' },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无数据' }}
                />
              )}
            </Card>

            {/* Images Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><DashboardOutlined /> 镜像列表</span>}
              extra={<Tag color="blue">{images.length} 个</Tag>}
              style={{ marginBottom: 16 }}
            >
              {images.length === 0 ? (
                <Empty description="无镜像" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={images}
                  columns={[
                    { title: '仓库', dataIndex: 'Repository', key: 'Repository', width: 150 },
                    { title: '标签', dataIndex: 'Tag', key: 'Tag', width: 100 },
                    { title: '大小', dataIndex: 'Size', key: 'Size', width: 100, render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
                    { title: '创建时间', dataIndex: 'CreatedAt', key: 'CreatedAt' },
                  ]}
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
