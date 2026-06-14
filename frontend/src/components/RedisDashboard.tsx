// Redis 实时监控面板：连接、内存、键统计等
import { useState, useEffect } from 'react'
import { Button, Tag, Table, Card, Spin, Empty, Tooltip, Statistic, Row, Col, Progress, Alert, Drawer, Switch, Space, Descriptions } from 'antd'
import { ReloadOutlined, BranchesOutlined, HddOutlined, TeamOutlined, KeyOutlined, AimOutlined, WarningOutlined } from '@ant-design/icons'

import { apiCall } from '../api/client'

interface Props {
  open: boolean
  onClose: () => void
}

export default function RedisDashboard({ open, onClose }: Props) {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(false)

  const load = async () => {
    if (!open) return
    setLoading(true)
    setError('')
    try {
      const d = await apiCall<any>('/redis/status')
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

  const memory = data?.memory || {}
  const stats = data?.stats || {}
  const server = data?.server || {}
  const keyspace = data?.keyspace || {}
  const databases = keyspace?.databases || []
  const slowlog = data?.slowlog || []
  const clients = data?.clients || []

  const usedMemoryHuman = data?.used_memory_human || memory?.used_memory_human || 'N/A'
  const totalKeys = data?.total_keys || keyspace?.total_keys || 0
  const connectedClients = data?.connected_clients || stats?.connected_clients || 0
  const hitRatio = data?.hit_ratio || stats?.hit_ratio || 'N/A'
  const uptime = data?.uptime || server?.uptime || 'N/A'
  const version = data?.version || server?.version || 'N/A'
  const os = server?.os || 'N/A'
  const tcpPort = server?.tcp_port || 6379
  const memFragRatio = memory?.mem_fragmentation_ratio || 0
  const usedMemoryPeak = memory?.used_memory_peak_human || 'N/A'
  const totalCommands = stats?.total_commands_processed || 0
  const opsPerSec = stats?.instantaneous_ops_per_sec || 0

  // Compute memory usage percentage (mock maxmemory=512M)
  const maxMemory = memory?.maxmemory_human ? parseFloat(memory.maxmemory_human) : 512
  const usedMemMatch = typeof usedMemoryHuman === 'string' ? usedMemoryHuman.match(/^[\d.]+/) : null
  const usedMemVal = usedMemMatch ? parseFloat(usedMemMatch[0]) : 0
  const memoryUsagePct = maxMemory > 0 ? Math.min(Math.round((usedMemVal / maxMemory) * 100), 100) : 0

  return (
    <Drawer
      title={<span><BranchesOutlined /> Redis 监控</span>}
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
              image={<BranchesOutlined style={{ fontSize: 48, color: 'var(--text2)' }} />}
              description={
                <span>
                  <div className="db-empty-title">未配置 Redis 连接</div>
                  <div className="db-empty-desc">
                    请在系统设置中添加一个 Redis 类型的连接
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
                    title={<span><HddOutlined /> 内存使用</span>}
                    value={usedMemoryHuman}
                    suffix={`/ ${maxMemory}M`}
                    valueStyle={{ fontSize: 22, color: memoryUsagePct > 80 ? '#ef4444' : memoryUsagePct > 60 ? '#f59e0b' : '#34c759' }}
                  />
                  <Progress
                    percent={memoryUsagePct}
                    status={memoryUsagePct > 80 ? 'exception' : memoryUsagePct > 60 ? 'active' : 'success'}
                    size="small"
                    style={{ marginTop: 8 }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    峰值: {usedMemoryPeak} · 碎片: {memFragRatio}
                  </div>
                </Card>
              </Col>
              <Col span={6}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><TeamOutlined /> 已连接客户端</span>}
                    value={connectedClients}
                    valueStyle={{ fontSize: 22, color: connectedClients > 100 ? '#ef4444' : '#34c759' }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    每秒操作: {opsPerSec.toLocaleString()} · 命令总数: {(totalCommands / 10000).toFixed(0)}w
                  </div>
                </Card>
              </Col>
              <Col span={6}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><KeyOutlined /> Key 总数</span>}
                    value={totalKeys}
                    valueStyle={{ fontSize: 22, color: '#0d9488' }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    已过期: {stats?.expired_keys || 0} · 已驱逐: {stats?.evicted_keys || 0}
                  </div>
                </Card>
              </Col>
              <Col span={6}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><AimOutlined /> 命中率</span>}
                    value={hitRatio}
                    valueStyle={{ fontSize: 22, color: '#34c759' }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--text2)', marginTop: 4 }}>
                    命中: {stats?.keyspace_hits?.toLocaleString() || 0} · 未命中: {stats?.keyspace_misses?.toLocaleString() || 0}
                  </div>
                </Card>
              </Col>
            </Row>

            {/* Server Info */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><BranchesOutlined /> 服务信息</span>}
              style={{ marginBottom: 16 }}
            >
              <Descriptions size="small" column={4} bordered>
                <Descriptions.Item label="版本">{version}</Descriptions.Item>
                <Descriptions.Item label="运行时间">{uptime}</Descriptions.Item>
                <Descriptions.Item label="操作系统">{os}</Descriptions.Item>
                <Descriptions.Item label="端口">{tcpPort}</Descriptions.Item>
                <Descriptions.Item label="架构">{server?.arch_bits || 64}-bit</Descriptions.Item>
                <Descriptions.Item label="多路复用">{server?.multiplexing_api || 'N/A'}</Descriptions.Item>
                <Descriptions.Item label="进程 ID">{server?.process_id || '-'}</Descriptions.Item>
                <Descriptions.Item label="淘汰策略">{memory?.maxmemory_policy || 'N/A'}</Descriptions.Item>
              </Descriptions>
            </Card>

            {/* Slow Log Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><WarningOutlined /> 慢查询日志</span>}
              extra={<Tag color={slowlog.length > 0 ? 'red' : 'green'}>{slowlog.length} 条</Tag>}
              style={{ marginBottom: 16 }}
            >
              {slowlog.length === 0 ? (
                <Empty description="无慢查询日志" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={slowlog}
                  columns={[
                    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
                    {
                      title: '耗时 (us)', dataIndex: 'duration_us', key: 'duration_us', width: 120,
                      render: (v: number) => (
                        <span style={{ color: v > 10000 ? '#ef4444' : v > 5000 ? '#f59e0b' : undefined, fontWeight: v > 5000 ? 600 : undefined }}>
                          {v?.toLocaleString()}
                        </span>
                      ),
                    },
                    { title: '命令', dataIndex: 'command_args', key: 'command_args' },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无慢查询' }}
                />
              )}
            </Card>

            {/* Key Space Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><KeyOutlined /> Key 空间</span>}
              extra={<Tag color="blue">{totalKeys} keys</Tag>}
              style={{ marginBottom: 16 }}
            >
              {databases.length === 0 ? (
                <Empty description="无 Key 空间数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={databases}
                  columns={[
                    { title: '数据库', dataIndex: 'db', key: 'db', width: 80 },
                    {
                      title: 'Key 数量', dataIndex: 'keys', key: 'keys', width: 100,
                      render: (v: number) => v > 0 ? <span style={{ fontWeight: 600 }}>{v?.toLocaleString()}</span> : v,
                    },
                    { title: '过期 Key', dataIndex: 'expires', key: 'expires', width: 100 },
                    {
                      title: '平均 TTL', dataIndex: 'avg_ttl', key: 'avg_ttl', width: 100,
                      render: (v: number) => {
                        if (v < 0) return <span style={{ color: 'var(--text2)' }}>-</span>
                        if (v >= 86400) return `${(v / 86400).toFixed(0)}d`
                        if (v >= 3600) return `${(v / 3600).toFixed(0)}h`
                        if (v >= 60) return `${(v / 60).toFixed(0)}m`
                        return `${v}s`
                      },
                    },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无数据' }}
                />
              )}
            </Card>

            {/* Client List */}
            {clients.length > 0 && (
              <Card
                className="db-card full-width"
                size="small"
                title={<span><TeamOutlined /> 客户端连接</span>}
                extra={<Tag color="blue">{clients.length} 个</Tag>}
                style={{ marginBottom: 16 }}
              >
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={clients}
                  columns={[
                    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
                    { title: '地址', dataIndex: 'addr', key: 'addr', width: 180 },
                    { title: '文件描述符', dataIndex: 'fd', key: 'fd', width: 100 },
                    { title: '连接时长', dataIndex: 'age', key: 'age', width: 100, render: (v: number) => `${v}s` },
                    { title: '数据库', dataIndex: 'db', key: 'db', width: 80 },
                    { title: '订阅', dataIndex: 'sub', key: 'sub', width: 80 },
                    { title: '标志', dataIndex: 'flags', key: 'flags', width: 80 },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无客户端连接' }}
                />
              </Card>
            )}
          </>
        )}
      </div>
    </Drawer>
  )
}
