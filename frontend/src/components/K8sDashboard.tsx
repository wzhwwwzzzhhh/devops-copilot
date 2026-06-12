import { useState, useEffect } from 'react'
import { Button, Tag, Table, Card, Spin, Empty, Tooltip, Statistic, Row, Col, Progress, Alert, Drawer, Switch, Space } from 'antd'
import { ReloadOutlined, ClusterOutlined, HddOutlined, AppstoreOutlined, CheckCircleOutlined, ClockCircleOutlined, CloseCircleOutlined } from '@ant-design/icons'

import { apiCall } from '../api/client'

interface Props {
  open: boolean
  onClose: () => void
}

export default function K8sDashboard({ open, onClose }: Props) {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(false)

  const load = async () => {
    if (!open) return
    setLoading(true)
    setError('')
    try {
      const d = await apiCall<any>('/k8s/status')
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

  const cluster = data?.cluster || {}
  const nodes = data?.nodes || []
  const pods = data?.pods || []
  const events = data?.events || []
  const deployments = data?.deployments || []

  const totalNodes = cluster?.node_count || 0
  const totalPods = cluster?.total_pods || 0
  const runningPods = cluster?.running_pods || 0
  const pendingPods = cluster?.pending_pods || 0
  const failedPods = cluster?.failed_pods || 0

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'Running': return 'green'
      case 'Pending': return 'orange'
      case 'Failed': return 'red'
      case 'Ready': return 'green'
      default: return 'default'
    }
  }

  return (
    <Drawer
      title={<span><ClusterOutlined /> K8s 集群监控</span>}
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

        {!loading && data?.error && !data?.cluster && (
          <Alert
            message="查询失败"
            description={error || data?.error}
            type="error"
            showIcon
            className="db-card full-width"
            style={{ textAlign: 'center' }}
          />
        )}

        {!loading && data?.cluster && (
          <>
            {/* Stat Cards Row */}
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={4}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><HddOutlined /> Nodes</span>}
                    value={totalNodes}
                    valueStyle={{ fontSize: 22, color: '#0d9488' }}
                  />
                </Card>
              </Col>
              <Col span={5}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><AppstoreOutlined /> 总 Pods</span>}
                    value={totalPods}
                    valueStyle={{ fontSize: 22 }}
                  />
                </Card>
              </Col>
              <Col span={5}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><CheckCircleOutlined /> Running</span>}
                    value={runningPods}
                    valueStyle={{ fontSize: 22, color: '#34c759' }}
                  />
                </Card>
              </Col>
              <Col span={5}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><ClockCircleOutlined /> Pending</span>}
                    value={pendingPods}
                    valueStyle={{ fontSize: 22, color: '#f59e0b' }}
                  />
                </Card>
              </Col>
              <Col span={5}>
                <Card className="db-card" size="small">
                  <Statistic
                    title={<span><CloseCircleOutlined /> Failed</span>}
                    value={failedPods}
                    valueStyle={{ fontSize: 22, color: '#ef4444' }}
                  />
                </Card>
              </Col>
            </Row>

            {/* Node Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><HddOutlined /> 节点列表</span>}
              extra={<Tag color="blue">{nodes.length} 个节点</Tag>}
              style={{ marginBottom: 16 }}
            >
              {nodes.length === 0 ? (
                <Empty description="无节点数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={nodes}
                  columns={[
                    { title: '名称', dataIndex: 'name', key: 'name', width: 140 },
                    {
                      title: '角色', dataIndex: 'role', key: 'role', width: 100,
                      render: (v: string) => <Tag>{v}</Tag>,
                    },
                    {
                      title: '状态', dataIndex: 'status', key: 'status', width: 100,
                      render: (v: string) => <Tag color={getStatusColor(v)}>{v}</Tag>,
                    },
                    {
                      title: 'CPU', dataIndex: 'cpu_percent', key: 'cpu_percent', width: 180,
                      render: (v: number) => (
                        <Progress
                          percent={v}
                          status={v > 80 ? 'exception' : v > 60 ? 'active' : 'success'}
                          size="small"
                          format={(pct) => `${pct}%`}
                        />
                      ),
                    },
                    {
                      title: '内存', dataIndex: 'mem_percent', key: 'mem_percent', width: 180,
                      render: (v: number) => (
                        <Progress
                          percent={v}
                          status={v > 80 ? 'exception' : v > 60 ? 'active' : 'success'}
                          size="small"
                          format={(pct) => `${pct}%`}
                        />
                      ),
                    },
                    { title: 'Kubelet 版本', dataIndex: 'kubelet_version', key: 'kubelet_version', width: 140 },
                    {
                      title: '警告', dataIndex: 'warning', key: 'warning', width: 180,
                      render: (v: string) => v ? <Tag color="red">{v}</Tag> : null,
                    },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无节点' }}
                />
              )}
            </Card>

            {/* Pod Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><AppstoreOutlined /> Pod 列表</span>}
              extra={<Tag color="blue">{pods.length} 个</Tag>}
              style={{ marginBottom: 16 }}
            >
              {pods.length === 0 ? (
                <Empty description="无 Pod 数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={pods}
                  columns={[
                    { title: '名称', dataIndex: 'name', key: 'name', width: 280, ellipsis: true },
                    { title: '命名空间', dataIndex: 'namespace', key: 'namespace', width: 120 },
                    {
                      title: '状态', dataIndex: 'status', key: 'status', width: 100,
                      render: (v: string, record: any) => {
                        const reason = record.reason
                        return (
                          <Tooltip title={reason || v}>
                            <Tag color={getStatusColor(v)}>{reason ? `${v} (${reason})` : v}</Tag>
                          </Tooltip>
                        )
                      },
                    },
                    { title: '镜像', dataIndex: 'image', key: 'image', width: 200, ellipsis: true },
                    {
                      title: '重启次数', dataIndex: 'restarts', key: 'restarts', width: 100,
                      render: (v: number) => (
                        <span style={{ color: v > 3 ? '#ef4444' : v > 0 ? '#f59e0b' : undefined, fontWeight: v > 0 ? 600 : undefined }}>
                          {v}
                        </span>
                      ),
                    },
                    { title: '节点', dataIndex: 'node', key: 'node', width: 120 },
                    { title: '存活时间', dataIndex: 'age', key: 'age', width: 100 },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无 Pod' }}
                />
              )}
            </Card>

            {/* Events Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><ClockCircleOutlined /> 事件</span>}
              extra={<Tag color={events.length > 0 ? 'red' : 'green'}>{events.length} 条</Tag>}
              style={{ marginBottom: 16 }}
            >
              {events.length === 0 ? (
                <Empty description="无事件" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={events}
                  columns={[
                    { title: '类型', dataIndex: 'type', key: 'type', width: 100, render: (v: string) => <Tag color={v === 'Warning' ? 'red' : 'blue'}>{v}</Tag> },
                    { title: '原因', dataIndex: 'reason', key: 'reason', width: 140 },
                    { title: '来源', dataIndex: 'source', key: 'source', width: 180 },
                    { title: '消息', dataIndex: 'message', key: 'message', ellipsis: true },
                    { title: '次数', dataIndex: 'count', key: 'count', width: 80, render: (v: number) => <span style={{ fontWeight: 600 }}>{v}</span> },
                    { title: '最近发生', dataIndex: 'last_seen', key: 'last_seen', width: 100 },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无事件' }}
                />
              )}
            </Card>

            {/* Deployments Table */}
            <Card
              className="db-card full-width"
              size="small"
              title={<span><AppstoreOutlined /> 部署</span>}
              extra={<Tag color="blue">{deployments.length} 个</Tag>}
              style={{ marginBottom: 16 }}
            >
              {deployments.length === 0 ? (
                <Empty description="无部署数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Table
                  rowKey={(_, i) => String(i)}
                  dataSource={deployments}
                  columns={[
                    { title: '名称', dataIndex: 'name', key: 'name', width: 180 },
                    { title: '期望副本', dataIndex: 'desired', key: 'desired', width: 100 },
                    { title: '当前副本', dataIndex: 'current', key: 'current', width: 100 },
                    { title: '已更新', dataIndex: 'up_to_date', key: 'up_to_date', width: 100 },
                    {
                      title: '可用', dataIndex: 'available', key: 'available', width: 100,
                      render: (v: number, record: any) => {
                        const isFull = v >= record.desired
                        return <span style={{ color: isFull ? '#34c759' : '#ef4444', fontWeight: 600 }}>{v}</span>
                      },
                    },
                    { title: '存活时间', dataIndex: 'age', key: 'age', width: 100 },
                  ]}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: '无部署' }}
                />
              )}
            </Card>
          </>
        )}
      </div>
    </Drawer>
  )
}
