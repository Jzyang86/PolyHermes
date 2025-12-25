package com.wrbug.polymarketbot.service.copytrading.statistics

import com.wrbug.polymarketbot.api.PolymarketClobApi
import com.wrbug.polymarketbot.entity.*
import com.wrbug.polymarketbot.repository.*
import com.wrbug.polymarketbot.util.RetrofitFactory
import com.wrbug.polymarketbot.util.CryptoUtils
import com.wrbug.polymarketbot.util.toSafeBigDecimal
import com.wrbug.polymarketbot.util.multi
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 订单状态更新服务
 * 定时轮询更新卖出订单的实际成交价，并清理已删除账户的订单
 */
@Service
class OrderStatusUpdateService(
    private val sellMatchRecordRepository: SellMatchRecordRepository,
    private val sellMatchDetailRepository: SellMatchDetailRepository,
    private val copyTradingRepository: CopyTradingRepository,
    private val accountRepository: AccountRepository,
    private val copyOrderTrackingRepository: CopyOrderTrackingRepository,
    private val retrofitFactory: RetrofitFactory,
    private val cryptoUtils: CryptoUtils,
    private val trackingService: CopyOrderTrackingService
) {
    
    private val logger = LoggerFactory.getLogger(OrderStatusUpdateService::class.java)
    
    private val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        logger.info("订单状态更新服务已启动，将每5秒轮询一次")
    }
    
    /**
     * 定时更新卖出订单价格
     * 每5秒执行一次
     */
    @Scheduled(fixedDelay = 5000)
    fun updateSellOrderPrices() {
        updateScope.launch {
            try {
                // 1. 清理已删除账户的订单
                cleanupDeletedAccountOrders()
                
                // 2. 更新卖出订单的实际成交价
                updatePendingSellOrderPrices()
            } catch (e: Exception) {
                logger.error("订单状态更新异常: ${e.message}", e)
            }
        }
    }
    
    /**
     * 验证订单ID格式
     * 订单ID必须以 0x 开头，且是有效的 16 进制字符串
     * 
     * @param orderId 订单ID
     * @return 如果格式有效返回 true，否则返回 false
     */
    private fun isValidOrderId(orderId: String): Boolean {
        if (!orderId.startsWith("0x", ignoreCase = true)) {
            return false
        }
        // 验证是否为有效的 16 进制字符串（去除 0x 前缀后）
        val hexPart = orderId.substring(2)
        if (hexPart.isEmpty()) {
            return false
        }
        // 检查是否只包含 0-9, a-f, A-F
        return hexPart.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }
    
    /**
     * 清理已删除账户的订单
     */
    @Transactional
    private suspend fun cleanupDeletedAccountOrders() {
        try {
            // 查询所有卖出记录
            val allRecords = sellMatchRecordRepository.findAll()
            
            // 查询所有有效的账户ID
            val validAccountIds = accountRepository.findAll().mapNotNull { it.id }.toSet()
            
            // 查询所有有效的跟单关系
            val validCopyTradingIds = copyTradingRepository.findAll()
                .filter { it.accountId in validAccountIds }
                .mapNotNull { it.id }
                .toSet()
            
            // 找出需要删除的记录（关联的跟单关系已不存在或账户已删除）
            val recordsToDelete = allRecords.filter { record ->
                val copyTrading = copyTradingRepository.findById(record.copyTradingId).orElse(null)
                copyTrading == null || copyTrading.accountId !in validAccountIds
            }
            
            if (recordsToDelete.isNotEmpty()) {
                logger.info("清理已删除账户的订单: ${recordsToDelete.size} 条记录")
                
                // 删除匹配明细
                for (record in recordsToDelete) {
                    val details = sellMatchDetailRepository.findByMatchRecordId(record.id!!)
                    sellMatchDetailRepository.deleteAll(details)
                }
                
                // 删除卖出记录
                sellMatchRecordRepository.deleteAll(recordsToDelete)
                
                logger.info("已清理 ${recordsToDelete.size} 条已删除账户的订单记录")
            }
        } catch (e: Exception) {
            logger.error("清理已删除账户订单异常: ${e.message}", e)
        }
    }
    
    /**
     * 更新待更新的卖出订单价格
     */
    @Transactional
    private suspend fun updatePendingSellOrderPrices() {
        try {
            // 查询所有价格未更新的卖出记录
            val pendingRecords = sellMatchRecordRepository.findByPriceUpdatedFalse()
            
            if (pendingRecords.isEmpty()) {
                return
            }
            
            logger.debug("找到 ${pendingRecords.size} 条待更新价格的卖出订单")
            
            for (record in pendingRecords) {
                try {
                    // 获取跟单关系
                    val copyTrading = copyTradingRepository.findById(record.copyTradingId).orElse(null)
                    if (copyTrading == null) {
                        logger.warn("跟单关系不存在，跳过更新: copyTradingId=${record.copyTradingId}")
                        continue
                    }
                    
                    // 获取账户
                    val account = accountRepository.findById(copyTrading.accountId).orElse(null)
                    if (account == null) {
                        logger.warn("账户不存在，跳过更新: accountId=${copyTrading.accountId}")
                        continue
                    }
                    
                    // 检查账户是否配置了 API 凭证
                    if (account.apiKey == null || account.apiSecret == null || account.apiPassphrase == null) {
                        logger.debug("账户未配置 API 凭证，跳过更新: accountId=${account.id}")
                        continue
                    }
                    
                    // 解密 API 凭证
                    val apiSecret = try {
                        cryptoUtils.decrypt(account.apiSecret!!)
                    } catch (e: Exception) {
                        logger.warn("解密 API Secret 失败: accountId=${account.id}, error=${e.message}")
                        continue
                    }
                    
                    val apiPassphrase = try {
                        cryptoUtils.decrypt(account.apiPassphrase!!)
                    } catch (e: Exception) {
                        logger.warn("解密 API Passphrase 失败: accountId=${account.id}, error=${e.message}")
                        continue
                    }
                    
                    // 创建带认证的 CLOB API 客户端
                    val clobApi = retrofitFactory.createClobApi(
                        account.apiKey!!,
                        apiSecret,
                        apiPassphrase,
                        account.walletAddress
                    )
                    
                    // 如果 orderId 不是 0x 开头，直接标记为已更新（不需要通过API查询）
                    if (!record.sellOrderId.startsWith("0x", ignoreCase = true)) {
                        logger.debug("卖出订单ID非0x开头，直接标记为已更新: orderId=${record.sellOrderId}")
                        val updatedRecord = SellMatchRecord(
                            id = record.id,
                            copyTradingId = record.copyTradingId,
                            sellOrderId = record.sellOrderId,
                            leaderSellTradeId = record.leaderSellTradeId,
                            marketId = record.marketId,
                            side = record.side,
                            outcomeIndex = record.outcomeIndex,
                            totalMatchedQuantity = record.totalMatchedQuantity,
                            sellPrice = record.sellPrice,
                            totalRealizedPnl = record.totalRealizedPnl,
                            priceUpdated = true,  // 标记为已更新
                            createdAt = record.createdAt
                        )
                        sellMatchRecordRepository.save(updatedRecord)
                        continue
                    }
                    
                    // 查询订单详情，获取实际成交价
                    val actualSellPrice = trackingService.getActualExecutionPrice(
                        orderId = record.sellOrderId,
                        clobApi = clobApi,
                        fallbackPrice = record.sellPrice
                    )
                    
                    // 如果价格已更新（与当前价格不同），更新数据库
                    if (actualSellPrice != record.sellPrice) {
                        // 重新计算盈亏
                        val details = sellMatchDetailRepository.findByMatchRecordId(record.id!!)
                        var totalRealizedPnl = BigDecimal.ZERO
                        
                        for (detail in details) {
                            val updatedRealizedPnl = actualSellPrice.subtract(detail.buyPrice).multi(detail.matchedQuantity)
                            
                            // 更新明细的卖出价格和盈亏
                            // 注意：SellMatchDetail 的字段都是 val，需要创建新对象
                            val updatedDetail = SellMatchDetail(
                                id = detail.id,
                                matchRecordId = detail.matchRecordId,
                                trackingId = detail.trackingId,
                                buyOrderId = detail.buyOrderId,
                                matchedQuantity = detail.matchedQuantity,
                                buyPrice = detail.buyPrice,
                                sellPrice = actualSellPrice,  // 更新卖出价格
                                realizedPnl = updatedRealizedPnl,  // 更新盈亏
                                createdAt = detail.createdAt
                            )
                            sellMatchDetailRepository.save(updatedDetail)
                            
                            totalRealizedPnl = totalRealizedPnl.add(updatedRealizedPnl)
                        }
                        
                        // 更新卖出记录
                        // 注意：SellMatchRecord 的字段都是 val，需要创建新对象
                        val updatedRecord = SellMatchRecord(
                            id = record.id,
                            copyTradingId = record.copyTradingId,
                            sellOrderId = record.sellOrderId,
                            leaderSellTradeId = record.leaderSellTradeId,
                            marketId = record.marketId,
                            side = record.side,
                            outcomeIndex = record.outcomeIndex,
                            totalMatchedQuantity = record.totalMatchedQuantity,
                            sellPrice = actualSellPrice,  // 更新卖出价格
                            totalRealizedPnl = totalRealizedPnl,  // 更新总盈亏
                            priceUpdated = true,  // 标记为已更新
                            createdAt = record.createdAt
                        )
                        sellMatchRecordRepository.save(updatedRecord)
                        
                        logger.info("更新卖出订单价格成功: orderId=${record.sellOrderId}, 原价格=${record.sellPrice}, 新价格=$actualSellPrice")
                    } else {
                        // 价格相同，但可能已经查询过，标记为已更新
                        val updatedRecord = SellMatchRecord(
                            id = record.id,
                            copyTradingId = record.copyTradingId,
                            sellOrderId = record.sellOrderId,
                            leaderSellTradeId = record.leaderSellTradeId,
                            marketId = record.marketId,
                            side = record.side,
                            outcomeIndex = record.outcomeIndex,
                            totalMatchedQuantity = record.totalMatchedQuantity,
                            sellPrice = record.sellPrice,
                            totalRealizedPnl = record.totalRealizedPnl,
                            priceUpdated = true,  // 标记为已更新
                            createdAt = record.createdAt
                        )
                        sellMatchRecordRepository.save(updatedRecord)
                        logger.debug("卖出订单价格无需更新: orderId=${record.sellOrderId}, price=$actualSellPrice")
                    }
                } catch (e: Exception) {
                    logger.warn("更新卖出订单价格失败: orderId=${record.sellOrderId}, error=${e.message}", e)
                    // 继续处理下一条记录
                }
            }
        } catch (e: Exception) {
            logger.error("更新待更新卖出订单价格异常: ${e.message}", e)
        }
    }
}

