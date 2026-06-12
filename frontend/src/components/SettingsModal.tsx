import { useState, useEffect } from 'react'
import { Modal, Tabs, Form, Input, Select, Button, Table, Tag, message, Popconfirm, Space, Tooltip, Divider, Alert } from 'antd'
import { PlusOutlined, DeleteOutlined, ThunderboltOutlined, SearchOutlined, LinkOutlined, KeyOutlined, BookOutlined } from '@ant-design/icons'
import { apiCall } from '../api/client'

interface Props {
  activeTab: string | null
  onClose: () => void
  onRefresh: () => void
}

const CONN_TYPE_OPTIONS = [
  { value: 'mysql', label: 'MySQL 数据库' },
  { value: 'redis', label: 'Redis 缓存' },
  { value: 'prometheus', label: 'Prometheus 监控' },
  { value: 'k8s', label: 'Kubernetes 集群' },
  { value: 'log', label: '日志文件' },
  { value: 'es', label: 'Elasticsearch' },
  { value: 'service', label: '微服务' },
]

const CONN_TYPE_DEFAULTS: Record<string, { port: number; hint: string }> = {
  mysql:      { port: 3306, hint: 'MySQL: host + port(3306) + username + password <b>必填</b>' },
  redis:     { port: 6379, hint: 'Redis: host + port(6379) 必填，password 可选' },
  es:        { port: 9200, hint: 'Elasticsearch: host + port(9200) 必填' },
  prometheus: { port: 9090, hint: 'Prometheus: host + port(9090) 必填，无需认证' },
  k8s:       { port: 6443, hint: 'Kubernetes: host + port(6443) 必填' },
  log:       { port: 0,   hint: '日志文件: properties 中指定 logPath' },
  service:   { port: 8080, hint: '微服务: host + port 必填' },
}

