// 经验记忆库：展示 Agent 自动保存的故障排查经验记录
import { useState, useEffect } from 'react'
import { Button, Tag, Card, Spin, Empty, Alert, Drawer, Space, Input, Select, Statistic, Row, Col, message, Typography, Descriptions, Modal } from 'antd'
import { ReloadOutlined, ExperimentOutlined, SearchOutlined, DeleteOutlined, BulbOutlined, ClockCircleOutlined, CheckCircleOutlined, ToolOutlined, DatabaseOutlined } from '@ant-design/icons'
import { apiCall } from '../api/client'

interface Props {
  open: boolean
  onClose: () => void
}

const PROBLEM_TYPES = [
  { value: '', label: '全部类型' },
  { value: 'CONNECTION_POOL', label: '连接池耗尽', color: 'red' },
  { value: 'DEADLOCK', label: '死锁', color: 'volcano' },
  { value: 'SLOW_QUERY', label: '慢查询', color: 'orange' },
  { value: 'LOCK_WAIT', label: '锁等待', color: 'gold' },
  { value: 'HIGH_ERROR_RATE', label: '高错误率', color: 'magenta' },
  { value: 'DISK_FULL', label: '磁盘满', color: 'purple' },
  { value: 'MEMORY_HIGH', label: '内存高', color: 'cyan' },
  { value: 'DEPLOYMENT', label: '部署问题', color: 'blue' },
  { value: 'NETWORK', label: '网络问题', color: 'geekblue' },
  { value: 'UNKNOWN', label: '未知问题', color: 'default' },
]

