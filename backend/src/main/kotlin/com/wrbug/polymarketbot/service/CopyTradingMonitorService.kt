package com.wrbug.polymarketbot.service

import com.wrbug.polymarketbot.entity.CopyTrading
import com.wrbug.polymarketbot.entity.Leader
import com.wrbug.polymarketbot.repository.CopyTradingRepository
import com.wrbug.polymarketbot.repository.LeaderRepository
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 跟单监听服务（主服务）
 * 管理所有Leader的交易监听（使用轮询方式）
 * 注意：WebSocket 需要认证才能订阅其他用户的交易，因此只使用轮询方式
 */
@Service
class CopyTradingMonitorService(
    private val copyTradingRepository: CopyTradingRepository,
    private val leaderRepository: LeaderRepository,
    private val pollingService: CopyTradingPollingService
) {
    
    private val logger = LoggerFactory.getLogger(CopyTradingMonitorService::class.java)
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * 系统启动时初始化监听
     */
    @PostConstruct
    fun init() {
        logger.info("跟单监听服务初始化...")
        scope.launch {
            try {
                startMonitoring()
            } catch (e: Exception) {
                logger.error("启动跟单监听失败", e)
            }
        }
    }
    
    /**
     * 系统关闭时清理资源
     */
    @PreDestroy
    fun destroy() {
        logger.info("停止跟单监听服务...")
        scope.cancel()
        // 只使用轮询，不使用WebSocket
        pollingService.stop()
    }
    
    /**
     * 启动监听
     */
    suspend fun startMonitoring() {
        // 1. 获取所有启用的跟单关系
        val enabledCopyTradings = copyTradingRepository.findByEnabledTrue()
        
        if (enabledCopyTradings.isEmpty()) {
            logger.info("没有启用的跟单关系，等待添加...")
            return
        }
        
        // 2. 获取所有需要监听的Leader（去重）
        val leaderIds = enabledCopyTradings.map { it.leaderId }.distinct()
        val leaders = leaderIds.mapNotNull { leaderId ->
            leaderRepository.findById(leaderId).orElse(null)
        }
        
        logger.info("开始监听 ${leaders.size} 个Leader的交易: ${leaders.map { it.leaderAddress }}")
        
        // 3. 启动轮询监听（使用 /activity 接口，不需要认证）
        // 注意：WebSocket 需要认证才能订阅其他用户的交易，因此禁用WebSocket，只使用轮询
        pollingService.start(leaders)
    }
    
    /**
     * 添加Leader监听（当创建新的跟单关系时调用）
     */
    suspend fun addLeaderMonitoring(leaderId: Long) {
        val leader = leaderRepository.findById(leaderId).orElse(null)
            ?: return
        
        val copyTradings = copyTradingRepository.findByLeaderIdAndEnabledTrue(leaderId)
        if (copyTradings.isEmpty()) {
            logger.debug("Leader $leaderId 没有启用的跟单关系，不启动监听")
            return
        }
        
        logger.info("添加Leader监听: ${leader.leaderAddress}")
        // 只使用轮询，不使用WebSocket（需要认证）
        pollingService.addLeader(leader)
    }
    
    /**
     * 移除Leader监听（当删除跟单关系时调用）
     */
    suspend fun removeLeaderMonitoring(leaderId: Long) {
        val copyTradings = copyTradingRepository.findByLeaderIdAndEnabledTrue(leaderId)
        if (copyTradings.isNotEmpty()) {
            logger.debug("Leader $leaderId 仍有启用的跟单关系，不停止监听")
            return
        }
        
        logger.info("移除Leader监听: leaderId=$leaderId")
        // 只使用轮询，不使用WebSocket
        pollingService.removeLeader(leaderId)
    }
    
    /**
     * 重新启动监听（当跟单关系状态改变时调用）
     */
    suspend fun restartMonitoring() {
        logger.info("重新启动跟单监听...")
        // 只使用轮询，不使用WebSocket
        pollingService.stop()
        delay(1000)  // 等待1秒
        startMonitoring()
    }
}

