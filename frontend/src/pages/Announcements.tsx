import { useEffect, useState, useRef } from 'react'
import { Card, List, Spin, Empty, Typography, Button, Avatar, Drawer } from 'antd'
import { MessageOutlined, LinkOutlined, UpOutlined, DownOutlined, ReloadOutlined, UnorderedListOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { apiService } from '../services/api'
import { useMediaQuery } from 'react-responsive'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

const { Title, Text } = Typography

interface Reactions {
  plusOne?: number
  minusOne?: number
  laugh?: number
  confused?: number
  heart?: number
  hooray?: number
  eyes?: number
  rocket?: number
  total?: number
}

interface Announcement {
  id: number
  title: string
  body: string
  author: string
  authorAvatarUrl?: string
  createdAt: number
  updatedAt: number
  reactions?: Reactions
}

const Announcements: React.FC = () => {
  const { t } = useTranslation()
  const isMobile = useMediaQuery({ maxWidth: 768 })
  const [announcements, setAnnouncements] = useState<Announcement[]>([])
  const [selectedAnnouncement, setSelectedAnnouncement] = useState<Announcement | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [isExpanded, setIsExpanded] = useState(false)
  const [drawerVisible, setDrawerVisible] = useState(false)
  const contentRef = useRef<HTMLDivElement>(null)
  
  useEffect(() => {
    fetchAnnouncements()
    fetchLatestDetail()
  }, [])
  
  const fetchAnnouncements = async (forceRefresh: boolean = false) => {
    setLoading(true)
    try {
      const response = await apiService.announcements.list({ forceRefresh })
      if (response.data.code === 0 && response.data.data) {
        setAnnouncements(response.data.data.list || [])
        setHasMore(response.data.data.hasMore || false)
      } else {
        console.error('获取公告列表失败:', response.data.msg)
      }
    } catch (error: any) {
      console.error('获取公告列表异常:', error)
    } finally {
      setLoading(false)
    }
  }
  
  const fetchLatestDetail = async (forceRefresh: boolean = false) => {
    setLoadingDetail(true)
    try {
      const response = await apiService.announcements.detail({ forceRefresh })
      if (response.data.code === 0 && response.data.data) {
        setSelectedAnnouncement(response.data.data)
      } else {
        console.error('获取公告详情失败:', response.data.msg)
      }
    } catch (error: any) {
      console.error('获取公告详情异常:', error)
    } finally {
      setLoadingDetail(false)
    }
  }
  
  const handleSelectAnnouncement = async (id: number, forceRefresh: boolean = false) => {
    setLoadingDetail(true)
    try {
      const response = await apiService.announcements.detail({ id, forceRefresh })
      if (response.data.code === 0 && response.data.data) {
        setSelectedAnnouncement(response.data.data)
        // 移动端选择公告后关闭抽屉
        if (isMobile) {
          setDrawerVisible(false)
        }
      } else {
        console.error('获取公告详情失败:', response.data.msg)
      }
    } catch (error: any) {
      console.error('获取公告详情异常:', error)
    } finally {
      setLoadingDetail(false)
    }
  }
  
  const handleRefresh = async () => {
    await Promise.all([
      fetchAnnouncements(true),
      fetchLatestDetail(true)
    ])
  }
  
  const formatDate = (timestamp: number): string => {
    const date = new Date(timestamp)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  }
  
  // 计算内容行数（通过换行符计算）
  const getLineCount = (text: string): number => {
    if (!text) return 0
    return text.split('\n').length
  }
  
  // 检查是否需要折叠（超过30行）
  const shouldCollapse = (body: string): boolean => {
    return getLineCount(body) > 30
  }
  
  // 当选中公告改变时，重置展开状态
  useEffect(() => {
    if (selectedAnnouncement) {
      const shouldCollapseContent = shouldCollapse(selectedAnnouncement.body)
      setIsExpanded(!shouldCollapseContent) // 如果超过30行，默认折叠（isExpanded = false）
    }
  }, [selectedAnnouncement])
  
  // 渲染公告详情内容（带折叠功能）
  const renderAnnouncementContent = (announcement: Announcement, isMobileView: boolean) => {
    const lineCount = getLineCount(announcement.body)
    const needsCollapse = shouldCollapse(announcement.body)
    const showCollapseButton = needsCollapse
    
    return (
      <div>
        <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 12 }}>
          <Avatar
            src={announcement.authorAvatarUrl}
            icon={<MessageOutlined />}
            size={isMobileView ? 'default' : 'large'}
          />
          <div>
            <Text strong style={{ fontSize: isMobileView ? 14 : 16 }}>
              {announcement.author}
            </Text>
            <br />
            <Text type="secondary" style={{ fontSize: 12 }}>
              {formatDate(announcement.createdAt)}
            </Text>
          </div>
        </div>
        <div
          style={{
            position: 'relative'
          }}
        >
          <div
            ref={contentRef}
            style={{
              padding: isMobileView ? '16px' : '24px',
              backgroundColor: '#fafafa',
              borderRadius: '4px',
              minHeight: isMobileView ? '200px' : '400px',
              maxHeight: needsCollapse && !isExpanded ? '600px' : 'none',
              overflow: needsCollapse && !isExpanded ? 'hidden' : 'visible',
              transition: 'max-height 0.3s ease',
              position: 'relative'
            }}
          >
            <ReactMarkdown remarkPlugins={[remarkGfm]}>
              {announcement.body}
            </ReactMarkdown>
            {needsCollapse && !isExpanded && (
              <div
                style={{
                  position: 'absolute',
                  bottom: 0,
                  left: 0,
                  right: 0,
                  height: '80px',
                  background: 'linear-gradient(to bottom, rgba(250, 250, 250, 0), rgba(250, 250, 250, 1))',
                  pointerEvents: 'none'
                }}
              />
            )}
          </div>
        </div>
        {showCollapseButton && (
          <div style={{ marginTop: 12, textAlign: 'center' }}>
            <Button
              type="link"
              icon={isExpanded ? <UpOutlined /> : <DownOutlined />}
              onClick={() => setIsExpanded(!isExpanded)}
            >
              {isExpanded 
                ? (t('announcements.collapse') || '收起') 
                : (t('announcements.expand') || `展开全部 (共 ${lineCount} 行)`)}
            </Button>
          </div>
        )}
      </div>
    )
  }
  
  // 渲染 reactions（使用 emoji）
  const renderReactions = (reactions?: Reactions) => {
    if (!reactions || reactions.total === 0) {
      return null
    }
    
    const reactionItems: Array<{ emoji: string; count: number; key: string }> = []
    
    if (reactions.plusOne && reactions.plusOne > 0) {
      reactionItems.push({ emoji: '👍', count: reactions.plusOne, key: 'plusOne' })
    }
    if (reactions.minusOne && reactions.minusOne > 0) {
      reactionItems.push({ emoji: '👎', count: reactions.minusOne, key: 'minusOne' })
    }
    if (reactions.laugh && reactions.laugh > 0) {
      reactionItems.push({ emoji: '😄', count: reactions.laugh, key: 'laugh' })
    }
    if (reactions.confused && reactions.confused > 0) {
      reactionItems.push({ emoji: '😕', count: reactions.confused, key: 'confused' })
    }
    if (reactions.heart && reactions.heart > 0) {
      reactionItems.push({ emoji: '❤️', count: reactions.heart, key: 'heart' })
    }
    if (reactions.hooray && reactions.hooray > 0) {
      reactionItems.push({ emoji: '🎉', count: reactions.hooray, key: 'hooray' })
    }
    if (reactions.eyes && reactions.eyes > 0) {
      reactionItems.push({ emoji: '👀', count: reactions.eyes, key: 'eyes' })
    }
    if (reactions.rocket && reactions.rocket > 0) {
      reactionItems.push({ emoji: '🚀', count: reactions.rocket, key: 'rocket' })
    }
    
    if (reactionItems.length === 0) {
      return null
    }
    
    return (
      <div style={{ 
        display: 'flex', 
        alignItems: 'center', 
        gap: '12px',
        flexWrap: 'wrap',
        marginTop: '8px',
        paddingTop: '8px',
        borderTop: '1px solid #f0f0f0'
      }}>
        {reactionItems.map((item) => (
          <span
            key={item.key}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '4px',
              fontSize: '13px',
              color: '#595959',
              backgroundColor: '#fafafa',
              padding: '2px 8px',
              borderRadius: '12px',
              border: '1px solid #e8e8e8'
            }}
          >
            <span>{item.emoji}</span>
            <span style={{ fontWeight: 500 }}>{item.count}</span>
          </span>
        ))}
      </div>
    )
  }
  
  // 渲染公告列表（用于抽屉）
  const renderAnnouncementList = () => {
    return (
      <div>
        {loading ? (
          <div style={{ textAlign: 'center', padding: '40px' }}>
            <Spin size="large" />
          </div>
        ) : announcements.length === 0 ? (
          <Empty description={t('announcements.noAnnouncements') || '暂无公告'} />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {announcements.map((item) => {
              const isSelected = selectedAnnouncement?.id === item.id
              
              return (
                <Card
                  key={item.id}
                  onClick={() => handleSelectAnnouncement(item.id)}
                  style={{
                    cursor: 'pointer',
                    borderRadius: '12px',
                    boxShadow: isSelected 
                      ? '0 4px 12px rgba(24, 144, 255, 0.2)' 
                      : '0 2px 8px rgba(0,0,0,0.08)',
                    border: isSelected 
                      ? '2px solid #1890ff' 
                      : '1px solid #e8e8e8',
                    backgroundColor: isSelected ? '#f0f8ff' : '#ffffff',
                    transition: 'all 0.3s ease',
                    transform: isSelected ? 'scale(1.02)' : 'scale(1)'
                  }}
                  bodyStyle={{ padding: '16px' }}
                  hoverable
                >
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    {/* 标题 */}
                    <div style={{ 
                      fontSize: '16px', 
                      fontWeight: '600', 
                      color: '#262626',
                      lineHeight: '1.5',
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis'
                    }}>
                      {item.title || t('announcements.noTitle') || '无标题'}
                    </div>
                    
                    {/* 时间和作者 */}
                    <div style={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      gap: '8px',
                      fontSize: '12px',
                      color: '#8c8c8c'
                    }}>
                      <Avatar
                        src={item.authorAvatarUrl}
                        icon={<MessageOutlined />}
                        size="small"
                        style={{ flexShrink: 0 }}
                      />
                      <span style={{ fontWeight: 500 }}>{item.author}</span>
                      <span>•</span>
                      <span>{formatDate(item.createdAt)}</span>
                    </div>
                    
                    {/* Reactions */}
                    {renderReactions(item.reactions)}
                  </div>
                </Card>
              )
            })}
          </div>
        )}
        
        {hasMore && (
          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <Button
              type="link"
              icon={<LinkOutlined />}
              href="https://github.com/WrBug/PolyHermes/issues/1"
              target="_blank"
              rel="noopener noreferrer"
            >
              {t('announcements.viewMore') || '查看更多公告'}
            </Button>
          </div>
        )}
      </div>
    )
  }
  
  if (isMobile) {
    // 移动端布局：详情在主要内容区，列表在侧边抽屉
    return (
      <div>
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <Button
                type="default"
                icon={<UnorderedListOutlined />}
                onClick={() => setDrawerVisible(true)}
                style={{ flexShrink: 0 }}
              >
                {t('announcements.list') || '列表'}
              </Button>
              <Title level={4} style={{ margin: 0 }}>
                {t('announcements.title') || '公告'}
              </Title>
            </div>
            <Button
              type="primary"
              icon={<ReloadOutlined />}
              onClick={handleRefresh}
              loading={loading || loadingDetail}
              size="small"
            >
              {t('announcements.refresh') || '刷新'}
            </Button>
          </div>
          
          {/* 公告详情 */}
          <div>
            {loadingDetail ? (
              <div style={{ textAlign: 'center', padding: '40px' }}>
                <Spin size="large" />
              </div>
            ) : selectedAnnouncement ? (
              renderAnnouncementContent(selectedAnnouncement, true)
            ) : (
              <Empty description={t('announcements.noDetail') || '请选择一条公告查看详情'} />
            )}
          </div>
        </Card>
        
        {/* 侧边抽屉：公告列表 */}
        <Drawer
          title={t('announcements.list') || '公告列表'}
          placement="right"
          onClose={() => setDrawerVisible(false)}
          open={drawerVisible}
          width="85%"
          bodyStyle={{ padding: '16px' }}
        >
          {renderAnnouncementList()}
        </Drawer>
      </div>
    )
  }
  
  // 桌面端布局：左右结构
  return (
    <div>
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <Title level={2} style={{ margin: 0 }}>
            {t('announcements.title') || '公告'}
          </Title>
          <Button
            type="primary"
            icon={<ReloadOutlined />}
            onClick={handleRefresh}
            loading={loading || loadingDetail}
          >
            {t('announcements.refresh') || '刷新'}
          </Button>
        </div>
        
        <div style={{ display: 'flex', gap: 24, minHeight: '600px' }}>
          {/* 左侧：公告列表 */}
          <div style={{ width: '300px', flexShrink: 0 }}>
            <List
              loading={loading}
              dataSource={announcements}
              locale={{ emptyText: <Empty description={t('announcements.noAnnouncements') || '暂无公告'} /> }}
              renderItem={(item) => (
                <List.Item
                  style={{
                    cursor: 'pointer',
                    backgroundColor: selectedAnnouncement?.id === item.id ? '#e6f7ff' : 'transparent',
                    padding: '12px',
                    borderRadius: '4px',
                    marginBottom: '8px',
                    border: selectedAnnouncement?.id === item.id ? '1px solid #1890ff' : '1px solid transparent'
                  }}
                  onClick={() => handleSelectAnnouncement(item.id)}
                >
                  <List.Item.Meta
                    title={
                      <Text strong style={{ fontSize: 14 }}>
                        {item.title || t('announcements.noTitle') || '无标题'}
                      </Text>
                    }
                    description={
                      <div>
                        <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
                          {formatDate(item.createdAt)}
                        </Text>
                        {renderReactions(item.reactions)}
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
            
            {hasMore && (
              <div style={{ marginTop: 16, textAlign: 'center' }}>
                <Button
                  type="link"
                  icon={<LinkOutlined />}
                  href="https://github.com/WrBug/PolyHermes/issues/1"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {t('announcements.viewMore') || '查看更多公告'}
                </Button>
              </div>
            )}
          </div>
          
          {/* 右侧：公告详情 */}
          <div style={{ flex: 1, borderLeft: '1px solid #e8e8e8', paddingLeft: 24 }}>
            {loadingDetail ? (
              <div style={{ textAlign: 'center', padding: '40px' }}>
                <Spin size="large" />
              </div>
            ) : selectedAnnouncement ? (
              renderAnnouncementContent(selectedAnnouncement, false)
            ) : (
              <Empty description={t('announcements.noDetail') || '请选择一条公告查看详情'} />
            )}
          </div>
        </div>
      </Card>
    </div>
  )
}

export default Announcements

