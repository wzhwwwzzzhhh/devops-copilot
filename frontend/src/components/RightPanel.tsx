import { useState, useEffect } from 'react'
import { Tag, Button, Tooltip } from 'antd'
import { SettingOutlined, DatabaseOutlined, CloseOutlined, ExperimentOutlined } from '@ant-design/icons'
import { apiCall } from '../api/client'

interface Props {
  open: boolean
  refreshKey: number
  onOpenSettings: () => void
  onOpenDbDashboard?: () => void
  onOpenExperiences?: () => void
}

export default function RightPanel({ open, refreshKey, onOpenSettings, onOpenDbDashboard, onOpenExperiences }: Props) {
  const [connections, setConnections] = useState<any[]>([])
  const [models, setModels] = useState<any[]>([])

  useEffect(() => {
    if (!open) return
    apiCall('/connections').then(d => setConnections(Array.isArray(d) ? d : [])).catch(() => {})
    apiCall('/models').then(d => setModels(Array.isArray(d) ? d : [])).catch(() => {})
  }, [open, refreshKey])

  const services = connections.filter(c => c.type === 'service')
  const mysqlConns = connections.filter(c => c.type === 'mysql')

  return (
    <div className={`right-panel${open ? '' : ' collapsed'}`} id="rightPanel">
      <div className="panel-header">
        <span>系统状态</span>
        <Button type="text" size="small" icon={<CloseOutlined />} onClick={() => {/* parent handles close */}} />
      </div>
      <div className="panel-body">
        <div className="section">
          <div className="section-title">已注册的服务</div>
          <div id="svcList">
            {services.length > 0
              ? services.map(s => (
                  <div key={s.id} className="tool-item">
                    <span className="dot green"></span>{s.name}
                  </div>
                ))
              : <div style={{ fontSize: 12, color: 'var(--text2)', padding: '4px 0' }}>
                  未注册服务（共 {connections.length} 条连接）
                  <br />
                  <Button type="link" size="small" onClick={onOpenSettings} style={{ padding: 0 }}>
                    去配置
                  </Button>
                </div>
            }
          </div>
        </div>

        <div className="section">
          <div className="section-title">数据库连接</div>
          <div style={{ fontSize: 12, padding: '4px 0' }}>
            {mysqlConns.length > 0 ? (
              <>
                <div className="tool-item">
                  <span className="dot green"></span>
                  MySQL × {mysqlConns.length}
                </div>
                {onOpenDbDashboard && (
                  <Button type="link" size="small" onClick={onOpenDbDashboard} style={{ padding: 0, fontSize: 11 }}>
                    查看监控详情 →
                  </Button>
                )}
              </>
            ) : (
              <div style={{ color: 'var(--text2)' }}>
                未配置
                <br />
                <Button type="link" size="small" onClick={onOpenSettings} style={{ padding: 0 }}>
                  去配置
                </Button>
              </div>
            )}
          </div>
        </div>

        <div className="section">
          <div className="section-title">LLM 模型</div>
          <div id="modelListRight" style={{ fontSize: 12, color: 'var(--text2)', padding: '4px 0' }}>
            {models.length > 0
              ? models.map(m => (
                  <div key={m.id} className="tool-item">
                    <span className={`dot ${m.isCurrent ? 'green' : 'orange'}`}></span>
                    {m.name}
                    {m.isCurrent && <Tag color="green" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>当前</Tag>}
                  </div>
                ))
              : <Tag style={{ fontSize: 12 }}>未配置模型</Tag>
            }
          </div>
        </div>

        <div className="section">
          <div className="section-title">知识库文档</div>
          <div style={{ fontSize: 12, color: 'var(--text2)', padding: '4px 0' }}>
            <Button type="link" size="small" icon={<SettingOutlined />} onClick={onOpenSettings} style={{ padding: 0 }}>
              管理知识库
            </Button>
          </div>
        </div>

        <div className="section">
          <div className="section-title">经验记忆</div>
          <div style={{ fontSize: 12, color: 'var(--text2)', padding: '4px 0' }}>
            {onOpenExperiences && (
              <Button type="link" size="small" icon={<ExperimentOutlined />} onClick={onOpenExperiences} style={{ padding: 0 }}>
                查看历史排查经验
              </Button>
            )}
            <div style={{ marginTop: 4, color: 'var(--text2)', fontSize: 11 }}>
              Agent 解决故障后自动保存的经验记录
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