export default function ExperiencePanel({ open, onClose }: Props) {
  const [experiences, setExperiences] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [searchText, setSearchText] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [selectedExp, setSelectedExp] = useState<any>(null)
  const [detailOpen, setDetailOpen] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const d = await apiCall<any[]>('/knowledge/experiences')
      setExperiences(Array.isArray(d) ? d : [])
    } catch {
      setExperiences([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) load()
  }, [open])

  // 搜索经验
  const handleSearch = async () => {
    if (!searchText.trim()) { load(); return }
    setLoading(true)
    try {
      const res = await apiCall<any>(`/knowledge/experiences/search?q=${encodeURIComponent(searchText)}`)
      // The search result is a formatted string; for better UX, filter locally
      setExperiences(prev => prev.filter(e =>
        (e.root_cause || '').includes(searchText) ||
        (e.fix_action || '').includes(searchText) ||
        (e.type || '').includes(searchText)
      ))
    } catch {
      message.error('搜索失败')
    } finally {
      setLoading(false)
    }
  }

  // 筛选
  const filtered = experiences.filter(e => {
    if (typeFilter && e.type !== typeFilter) return false
    if (searchText) {
      const s = searchText.toLowerCase()
      return (e.root_cause || '').toLowerCase().includes(s) ||
             (e.fix_action || '').toLowerCase().includes(s) ||
             (e.service || '').toLowerCase().includes(s)
    }
    return true
  })

  const typeColor = (type: string) => {
    const found = PROBLEM_TYPES.find(t => t.value === type)
    return found?.color || 'default'
  }

  const typeLabel = (type: string) => {
    const found = PROBLEM_TYPES.find(t => t.value === type)
    return found?.label || type || '未知'
  }

  const openDetail = (exp: any) => {
    setSelectedExp(exp)
    setDetailOpen(true)
  }

  const statData = [
    { label: '总记录', value: experiences.length },
    { label: '过滤后', value: filtered.length },
    { label: '类型数', value: new Set(experiences.map(e => e.type)).size },
  ]

  return (
    <>
      <Drawer
        title={<span><ExperimentOutlined /> 经验记忆库</span>}
        placement="bottom"
        height="100vh"
        open={open}
        onClose={onClose}
        extra={
          <Space>
            <Tag color="blue">{filtered.length} 条</Tag>
            <Button type="text" icon={<ReloadOutlined />} onClick={load} />
          </Space>
        }
      >
        <div className="exp-body">
          {/* Stats */}
          <Row gutter={12} style={{ marginBottom: 16 }}>
            {statData.map((s, i) => (
              <Col span={8} key={i}>
                <Card size="small">
                  <Statistic title={s.label} value={s.value} valueStyle={{ fontSize: 22 }} />
                </Card>
              </Col>
            ))}
          </Row>

          {/* Search & Filter */}
          <Card size="small" style={{ marginBottom: 16 }}>
            <Space style={{ width: '100%' }}>
              <Input
                placeholder="搜索根因、修复方案、服务名..."
                prefix={<SearchOutlined />}
                value={searchText}
                onChange={e => setSearchText(e.target.value)}
                onPressEnter={handleSearch}
                style={{ width: 300 }}
                allowClear
              />
              <Select
                value={typeFilter}
                onChange={setTypeFilter}
                options={PROBLEM_TYPES.map(t => ({ value: t.value, label: t.label }))}
                style={{ width: 150 }}
                placeholder="按类型筛选"
              />
              <Button onClick={handleSearch} type="primary" icon={<SearchOutlined />}>搜索</Button>
              <Button onClick={load} icon={<ReloadOutlined />}>刷新</Button>
            </Space>
          </Card>

          {/* Loading */}
          {loading && (
            <div style={{ textAlign: 'center', padding: 40 }}>
              <Spin size="large" />
            </div>
          )}

          {/* Empty */}
          {!loading && filtered.length === 0 && (
            <Empty description={experiences.length === 0 ? '暂无历史经验数据，排查并修复问题后将自动保存' : '无匹配结果'}>
              {experiences.length === 0 && (
                <div style={{ fontSize: 12, color: 'var(--text2)', marginTop: 8 }}>
                  Agent 解决故障后会自动将经验存入 Elasticsearch
                </div>
              )}
            </Empty>
          )}

          {/* Experience List */}
          {!loading && filtered.map((exp, i) => (
            <Card
              key={i}
              size="small"
              className="exp-card"
              hoverable
              onClick={() => openDetail(exp)}
              style={{ marginBottom: 8 }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ flex: 1 }}>
                  <Space style={{ marginBottom: 6 }}>
                    <Tag color={typeColor(exp.type)}>{typeLabel(exp.type)}</Tag>
                    {exp.service && <Tag>{exp.service}</Tag>}
                    {exp.hit_count && <Tag color="blue">复用 {exp.hit_count} 次</Tag>}
                  </Space>
                  <div style={{ fontSize: 13, marginBottom: 4 }}>
                    <Typography.Text strong>根因：</Typography.Text>
                    <Typography.Text>{exp.root_cause?.substring(0, 120)}</Typography.Text>
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text2)' }}>
                    <Typography.Text type="secondary">修复：</Typography.Text>
                    {exp.fix_action?.substring(0, 100)}
                  </div>
                </div>
                <div style={{ textAlign: 'right', minWidth: 140, paddingLeft: 12 }}>
                  <div style={{ fontSize: 11, color: 'var(--text2)' }}>{exp.resolved_at || '-'}</div>
                </div>
              </div>
            </Card>
          ))}
        </div>
      </Drawer>

      {/* Detail Drawer */}
      <Drawer
        title={<span><BulbOutlined /> 经验详情</span>}
        placement="right"
        width={520}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
      >
        {selectedExp && (
          <div>
            <Descriptions column={1} bordered size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="问题类型">
                <Tag color={typeColor(selectedExp.type)}>{typeLabel(selectedExp.type)}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="涉及服务">
                {selectedExp.service || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="解决时间">
                {selectedExp.resolved_at || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="复用次数">
                {selectedExp.hit_count || 0} 次
              </Descriptions.Item>
            </Descriptions>

            <Card size="small" title={<span><ToolOutlined /> 根因</span>} style={{ marginBottom: 12 }}>
              <Typography.Text>{selectedExp.root_cause || '未记录'}</Typography.Text>
            </Card>

            <Card size="small" title={<span><DatabaseOutlined /> 修复方案</span>} style={{ marginBottom: 12 }}>
              <Typography.Text>{selectedExp.fix_action || '未记录'}</Typography.Text>
            </Card>

            {selectedExp.fix_sql && (
              <Card size="small" title={<span><DatabaseOutlined /> 修复 SQL</span>} style={{ marginBottom: 12 }}>
                <pre style={{ fontSize: 12, background: '#f5f5f5', padding: 8, borderRadius: 4, overflow: 'auto' }}>
                  {selectedExp.fix_sql}
                </pre>
              </Card>
            )}
          </div>
        )}
      </Drawer>
    </>
  )
}
