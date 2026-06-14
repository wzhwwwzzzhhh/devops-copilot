// 侧栏会话列表：展示历史会话，支持新建、切换、删除
import { useState, useEffect } from 'react'
import { Button, Spin, Tag, Tooltip } from 'antd'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import { apiCall } from '../api/client'

interface Session {
  sessionId: string
  title: string
  createdAt: string
}

interface Props {
  open: boolean
  currentSessionId: string
  onNew: () => void
  onSwitch: (id: string, title: string) => void
  onDelete: (id: string) => void
  refreshKey: number
}

export default function Sidebar({ open, currentSessionId, onNew, onSwitch, onDelete, refreshKey }: Props) {
  const [sessions, setSessions] = useState<Session[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    apiCall<Session[]>('/sessions')
      .then(list => {
        setSessions(Array.isArray(list) ? list : [])
        setLoading(false)
      })
      .catch(() => setLoading(false))
  }, [refreshKey])

  const formatTime = (t: string) => {
    if (!t) return ''
    try {
      const d = new Date(t)
      return d.toLocaleDateString() + ' ' + d.toLocaleTimeString().slice(0, 5)
    } catch { return t }
  }

  return (
    <div className={`sidebar${open ? '' : ' collapsed'}`} id="sidebar">
      <Button
        type="primary"
        block
        icon={<PlusOutlined />}
        onClick={onNew}
        className="new-btn"
      >
        新建会话
      </Button>

      <div className="sessions" id="sessionList">
        {loading ? (
          <div style={{ padding: 12, textAlign: 'center' }}><Spin size="small" /></div>
        ) : sessions.length === 0 ? (
          <div style={{ padding: 12, fontSize: 12, color: 'var(--text2)', textAlign: 'center' }}>暂无会话</div>
        ) : (
          sessions.map(s => (
            <div
              key={s.sessionId}
              className={`session-item${s.sessionId === currentSessionId ? ' active' : ''}`}
              onClick={() => onSwitch(s.sessionId, s.title || '会话')}
            >
              <div className="title">{s.title || '会话'}</div>
              <div className="time">{formatTime(s.createdAt)}</div>
              <Tooltip title="删除">
                <button
                  className="session-del-btn"
                  onClick={e => { e.stopPropagation(); onDelete(s.sessionId) }}
                >
                  <DeleteOutlined />
                </button>
              </Tooltip>
            </div>
          ))
        )}
      </div>

      <div className="status-bar">
        <span className="tag"><span className="dot green"></span>Mock 模式</span>
        <span className="tag">
          <span className="dot green" id="llmStatusDot"></span>
          <span id="llmLabel">LLM</span>
        </span>
      </div>
    </div>
  )
}
