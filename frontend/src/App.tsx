// DevOps Copilot 主应用组件：导航栏、聊天面板、侧栏面板、监控仪表盘等
import { useState, useEffect, useCallback, useRef } from 'react'
import { ConfigProvider, Select, Button, theme } from 'antd'
import { LoadingOutlined, SettingOutlined, DatabaseOutlined, MenuOutlined, SunOutlined, MoonOutlined, ExperimentOutlined, BranchesOutlined, ApiOutlined, MonitorOutlined, DashboardOutlined, ClusterOutlined, AlertOutlined } from '@ant-design/icons'
import { apiCall } from './api/client'
import Sidebar from './components/Sidebar'
import RightPanel from './components/RightPanel'
import SettingsModal from './components/SettingsModal'
import DbDashboard from './components/DbDashboard'
import RedisDashboard from './components/RedisDashboard'
import RabbitDashboard from './components/RabbitDashboard'
import SystemDashboard from './components/SystemDashboard'
import ESDashboard from './components/ESDashboard'
import DockerDashboard from './components/DockerDashboard'
import K8sDashboard from './components/K8sDashboard'
import AlertCenter from './components/AlertCenter'
import ExperiencePanel from './components/ExperiencePanel'
import BotMessage from './components/BotMessage'
import './App.css'

interface ChatMessage {
  role: 'user' | 'bot'
  content: string
  id: string
}