export default function SettingsModal({ activeTab, onClose, onRefresh }: Props) {
  const [tab, setTab] = useState('connections')
  const [conns, setConns] = useState<any[]>([])
  const [models, setModels] = useState<any[]>([])
  const [docs, setDocs] = useState<any[]>([])

  // Conn form
  const [connForm] = Form.useForm()
  const [connType, setConnType] = useState('mysql')

  // Model form
  const [modelForm] = Form.useForm()

  // KB form
  const [kbForm] = Form.useForm()
  const [kbSearch, setKbSearch] = useState('')

  useEffect(() => {
    if (activeTab) {
      setTab(activeTab)
      loadData()
    }
  }, [activeTab])

  const loadData = () => {
    loadConns()
    loadModels()
    loadDocs()
  }

  const loadConns = async () => {
    try { const d = await apiCall('/connections'); setConns(Array.isArray(d) ? d : []) } catch { setConns([]) }
  }
  const loadModels = async () => {
    try { const d = await apiCall('/models'); setModels(Array.isArray(d) ? d : []) } catch { setModels([]) }
  }
  const loadDocs = async () => {
    try {
      const d = await apiCall('/knowledge/list?size=50')
      setDocs(Array.isArray(d) ? d : [])
    } catch { setDocs([]) }
  }

  const connTypeHint = CONN_TYPE_DEFAULTS[connType]?.hint || ''

  const saveConnection = async () => {
    try {
      const values = await connForm.validateFields()
      const port = parseInt(values.port) || CONN_TYPE_DEFAULTS[connType]?.port || 0
      const props = values.properties?.trim()
      if (props) { try { JSON.parse(props) } catch { message.error('属性 JSON 格式无效'); return } }
      await apiCall('/connections', {
        method: 'POST',
        body: JSON.stringify({
          name: values.name, type: connType, host: values.host, port,
          username: values.username, password: values.password, tags: values.tags, properties: props,
        }),
      })
      connForm.resetFields()
      loadConns()
      message.success('连接已添加')
      onRefresh()
    } catch { /* validation failed */ }
  }

  const deleteConnection = async (id: number) => {
    try {
      await apiCall(`/connections/${id}`, { method: 'DELETE' })
      loadConns()
      message.success('已删除')
      onRefresh()
    } catch { /* */ }
  }

  const testConnection = async (id: number) => {
    try {
      const conn = await apiCall<any>(`/connections/${id}`)
      const resp = await fetch('/api/connections/test', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(conn),
      })
      const data = await resp.json()
      if (data.success) {
        message.success('连接成功')
      } else {
        message.error(data.message || '连接失败')
      }
    } catch (e: any) {
      message.error('连接测试失败: ' + e.message)
    }
  }

  const saveModel = async () => {
    try {
      const values = await modelForm.validateFields()
      try { new URL(values.baseUrl) } catch { message.error('API地址格式无效'); return }
      const d = await apiCall<any>('/models', {
        method: 'POST',
        body: JSON.stringify({
          name: values.name, provider: values.provider, baseUrl: values.baseUrl,
          modelName: values.modelName, apiKey: values.apiKey, modelType: 'chat',
        }),
      })
      await apiCall(`/models/${d.id}/activate`, { method: 'PUT' })
      modelForm.resetFields()
      loadModels()
      message.success('模型已添加')
    } catch { /* */ }
  }

  const deleteModel = async (id: number) => {
    try { await apiCall(`/models/${id}`, { method: 'DELETE' }); loadModels(); message.success('已删除') } catch { /* */ }
  }

  const activateModel = async (id: number) => {
    try { await apiCall(`/models/${id}/activate`, { method: 'PUT' }); loadModels(); message.success('已切换') } catch { /* */ }
  }

  const saveKnowledge = async () => {
    try {
      const values = await kbForm.validateFields()
      const tags = (values.tags || '').split(',').map((t: string) => t.trim()).filter(Boolean)
      await apiCall('/knowledge', {
        method: 'POST',
        body: JSON.stringify({ title: values.title, content: values.content, tags }),
      })
      kbForm.resetFields()
      loadDocs()
      message.success('文档已添加到知识库')
    } catch { /* */ }
  }

  const searchKnowledge = async () => {
    if (!kbSearch) { message.warning('请输入搜索关键词'); return }
    try {
      const d = await apiCall<{ result?: string }>(`/knowledge?q=${encodeURIComponent(kbSearch)}`)
      message.success('搜索结果已返回')
      return d.result || '未找到相关文档'
    } catch { message.error('查询失败') }
  }

  const deleteKnowledge = async (docId: string) => {
    try { await apiCall(`/knowledge/${docId}`, { method: 'DELETE' }); loadDocs(); message.success('文档已删除') } catch { /* */ }
  }

  const connColumns = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    {
      title: '类型', dataIndex: 'type', key: 'type',
      render: (t: string) => <Tag color="blue">{t}</Tag>,
    },
    {
      title: '地址', key: 'addr',
      render: (_: any, r: any) => `${r.host}:${r.port}`,
    },
    {
      title: '状态', dataIndex: 'status', key: 'status',
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag>,
    },
    {
      title: '', key: 'action',
      render: (_: any, r: any) => (
        <Space>
          <Tooltip title="测试连接">
            <Button size="small" icon={<ThunderboltOutlined />} onClick={() => testConnection(r.id)} />
          </Tooltip>
          <Popconfirm title="确认删除此连接？" onConfirm={() => deleteConnection(r.id)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const modelColumns = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '模型', dataIndex: 'modelName', key: 'modelName' },
    {
      title: '状态', key: 'status',
      render: (_: any, r: any) => r.isCurrent
        ? <Tag color="green">当前</Tag>
        : <Button type="link" size="small" onClick={() => activateModel(r.id)}>切换</Button>,
    },
    {
      title: '', key: 'action',
      render: (_: any, r: any) => (
        <Popconfirm title="确认删除此模型？" onConfirm={() => deleteModel(r.id)}>
          <Button size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ]

  const docColumns = [
    { title: '标题', dataIndex: 'title', key: 'title', render: (t: string) => t || '无标题' },
    {
      title: '标签', dataIndex: 'tags', key: 'tags',
      render: (tags: string[]) => tags?.map((t: string) => <Tag key={t} style={{ marginRight: 4 }}>{t}</Tag>),
    },
    {
      title: '', key: 'action',
      render: (_: any, r: any) => (
        <Popconfirm title="确认删除此文档？" onConfirm={() => deleteKnowledge(r.docId)}>
          <Button size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ]

  if (!activeTab) return null

  const tabItems = [
    {
      key: 'connections',
      label: <span><LinkOutlined /> 服务连接</span>,
      children: (
        <div>
          <Form form={connForm} layout="vertical" size="middle">
            <Form.Item label="类型" name="type" initialValue={connType}>
              <Select onChange={v => setConnType(v)} options={CONN_TYPE_OPTIONS} />
            </Form.Item>

            <Alert type="info" message={connTypeHint} showIcon style={{ marginBottom: 14, fontSize: 12 }} />

            <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入名称' }]}>
              <Input placeholder="如：生产MySQL" />
            </Form.Item>
            <Form.Item label="地址" name="host" rules={[{ required: true, message: '请输入地址' }]}>
              <Input placeholder="如：192.168.1.100" />
            </Form.Item>
            <Form.Item label="端口" name="port">
              <Input placeholder={CONN_TYPE_DEFAULTS[connType]?.port ? `默认 ${CONN_TYPE_DEFAULTS[connType].port}` : '无需端口'} />
            </Form.Item>
            <Form.Item label="用户名" name="username">
              <Input placeholder="按需填写" />
            </Form.Item>
            <Form.Item label="密码" name="password">
              <Input.Password placeholder="按需填写" />
            </Form.Item>
            <Form.Item label="标签（逗号分隔）" name="tags">
              <Input placeholder="order-service,production" />
            </Form.Item>
            <Form.Item label="属性（JSON）" name="properties">
              <Input.TextArea rows={3} placeholder='{"logPath":"/var/log/app.log"}' />
            </Form.Item>
            <Form.Item>
              <Button type="primary" icon={<PlusOutlined />} onClick={saveConnection}>保存</Button>
            </Form.Item>
          </Form>

          <Table
            rowKey="id"
            dataSource={conns}
            columns={connColumns}
            pagination={false}
            size="small"
            locale={{ emptyText: '暂无连接' }}
            style={{ marginTop: 16 }}
          />
        </div>
      ),
    },
    {
      key: 'models',
      label: <span><KeyOutlined /> 模型配置</span>,
      children: (
        <div>
          <Form form={modelForm} layout="vertical" size="middle">
            <Form.Item label="供应商" name="provider" initialValue="ollama">
              <Select options={[
                { value: 'ollama', label: 'Ollama' },
                { value: 'openai', label: 'OpenAI 兼容' },
              ]} />
            </Form.Item>
            <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入名称' }]}>
              <Input placeholder="如 通义千问" />
            </Form.Item>
            <Form.Item label="API 地址" name="baseUrl" rules={[{ required: true, message: '请输入API地址' }]}>
              <Input placeholder="http://localhost:11434" />
            </Form.Item>
            <Form.Item label="模型名" name="modelName">
              <Input placeholder="qwen2.5" />
            </Form.Item>
            <Form.Item label="API Key" name="apiKey">
              <Input.Password />
            </Form.Item>
            <Form.Item>
              <Button type="primary" icon={<PlusOutlined />} onClick={saveModel}>添加</Button>
            </Form.Item>
          </Form>

          <Table
            rowKey="id"
            dataSource={models}
            columns={modelColumns}
            pagination={false}
            size="small"
            locale={{ emptyText: '暂无模型' }}
            style={{ marginTop: 16 }}
          />
        </div>
      ),
    },
    {
      key: 'knowledge',
      label: <span><BookOutlined /> 知识库</span>,
      children: (
        <div>
          <Space style={{ marginBottom: 12, width: '100%' }}>
            <Input
              value={kbSearch}
              onChange={e => setKbSearch(e.target.value)}
              placeholder="搜索知识库..."
              style={{ flex: 1 }}
            />
            <Button type="primary" icon={<SearchOutlined />} onClick={searchKnowledge}>搜索</Button>
          </Space>

          <Divider />

          <Form form={kbForm} layout="vertical" size="middle">
            <Form.Item label="标题" name="title" rules={[{ required: true, message: '请输入标题' }]}>
              <Input placeholder="如 MySQL 主从延迟处理" />
            </Form.Item>
            <Form.Item label="内容" name="content" rules={[{ required: true, message: '请输入内容' }]}>
              <Input.TextArea rows={6} placeholder="排障步骤..." />
            </Form.Item>
            <Form.Item label="标签（逗号分隔）" name="tags">
              <Input placeholder="mysql,主从" />
            </Form.Item>
            <Form.Item>
              <Button type="primary" icon={<PlusOutlined />} onClick={saveKnowledge}>添加文档</Button>
            </Form.Item>
          </Form>

          <Divider />

          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text2)', marginBottom: 8 }}>已添加的文档</div>
          <Table
            rowKey="docId"
            dataSource={docs}
            columns={docColumns}
            pagination={false}
            size="small"
            locale={{ emptyText: '暂无文档' }}
          />
        </div>
      ),
    },
  ]

  return (
    <Modal
      title="系统设置"
      open={!!activeTab}
      onCancel={onClose}
      width={680}
      footer={null}
      destroyOnClose
    >
      <Tabs activeKey={tab} onChange={setTab} items={tabItems} />
    </Modal>
  )
}
