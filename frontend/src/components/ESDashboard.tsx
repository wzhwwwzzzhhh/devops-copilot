import { useState, useEffect } from 'react'
import { Button, Tag, Table, Card, Spin, Empty, Tooltip, Statistic, Row, Col, Progress, Alert, Drawer, Switch, Space, Descriptions } from 'antd'
import { ReloadOutlined, DatabaseOutlined, HddOutlined, TeamOutlined, KeyOutlined, WarningOutlined, ApiOutlined, DashboardOutlined } from '@ant-design/icons'

import { apiCall } from '../api/client'

interface Props {
  open: boolean
  onClose: () => void
}

export default function ESDashboard({ open, onClose }: Props) {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(false)

  const load = async () => {
    if (!open) return
    setLoading(true)
    setError('')
    try {
      const d = await apiCall<any>('/es/status')
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

  const clusterHealth = data?.cluster_health || {}
  const indices = data?.indices || []
  const nodes = data?.nodes || []
  const slowLogs = data?.slow_logs || []

  const status = data?.status || clusterHealth?.status || 'unknown'
  const nodesCount = data?.nodes_count || clusterHealth?.nodes || 0
  const indicesCount = data?.indices_count || 0
  const documentsCount = data?.total_documents || 0
  const shardsCount = data?.shards_count || clusterHealth?.active_shards || 0
  const unassignedShards = data?.unassigned_shards || clusterHealth?.unassigned_shards || 0
  const activeShardsPercent = clusterHealth?.active_shards_percent || 0

  const statusColor: Record<string, string> = {
    green: '#34c759',
    yellow: '#f59e0b',
    red: '#ef4444',
    unknown: 'var(--text2)',
  }

  return (
    <Drawer
      title={<span><DatabaseOutlined /> ES 监控</span>}
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
              image={<DatabaseOutlined style={{ fontSize: 48, color: 'var(--text2)' }} />}
              description={
                <span>
                  <div className="db-empty-title">未配置 ES 连接</div>
                  <div className="db-empty-desc">
                    请在系统设置中添加 ES 连接或检查 ES 服务状态
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
            {/* Row 1: Stat Cards */}
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={4}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><DashboardOutlined /> 集群状态</span>}
                    valueRender={() => (
                      <Tag color={statusColor[status] || statusColor.unknown} style={{ fontSize: 16, padding: '4px 16px' }}>
                        {status.toUpperCase()}
                      </Tag>
                    )}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    活跃分片: {activeShardsPercent.toFixed(1)}% · 未分配: {unassignedShards}
                  </div>
                </Card>
              </Col>
              <Col span={5}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><TeamOutlined /> 节点</span>}
                    value={nodesCount}
                    valueStyle={{ fontSize: 22, color: '#0d9488' }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    数据节点: {clusterHealth?.data_nodes || 0}
                  </div>
                </Card>
              </Col>
              <Col span={5}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><DatabaseOutlined /> 索引</span>}
                    value={indicesCount}
                    valueStyle={{ fontSize: 22, color: '#0891b2' }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    主分片: {clusterHealth?.active_primary_shards || 0}
                  </div>
                </Card>
              </Col>
              <Col span={5}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><KeyOutlined /> 文档数</span>}
                    value={documentsCount}
                    valueStyle={{ fontSize: 22, color: '#34c759' }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    约 {documentsCount >= 1000000 ? (documentsCount / 1000000).toFixed(1) + 'M' : documentsCount >= 1000 ? (documentsCount / 1000).toFixed(1) + 'K' : documentsCount} 条
                  </div>
                </Card>
              </Col>
              <Col span={5}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><ApiOutlined /> 分片</span>}
                    value={shardsCount}
                    valueStyle={{ fontSize: 22, color: '#6366f1' }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    未分配: {unassignedShards}
                  </div>
                </Card>
              </Col>
            </Row>

            {/* Row 2: Node Health Cards */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><HddOutlined /> 节点健康</span>}
              extra={<Tag color="blue">{nodes.length} 个节点</Tag>}
              style={{ marginBottom: 16 }}
            >
              {nodes.length === 0 ? (
                <Empty description="无节点数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Row gutter={16}>
                  {nodes.map((node: any, i: number) => {
                    const heapPct = node?.heap_used_percent || 0
                    const cpuPct = node?.cpu_percent || 0
                    const diskPct = node?.disk_used_percent || 0
                    const diskAvail = node?.disk_avail_bytes || 0
                    const diskAvailGB = (diskAvail / (1024 * 1024 * 1024)).toFixed(1)
                    const gcCollectors = node?.gc_collectors || []

                    return (
                      <Col span={8} key={i} style={{ marginBottom: 12 }}>
                        <Card className="db-card" size="small" title={
                          <span style={{ fontWeight: 600 }}>{node.name || 'unknown'}</span>
                        }>
                          <div style={{ marginBottom: 8 }}>
                            <div style={{ fontSize: 12, color: 'var(--text2)', marginBottom: 2 }}>Heap 使用率</div>
                            <Progress
                              percent={Math.round(heapPct)}
                              status={heapPct > 85 ? 'exception' : heapPct > 70 ? 'active' : 'success'}
                              size="small"
                            />
                          </div>
                          <div style={{ marginBottom: 8 }}>
                            <div style={{ fontSize: 12, color: 'var(--text2)', marginBottom: 2 }}>CPU</div>
                            <Progress
                              percent={cpuPct}
                              status={cpuPct > 80 ? 'exception' : cpuPct > 60 ? 'active' : 'success'}
                              size="small"
                            />
                          </div>
                          <div style={{ marginBottom: 8 }}>
                            <div style={{ fontSize: 12, color: 'var(--text2)', marginBottom: 2 }}>磁盘使用率</div>
                            <Progress
                              percent={Math.round(diskPct)}
                              status={diskPct > 85 ? 'exception' : diskPct > 70 ? 'active' : 'success'}
                              size="small"
                            />
                          </div>
                          <div style={{ fontSize: 11, color: 'var(--text2)' }}>
                            可用磁盘: {diskAvailGB} GB
                          </div>
                          {gcCollectors.length > 0 && (
                            <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                              GC: {gcCollectors.map((gc: any) => `${gc.name}=${gc.collection_count}次`).join(' | ')}
                            </div>
                          )}
                        </Card>
                      </Col>
                    )
                  })}
                </Row>
              )}
            </Card>

            {/* Row 3: Indices Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><DatabaseOutlined /> 索引列表</span>}
              extra={<Tag color="blue">{indices.length} 个索引</Tag>}
              style={{ marginBottom: 16 }}
            >
              {indices.length === 0 ? (
                <Empty description="无索引数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={indices}
                  columns={[
                    {
                      title: '健康状态', dataIndex: 'health', key: 'health', width: 100,
                      render: (v: string) => {
                        const dotColor = v === 'green' ? '#34c759' : v === 'yellow' ? '#f59e0b' : '#ef4444'
                        return (
                          <span>
                            <span style={{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%', backgroundColor: dotColor, marginRight: 6 }} />
                            {v}
                          </span>
                        )
                      },
                    },
                    { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
                    { title: '索引名', dataIndex: 'index', key: 'index', ellipsis: true },
                    { title: '主分片', dataIndex: 'pri', key: 'pri', width: 80, align: 'right' },
                    { title: '副本', dataIndex: 'rep', key: 'rep', width: 80, align: 'right' },
                    {
                      title: '文档数', dataIndex: 'docs_count', key: 'docs_count', width: 120, align: 'right',
                      render: (v: number) => v?.toLocaleString(),
                    },
                    { title: '存储大小', dataIndex: 'store_size', key: 'store_size', width: 120, align: 'right' },
                  ]}
                  pagination={{ pageSize: 10, size: 'small', showSizeChanger: false }}
                  size="small"
                  locale={{ emptyText: '无索引' }}
                />
              )}
            </Card>

            {/* Row 4: Slow Logs Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><WarningOutlined /> 慢查询日志</span>}
              extra={<Tag color={slowLogs.length > 0 ? 'red' : 'green'}>{slowLogs.length} 条</Tag>}
              style={{ marginBottom: 16 }}
            >
              {slowLogs.length === 0 ? (
                <Empty description="无慢查询日志" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={slowLogs}
                  columns={[
                    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
                    {
                      title: '耗时 (ms)', dataIndex: 'took_millis', key: 'took_millis', width: 110,
                      render: (v: number) => (
                        <span style={{ color: v > 5000 ? '#ef4444' : v > 2000 ? '#f59e0b' : undefined, fontWeight: v > 2000 ? 600 : undefined }}>
                          {v?.toLocaleString()}
                        </span>
                      ),
                    },
                    { title: '来源', dataIndex: 'source', key: 'source', ellipsis: true },
                    {
                      title: '分片', dataIndex: 'shards_total', key: 'shards_total', width: 80,
                      render: (v: number, record: any) => `${record.shards_successful || 0}/${v}`,
                    },
                    {
                      title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true,
                      render: (v: string) => <span style={{ color: 'var(--text2)', fontSize: 12 }}>{v}</span>,
                    },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无慢查询' }}
                />
              )}
            </Card>
          </>
        )}
      </div>
    </Drawer>
  )
}