export default function App() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [sessionId, setSessionId] = useState(() => Math.random().toString(36).substring(2, 10))
  const [sessionLabel, setSessionLabel] = useState('新会话')
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [rightPanelOpen, setRightPanelOpen] = useState(false)
  const [settingsTab, setSettingsTab] = useState<string | null>(null)
  const [dbDashOpen, setDbDashOpen] = useState(false)
  const [expOpen, setExpOpen] = useState(false)
  const [redisDashOpen, setRedisDashOpen] = useState(false)
  const [rabbitDashOpen, setRabbitDashOpen] = useState(false)
  const [sysDashOpen, setSysDashOpen] = useState(false)
  const [esDashOpen, setEsDashOpen] = useState(false)
  const [dockerDashOpen, setDockerDashOpen] = useState(false)
  const [k8sDashOpen, setK8sDashOpen] = useState(false)
  const [alertDashOpen, setAlertDashOpen] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const [registeredTypes, setRegisteredTypes] = useState<string[]>([])
  const [modelOnline, setModelOnline] = useState(false)
  const [modelName, setModelName] = useState('')
  const eventSourceRef = useRef<EventSource | null>(null)
  const chatRef = useRef<HTMLDivElement>(null)
  const botStreamIdRef = useRef<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const triggerRefresh = () => setRefreshKey(k => k + 1)

  // Dark mode
  const [isDark, setIsDark] = useState(() => {
    const saved = localStorage.getItem('theme')
    if (saved) return saved === 'dark'
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  })

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light')
    localStorage.setItem('theme', isDark ? 'dark' : 'light')
  }, [isDark])

  // Keyboard shortcuts
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { setSettingsTab(null); setDbDashOpen(false) }
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') { e.preventDefault(); inputRef.current?.focus() }
      if ((e.ctrlKey || e.metaKey) && e.key === 'n') { e.preventDefault(); newSession() }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  // Load model status
  useEffect(() => {
    apiCall<any>('/models/current').then(d => {
      if (d && d.name) { setModelOnline(true); setModelName(d.name) }
      else { setModelOnline(false); setModelName('') }
    }).catch(() => { setModelOnline(false); setModelName('') })
  }, [])

  // Load registered connection types (determines which nav buttons to show)
  useEffect(() => {
    apiCall<any[]>('/connections').then(d => {
      if (Array.isArray(d)) {
        const types = [...new Set(d.map(c => c.type).filter(Boolean))]
        setRegisteredTypes(types)
      }
    }).catch(() => {})
  }, [refreshKey])

  // Scroll to bottom
  const scrollToBottom = () => {
    setTimeout(() => chatRef.current?.lastElementChild?.scrollIntoView({ behavior: 'smooth' }), 50)
  }

  // Load sessions on mount
  useEffect(() => { loadSessions() }, [refreshKey])

  // Load messages when session changes
  useEffect(() => {
    if (sessionId) loadSessionMessages()
  }, [sessionId])

  const loadSessions = async () => {
    try {
      const list: any[] = await apiCall('/sessions')
      // The sidebar component handles rendering
    } catch { /* ignore */ }
  }

  const loadSessionMessages = async () => {
    try {
      const msgs = await apiCall<any[]>(`/sessions/${sessionId}/messages`)
      if (msgs.length === 0) {
        setMessages([])
        return
      }
      const mapped: ChatMessage[] = msgs.map((m: any, i: number) => ({
        role: m.role === 'user' ? 'user' : 'bot',
        content: m.content,
        id: `msg-${i}-${Date.now()}`,
      }))
      setMessages(mapped)
      scrollToBottom()
    } catch { /* ignore */ }
  }

  const newSession = () => {
    closeSSE()
    setSessionId(Math.random().toString(36).substring(2, 10))
    setSessionLabel('新会话')
    setMessages([])
  }

  const switchSession = async (id: string, title: string) => {
    closeSSE()
    setSessionId(id)
    setSessionLabel(title)
  }

  const deleteSession = async (id: string) => {
    if (!confirm('确认删除此会话？')) return
    try {
      await apiCall(`/sessions/${id}`, { method: 'DELETE' })
      if (id === sessionId) newSession()
      triggerRefresh()
    } catch { /* ignore */ }
  }

  const editSessionName = async () => {
    const name = prompt('编辑会话名称:', sessionLabel)
    if (name && name.trim() && name.trim() !== sessionLabel) {
      try {
        await apiCall(`/sessions/${sessionId}`, {
          method: 'PUT',
          body: JSON.stringify({ title: name.trim() }),
        })
        setSessionLabel(name.trim())
        triggerRefresh()
      } catch { /* ignore */ }
    }
  }

  const closeSSE = () => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }
  }

  const send = useCallback(async (text?: string) => {
    const msg = text || input.trim()
    if (!msg || loading) return
    setInput('')
    setLoading(true)

    // Add user message
    const userMsg: ChatMessage = { role: 'user', content: msg, id: `user-${Date.now()}` }
    setMessages(prev => [...prev, userMsg])

    const botId = `bot-${Date.now()}`
    botStreamIdRef.current = botId

    // Streaming SSE
    closeSSE()
    try {
      const url = `/api/chat?message=${encodeURIComponent(msg)}&sessionId=${sessionId}`
      const es = new EventSource(url)
      eventSourceRef.current = es

      es.addEventListener('message', (e: MessageEvent) => {
        setMessages(prev => {
          const existing = prev.find(m => m.id === botId)
          if (existing) {
            return prev.map(m => m.id === botId ? { ...m, content: m.content + e.data } : m)
          }
          return [...prev, { role: 'bot', content: e.data, id: botId }]
        })
        scrollToBottom()
      })

      es.addEventListener('done', () => {
        closeSSE()
        setLoading(false)
        triggerRefresh()
        scrollToBottom()
      })

      es.addEventListener('error', (e: MessageEvent) => {
        closeSSE()
        setLoading(false)
        const msg = e.data || '服务端处理异常'
        setMessages(prev => prev.some(m => m.id === botId)
          ? prev
          : [...prev, { role: 'bot', content: '❌ ' + msg, id: `err-${Date.now()}` }]
        )
      })

      es.onerror = () => {
        // 网络断开时 EventSource 会自动触发 onerror 和 error 事件
        // 由上面的 error handler 处理消息，这里只停 loading
        if (loading) setLoading(false)
      }
    } catch (e: any) {
      setMessages(prev => [...prev, { role: 'bot', content: '❌ 连接失败: ' + e.message, id: `err-${Date.now()}` }])
      setLoading(false)
    }
  }, [input, loading, sessionId])

  const quickCommand = (prefix: string) => {
    setInput(prefix)
  }

  return (
    <ConfigProvider
      theme={{
        algorithm: isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          colorPrimary: '#0d9488',
          colorLink: '#0d9488',
          borderRadius: 8,
          fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
        },
      }}
    >
    <div className="app">
      {/* ========= Nav ========= */}
      <div className="nav">
        <svg className="logo" onClick={() => setSidebarOpen(!sidebarOpen)} width="32" height="32" viewBox="0 0 200 200">
          <defs><linearGradient id="g2" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stopColor="#0d9488"/><stop offset="100%" stopColor="#0891b2"/></linearGradient></defs>
          <circle cx="100" cy="100" r="70" fill="none" stroke="url(#g2)" strokeWidth="8" strokeDasharray="20 10"/>
          <circle cx="100" cy="100" r="50" fill="none" stroke="url(#g2)" strokeWidth="8"/>
          <path d="M70,70 L130,70 A15,15 0 0,1 145,85 L145,115 A15,15 0 0,1 130,130 L85,130 L65,150 L70,130 L70,115 A15,15 0 0,1 55,100 L55,85 A15,15 0 0,1 70,70 Z" fill="url(#g2)" opacity="0.85"/>
          <circle cx="85" cy="95" r="4" fill="#fff"/><circle cx="100" cy="95" r="4" fill="#fff"/><circle cx="115" cy="95" r="4" fill="#fff"/>
        </svg>
        <div className="session-name" onClick={editSessionName}>
          <span id="sessionLabel">{sessionLabel}</span>
        </div>
        <div className="nav-right">
          <span className={`model-status ${modelOnline ? 'on' : 'off'}`} id="modelStatus" title={modelName}></span>
          <ModelSelect sessionId={sessionId} />
          <Button type="text" icon={isDark ? <SunOutlined /> : <MoonOutlined />} onClick={() => setIsDark(!isDark)} title={isDark ? '亮色模式' : '暗色模式'} />
          <Button type="text" icon={<SettingOutlined />} onClick={() => setSettingsTab('connections')} title="设置">设置</Button>
          {registeredTypes.includes('mysql') && <Button type="text" icon={<DatabaseOutlined />} onClick={() => setDbDashOpen(true)} title="数据库监控">数据库</Button>}
          {registeredTypes.includes('redis') && <Button type="text" icon={<BranchesOutlined />} onClick={() => setRedisDashOpen(true)} title="Redis 监控">Redis</Button>}
          {registeredTypes.includes('rabbit') && <Button type="text" icon={<ApiOutlined />} onClick={() => setRabbitDashOpen(true)} title="RabbitMQ 监控">RabbitMQ</Button>}
          <Button type="text" icon={<MonitorOutlined />} onClick={() => setSysDashOpen(true)} title="系统监控">系统</Button>
          {registeredTypes.includes('es') && <Button type="text" icon={<DatabaseOutlined />} onClick={() => setEsDashOpen(true)} title="ES 监控">ES</Button>}
          <Button type="text" icon={<DashboardOutlined />} onClick={() => setDockerDashOpen(true)} title="Docker">Docker</Button>
          {registeredTypes.includes('k8s') && <Button type="text" icon={<ClusterOutlined />} onClick={() => setK8sDashOpen(true)} title="K8s">K8s</Button>}
          <Button type="text" icon={<AlertOutlined />} onClick={() => setAlertDashOpen(true)} title="告警中心">告警</Button>
          <Button type="text" icon={<ExperimentOutlined />} onClick={() => setExpOpen(true)} title="经验记忆库">经验</Button>
          <Button type="text" icon={<MenuOutlined />} onClick={() => setRightPanelOpen(!rightPanelOpen)} title="侧栏">侧栏</Button>
        </div>
      </div>

      {/* Body */}
      <div className="body">
        <Sidebar
          open={sidebarOpen}
          currentSessionId={sessionId}
          onNew={newSession}
          onSwitch={switchSession}
          onDelete={deleteSession}
          refreshKey={refreshKey}
        />

        {/* ========= Chat ========= */}
        <div className="main">
          <div className="chat" ref={chatRef} id="chat">
            {messages.length === 0 && (
              <div className="welcome" id="welcome">
                <div className="icon">
                  <svg width="50" height="50" viewBox="0 0 200 200">
                    <defs><linearGradient id="g3" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stopColor="#0d9488"/><stop offset="100%" stopColor="#0891b2"/></linearGradient></defs>
                    <circle cx="100" cy="100" r="70" fill="none" stroke="url(#g3)" strokeWidth="8" strokeDasharray="20 10"/>
                    <circle cx="100" cy="100" r="50" fill="none" stroke="url(#g3)" strokeWidth="8"/>
                    <path d="M70,70 L130,70 A15,15 0 0,1 145,85 L145,115 A15,15 0 0,1 130,130 L85,130 L65,150 L70,130 L70,115 A15,15 0 0,1 55,100 L55,85 A15,15 0 0,1 70,70 Z" fill="url(#g3)" opacity="0.85"/>
                  </svg>
                </div>
                <h2>DevOps Copilot</h2>
                <p>AI Agent 运维助手 · 输入报障信息自动排查根因</p>
              </div>
            )}
            {messages.map(msg =>
              msg.role === 'user' ? (
                <div key={msg.id} className="msg user">{msg.content}</div>
              ) : (
                <BotMessage key={msg.id} content={msg.content} messageId={msg.id} />
              )
            )}
            {loading && !messages.find(m => m.id === botStreamIdRef.current) && (
              <div className="msg bot">
                <div className="typing"><span></span><span></span><span></span></div>
              </div>
            )}
          </div>

          <div className="input-area">
            <div className="quick-bar">
              <Button size="small" type="default" onClick={() => send('/健康巡检')}>🔍 健康巡检</Button>
              <Button size="small" type="default" onClick={() => { quickCommand('/知识库 '); document.getElementById('chat-input')?.focus() }}>📚 知识库</Button>
              <Button size="small" type="default" onClick={() => setSettingsTab('connections')}>⚙ 配置管理</Button>
            </div>
            <div className="input-row">
              <input
                id="chat-input"
                ref={inputRef}
                value={input}
                onChange={e => setInput(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }}
                placeholder="描述问题，如 order-service 报错了..."
              />
              <button id="sendBtn" onClick={() => send()} disabled={loading}>
                {loading ? <><LoadingOutlined /> 处理中...</> : '发送'}
              </button>
            </div>
          </div>
        </div>

        <RightPanel open={rightPanelOpen} refreshKey={refreshKey} onOpenSettings={() => setSettingsTab('connections')} onOpenDbDashboard={() => setDbDashOpen(true)} onOpenRedisDashboard={() => setRedisDashOpen(true)} onOpenRabbitDashboard={() => setRabbitDashOpen(true)} onOpenSysDashboard={() => setSysDashOpen(true)} onOpenESDashboard={() => setEsDashOpen(true)} onOpenDockerDashboard={() => setDockerDashOpen(true)} onOpenK8sDashboard={() => setK8sDashOpen(true)} onOpenExperiences={() => setExpOpen(true)} />
      </div>

      {/* ========= Modals ========= */}
      <SettingsModal
        activeTab={settingsTab}
        onClose={() => setSettingsTab(null)}
        onRefresh={triggerRefresh}
      />
      <DbDashboard
        open={dbDashOpen}
        onClose={() => setDbDashOpen(false)}
      />
      <RedisDashboard
        open={redisDashOpen}
        onClose={() => setRedisDashOpen(false)}
      />
      <RabbitDashboard
        open={rabbitDashOpen}
        onClose={() => setRabbitDashOpen(false)}
      />
      <SystemDashboard
        open={sysDashOpen}
        onClose={() => setSysDashOpen(false)}
      />
      <ESDashboard
        open={esDashOpen}
        onClose={() => setEsDashOpen(false)}
      />
      <DockerDashboard
        open={dockerDashOpen}
        onClose={() => setDockerDashOpen(false)}
      />
      <K8sDashboard
        open={k8sDashOpen}
        onClose={() => setK8sDashOpen(false)}
      />
      <AlertCenter
        open={alertDashOpen}
        onClose={() => setAlertDashOpen(false)}
      />
      <ExperiencePanel
        open={expOpen}
        onClose={() => setExpOpen(false)}
      />
    </div>
    </ConfigProvider>
  )
}

/** Model select dropdown */
function ModelSelect({ sessionId: _sid }: { sessionId: string }) {
  const [models, setModels] = useState<any[]>([])
  useEffect(() => {
    apiCall('/models').then(d => setModels(Array.isArray(d) ? d : [])).catch(() => {})
  }, [])
  const current = models.find(m => m.isCurrent)
  return (
    <Select
      value={current?.id || undefined}
      placeholder="未配置"
      onChange={id => {
        if (id) apiCall(`/models/${id}/activate`, { method: 'PUT' }).then(() => window.location.reload()).catch(() => {})
      }}
      options={models.map(m => ({ value: m.id, label: m.name }))}
      style={{ width: 140 }}
      size="small"
      variant="borderless"
    />
  )
}
