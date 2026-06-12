import { useState, useEffect } from 'react'
import { Button, Tag, Table, Card, Spin, Empty, Tooltip, Statistic, Row, Col, Progress, Drawer, Switch, Space } from 'antd'
import { ReloadOutlined, MonitorOutlined, DashboardOutlined, DatabaseOutlined, HddOutlined, WifiOutlined, AppstoreOutlined } from '@ant-design/icons'

import { apiCall } from '../api/client'

interface Props {
  open: boolean
  onClose: () => void
}

export default function SystemDashboard({ open, onClose }: Props) {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(false)

  const load = async () => {
    if (!open) return
    setLoading(true)
    setError('')
    try {
      const d = await apiCall<any>('/system/status')
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

  const cpu = data?.cpu
  const memory = data?.memory
  const disks: any[] = data?.disks || []
  const interfaces: any[] = data?.interfaces || []
  const processes: any[] = data?.processes || []

  const cpuColor = cpu?.usage_percent > 80 ? '#ef4444' : cpu?.usage_percent > 60 ? '#f59e0b' : '#34c759'

  return (
    <Drawer
      title={<span><MonitorOutlined /> 系统监控</span>}
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
              image={<MonitorOutlined style={{ fontSize: 48, color: 'var(--text2)' }} />}
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
            {/* Host Info */}
            <Card className="db-card full-width" size="small" title={<span><MonitorOutlined /> 主机信息</span>}>
              <Row gutter={16}>
                <Col span={8}>
                  <Statistic title="主机名" value={data.hostname || '-'} valueStyle={{ fontSize: 16 }} />
                </Col>
                <Col span={8}>
                  <Statistic title="操作系统" value={data.os || '-'} valueStyle={{ fontSize: 16 }} />
                </Col>
                <Col span={8}>
                  <Statistic title="运行时间" value={data.uptime || '-'} valueStyle={{ fontSize: 16 }} />
                </Col>
              </Row>
            </Card>

            {/* CPU Card */}
            <Card className="db-card" size="small" title={<span><DashboardOutlined /> CPU</span>}>
              <Row gutter={16}>
                <Col span={12}>
                  <Statistic
                    title="使用率"
                    value={cpu?.usage_percent || 0}
                    suffix="%"
                    valueStyle={{ color: cpuColor, fontSize: 28 }}
                  />
                </Col>
                <Col span={12}>
                  <Statistic title="核心数" value={cpu?.core_count || 0} suffix="核" />
                </Col>
              </Row>
              <div style={{ marginTop: 12 }}>
                <Row gutter={16}>
                  <Col span={8}>
                    <Statistic title="1 分钟负载" value={cpu?.load_1min || 0} valueStyle={{ fontSize: 14 }} />
                  </Col>
                  <Col span={8}>
                    <Statistic title="5 分钟负载" value={cpu?.load_5min || 0} valueStyle={{ fontSize: 14 }} />
                  </Col>
                  <Col span={8}>
                    <Statistic title="15 分钟负载" value={cpu?.load_15min || 0} valueStyle={{ fontSize: 14 }} />
                  </Col>
                </Row>
              </div>
              {cpu?.top_processes && cpu.top_processes.length > 0 && (
                <div style={{ marginTop: 12 }}>
                  <Tag style={{ marginBottom: 4 }}>CPU TOP 5</Tag>
                  <Table
                    rowKey={(_, i) => String(i)}
                    dataSource={cpu.top_processes}
                    columns={[
                      { title: 'PID', dataIndex: 'pid', key: 'pid', width: 60 },
                      { title: '进程名', dataIndex: 'name', key: 'name', width: 80 },
                      { title: 'CPU%', dataIndex: 'cpu_percent', key: 'cpu_percent', width: 70, render: (v: number) => <span style={{ fontWeight: 600 }}>{v}%</span> },
                      { title: '用户', dataIndex: 'user', key: 'user', width: 80 },
                    ]}
                    pagination={false}
                    size="small"
                    locale={{ emptyText: '无数据' }}
                  />
                </div>
              )}
            </Card>

            {/* Memory Card */}
            <Card className="db-card" size="small" title={<span><DatabaseOutlined /> 内存</span>}>
              <Row gutter={16}>
                <Col span={6}>
                  <Statistic title="总量" value={memory?.total || '-'} />
                </Col>
                <Col span={6}>
                  <Statistic title="已用" value={memory?.used || '-'} valueStyle={{ color: '#ef4444' }} />
                </Col>
                <Col span={6}>
                  <Statistic title="可用" value={memory?.available || '-'} valueStyle={{ color: '#34c759' }} />
                </Col>
                <Col span={6}>
                  <Statistic title="使用率" value={memory?.usage_percent || 0} suffix="%" valueStyle={{ color: memory?.usage_percent > 80 ? '#ef4444' : memory?.usage_percent > 60 ? '#f59e0b' : '#34c759' }} />
                </Col>
              </Row>
              <Progress
                percent={Math.min(Math.round(memory?.usage_percent || 0), 100)}
                status={memory?.usage_percent > 80 ? 'exception' : memory?.usage_percent > 60 ? 'active' : 'success'}
                style={{ marginTop: 12 }}
              />
              <Row gutter={16} style={{ marginTop: 8 }}>
                <Col span={12}>
                  <Statistic title="交换分区总量" value={memory?.swap_total || '-'} valueStyle={{ fontSize: 14 }} />
                </Col>
                <Col span={12}>
                  <Statistic title="交换分区已用" value={memory?.swap_used || '-'} valueStyle={{ fontSize: 14, color: memory?.swap_used !== '0G' ? '#f59e0b' : undefined }} />
                </Col>
              </Row>
            </Card>

            {/* Disk Card */}
            <Card className="db-card full-width" size="small" title={<span><HddOutlined /> 磁盘</span>}>
              <Table
                rowKey={(_, i) => String(i)}
                dataSource={disks}
                columns={[
                  { title: '文件系统', dataIndex: 'filesystem', key: 'filesystem', width: 100 },
                  { title: '挂载点', dataIndex: 'mounted_on', key: 'mounted_on', width: 80 },
                  { title: '总量', dataIndex: 'total', key: 'total', width: 80 },
                  { title: '已用', dataIndex: 'used', key: 'used', width: 80 },
                  { title: '可用', dataIndex: 'avail', key: 'avail', width: 80 },
                  {
                    title: '使用率', dataIndex: 'use_percent', key: 'use_percent', width: 200,
                    render: (v: number) => (
                      <Space>
                        <Progress
                          percent={Math.min(Math.round(v), 100)}
                          size="small"
                          style={{ width: 100 }}
                          strokeColor={v > 80 ? '#ef4444' : v > 60 ? '#f59e0b' : '#34c759'}
                        />
                        <span style={{ color: v > 80 ? '#ef4444' : v > 60 ? '#f59e0b' : '#34c759', fontWeight: 600 }}>{v}%</span>
                      </Space>
                    ),
                  },
                ]}
                pagination={false}
                size="small"
                locale={{ emptyText: '无磁盘数据' }}
              />
            </Card>

            {/* Network Card */}
            <Card className="db-card full-width" size="small" title={<span><WifiOutlined /> 网络</span>}>
              <Table
                rowKey={(_, i) => String(i)}
                dataSource={interfaces}
                columns={[
                  { title: '接口', dataIndex: 'interface', key: 'interface', width: 80 },
                  { title: '接收字节', dataIndex: 'rx_bytes', key: 'rx_bytes', width: 130, render: (v: number) => formatBytes(v) },
                  { title: '发送字节', dataIndex: 'tx_bytes', key: 'tx_bytes', width: 130, render: (v: number) => formatBytes(v) },
                  { title: '接收包', dataIndex: 'rx_packets', key: 'rx_packets', width: 110, render: (v: number) => v.toLocaleString() },
                  { title: '发送包', dataIndex: 'tx_packets', key: 'tx_packets', width: 110, render: (v: number) => v.toLocaleString() },
                  {
                    title: '错误', dataIndex: 'errors', key: 'errors', width: 60,
                    render: (v: number) => <span style={{ color: v > 0 ? '#ef4444' : '#34c759', fontWeight: v > 0 ? 600 : undefined }}>{v}</span>,
                  },
                  {
                    title: '丢包', dataIndex: 'drops', key: 'drops', width: 60,
                    render: (v: number) => <span style={{ color: v > 0 ? '#f59e0b' : '#34c759', fontWeight: v > 0 ? 600 : undefined }}>{v}</span>,
                  },
                ]}
                pagination={false}
                size="small"
                locale={{ emptyText: '无网络数据' }}
              />
            </Card>

            {/* Processes Card */}
            <Card className="db-card full-width" size="small" title={<span><AppstoreOutlined /> 进程 TOP 10</span>}>
              <Table
                rowKey={(_, i) => String(i)}
                dataSource={processes}
                columns={[
                  { title: 'PID', dataIndex: 'pid', key: 'pid', width: 60 },
                  { title: '名称', dataIndex: 'name', key: 'name', width: 80 },
                  { title: 'CPU%', dataIndex: 'cpu_percent', key: 'cpu_percent', width: 70, render: (v: number) => <span style={{ color: v > 10 ? '#ef4444' : v > 5 ? '#f59e0b' : undefined, fontWeight: 600 }}>{v}%</span> },
                  { title: 'MEM%', dataIndex: 'mem_percent', key: 'mem_percent', width: 70, render: (v: number) => <span style={{ fontWeight: 600 }}>{v}%</span> },
                  { title: 'RSS', dataIndex: 'rss', key: 'rss', width: 80, render: (v: number) => formatBytes(v * 1024) },
                  { title: '用户', dataIndex: 'user', key: 'user', width: 80 },
                  { title: '命令行', dataIndex: 'command', key: 'command', render: (v: string) => <Tooltip title={v}><span style={{ fontSize: 12 }}>{(v || '').substring(0, 60)}</span></Tooltip> },
                ]}
                pagination={false}
                size="small"
                locale={{ emptyText: '无进程数据' }}
              />
            </Card>
          </>
        )}
      </div>
    </Drawer>
  )
}

/** Format bytes to human-readable string */
function formatBytes(bytes: number): string {
  if (bytes === 0) return '0B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(k)), units.length - 1)
  const val = bytes / Math.pow(k, i)
  return val.toFixed(1) + units[i]
}
