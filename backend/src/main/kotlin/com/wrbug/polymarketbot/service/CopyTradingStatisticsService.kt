package com.wrbug.polymarketbot.service

import com.wrbug.polymarketbot.dto.*
import com.wrbug.polymarketbot.entity.*
import com.wrbug.polymarketbot.repository.*
import com.wrbug.polymarketbot.util.toSafeBigDecimal
import com.wrbug.polymarketbot.util.multi
import com.wrbug.polymarketbot.util.div
import com.wrbug.polymarketbot.util.gt
import com.wrbug.polymarketbot.util.eq
import com.wrbug.polymarketbot.util.lte
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 跟单统计服务
 * 提供统计信息和订单列表查询
 */
@Service
class CopyTradingStatisticsService(
    private val copyTradingRepository: CopyTradingRepository,
    private val copyOrderTrackingRepository: CopyOrderTrackingRepository,
    private val sellMatchRecordRepository: SellMatchRecordRepository,
    private val sellMatchDetailRepository: SellMatchDetailRepository,
    private val accountRepository: AccountRepository,
    private val leaderRepository: LeaderRepository,
    private val templateRepository: CopyTradingTemplateRepository,
    private val accountService: AccountService
) {
    
    private val logger = LoggerFactory.getLogger(CopyTradingStatisticsService::class.java)
    
    /**
     * 获取跟单关系统计
     */
    suspend fun getStatistics(copyTradingId: Long): Result<CopyTradingStatisticsResponse> {
        return try {
            // 1. 获取跟单关系
            val copyTrading = copyTradingRepository.findById(copyTradingId).orElse(null)
                ?: return Result.failure(IllegalArgumentException("跟单关系不存在: $copyTradingId"))
            
            // 2. 获取关联信息
            val account = accountRepository.findById(copyTrading.accountId).orElse(null)
            val leader = leaderRepository.findById(copyTrading.leaderId).orElse(null)
            val template = templateRepository.findById(copyTrading.templateId).orElse(null)
            
            // 3. 获取买入订单
            val buyOrders = copyOrderTrackingRepository.findByCopyTradingId(copyTradingId)
            
            // 4. 获取卖出记录
            val sellRecords = sellMatchRecordRepository.findByCopyTradingId(copyTradingId)
            
            // 5. 获取匹配明细
            val matchDetails = sellMatchDetailRepository.findByCopyTradingId(copyTradingId)
            
            // 6. 计算统计信息
            val statistics = calculateStatistics(buyOrders, sellRecords, matchDetails)
            
            // 7. 获取当前市场价格（用于计算未实现盈亏）
            val currentPrice = getCurrentMarketPrice(buyOrders)
            
            // 8. 计算未实现盈亏
            val unrealizedPnl = calculateUnrealizedPnl(buyOrders, currentPrice)
            
            // 9. 构建响应
            val response = CopyTradingStatisticsResponse(
                copyTradingId = copyTradingId,
                accountId = copyTrading.accountId,
                accountName = account?.accountName,
                leaderId = copyTrading.leaderId,
                leaderName = leader?.leaderName,
                templateId = copyTrading.templateId,
                templateName = template?.templateName,
                enabled = copyTrading.enabled,
                totalBuyQuantity = statistics.totalBuyQuantity,
                totalBuyOrders = statistics.totalBuyOrders,
                totalBuyAmount = statistics.totalBuyAmount,
                avgBuyPrice = statistics.avgBuyPrice,
                totalSellQuantity = statistics.totalSellQuantity,
                totalSellOrders = statistics.totalSellOrders,
                totalSellAmount = statistics.totalSellAmount,
                currentPositionQuantity = statistics.currentPositionQuantity,
                currentPositionValue = calculatePositionValue(statistics.currentPositionQuantity, currentPrice),
                totalRealizedPnl = statistics.totalRealizedPnl,
                totalUnrealizedPnl = unrealizedPnl,
                totalPnl = (statistics.totalRealizedPnl.toSafeBigDecimal().add(unrealizedPnl.toSafeBigDecimal())).toString(),
                totalPnlPercent = calculatePnlPercent(statistics.totalBuyAmount, statistics.totalRealizedPnl, unrealizedPnl)
            )
            
            Result.success(response)
        } catch (e: Exception) {
            logger.error("获取统计信息失败: copyTradingId=$copyTradingId", e)
            Result.failure(e)
        }
    }
    
    /**
     * 查询订单列表
     */
    fun getOrderList(request: OrderTrackingRequest): Result<OrderListResponse> {
        return try {
            // 1. 验证跟单关系
            val copyTrading = copyTradingRepository.findById(request.copyTradingId).orElse(null)
                ?: return Result.failure(IllegalArgumentException("跟单关系不存在: ${request.copyTradingId}"))
            
            // 2. 根据类型查询
            val (list, total) = when (request.type.lowercase()) {
                "buy" -> getBuyOrderList(request)
                "sell" -> getSellOrderList(request)
                "matched" -> getMatchedOrderList(request)
                else -> return Result.failure(IllegalArgumentException("不支持的订单类型: ${request.type}"))
            }
            
            // 3. 构建响应
            val response = OrderListResponse(
                list = list,
                total = total,
                page = request.page ?: 1,
                limit = request.limit ?: 20
            )
            
            Result.success(response)
        } catch (e: Exception) {
            logger.error("查询订单列表失败: ${request.copyTradingId}, type=${request.type}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取买入订单列表
     */
    private fun getBuyOrderList(request: OrderTrackingRequest): Pair<List<BuyOrderInfo>, Long> {
        var orders = copyOrderTrackingRepository.findByCopyTradingId(request.copyTradingId)
        
        // 筛选
        if (!request.marketId.isNullOrBlank()) {
            orders = orders.filter { it.marketId == request.marketId }
        }
        if (!request.side.isNullOrBlank()) {
            orders = orders.filter { it.side == request.side }
        }
        if (!request.status.isNullOrBlank()) {
            orders = orders.filter { it.status == request.status }
        }
        
        val total = orders.size.toLong()
        
        // 排序（按创建时间倒序）
        orders = orders.sortedByDescending { it.createdAt }
        
        // 分页
        val page = (request.page ?: 1) - 1
        val limit = request.limit ?: 20
        val start = page * limit
        val end = minOf(start + limit, orders.size)
        val pagedOrders = if (start < orders.size) orders.subList(start, end) else emptyList()
        
        // 转换为DTO
        val list = pagedOrders.map { order ->
            val amount = order.quantity.toSafeBigDecimal().multi(order.price)
            BuyOrderInfo(
                orderId = order.buyOrderId,
                leaderTradeId = order.leaderBuyTradeId,
                marketId = order.marketId,
                side = order.side,
                quantity = order.quantity.toString(),
                price = order.price.toString(),
                amount = amount.toString(),
                matchedQuantity = order.matchedQuantity.toString(),
                remainingQuantity = order.remainingQuantity.toString(),
                status = order.status,
                createdAt = order.createdAt
            )
        }
        
        return Pair(list, total)
    }
    
    /**
     * 获取卖出订单列表
     */
    private fun getSellOrderList(request: OrderTrackingRequest): Pair<List<SellOrderInfo>, Long> {
        var records = sellMatchRecordRepository.findByCopyTradingId(request.copyTradingId)
        
        // 筛选
        if (!request.marketId.isNullOrBlank()) {
            records = records.filter { it.marketId == request.marketId }
        }
        if (!request.side.isNullOrBlank()) {
            records = records.filter { it.side == request.side }
        }
        
        val total = records.size.toLong()
        
        // 排序（按创建时间倒序）
        records = records.sortedByDescending { it.createdAt }
        
        // 分页
        val page = (request.page ?: 1) - 1
        val limit = request.limit ?: 20
        val start = page * limit
        val end = minOf(start + limit, records.size)
        val pagedRecords = if (start < records.size) records.subList(start, end) else emptyList()
        
        // 转换为DTO
        val list = pagedRecords.map { record ->
            val amount = record.totalMatchedQuantity.toSafeBigDecimal().multi(record.sellPrice)
            SellOrderInfo(
                orderId = record.sellOrderId,
                leaderTradeId = record.leaderSellTradeId,
                marketId = record.marketId,
                side = record.side,
                quantity = record.totalMatchedQuantity.toString(),
                price = record.sellPrice.toString(),
                amount = amount.toString(),
                realizedPnl = record.totalRealizedPnl.toString(),
                createdAt = record.createdAt
            )
        }
        
        return Pair(list, total)
    }
    
    /**
     * 获取匹配订单列表
     */
    private fun getMatchedOrderList(request: OrderTrackingRequest): Pair<List<MatchedOrderInfo>, Long> {
        val matchDetails = sellMatchDetailRepository.findByCopyTradingId(request.copyTradingId)
        
        // 筛选
        var filtered = matchDetails
        if (!request.sellOrderId.isNullOrBlank()) {
            val sellRecord = sellMatchRecordRepository.findBySellOrderId(request.sellOrderId)
            if (sellRecord != null) {
                filtered = filtered.filter { it.matchRecordId == sellRecord.id }
            } else {
                filtered = emptyList()
            }
        }
        if (!request.buyOrderId.isNullOrBlank()) {
            filtered = filtered.filter { it.buyOrderId == request.buyOrderId }
        }
        
        val total = filtered.size.toLong()
        
        // 排序（按创建时间倒序）
        filtered = filtered.sortedByDescending { it.createdAt }
        
        // 分页
        val page = (request.page ?: 1) - 1
        val limit = request.limit ?: 20
        val start = page * limit
        val end = minOf(start + limit, filtered.size)
        val pagedDetails = if (start < filtered.size) filtered.subList(start, end) else emptyList()
        
        // 转换为DTO
        val list = pagedDetails.map { detail ->
            MatchedOrderInfo(
                sellOrderId = sellMatchRecordRepository.findById(detail.matchRecordId).orElse(null)?.sellOrderId ?: "",
                buyOrderId = detail.buyOrderId,
                matchedQuantity = detail.matchedQuantity.toString(),
                buyPrice = detail.buyPrice.toString(),
                sellPrice = detail.sellPrice.toString(),
                realizedPnl = detail.realizedPnl.toString(),
                matchedAt = detail.createdAt
            )
        }
        
        return Pair(list, total)
    }
    
    /**
     * 计算统计信息
     */
    private fun calculateStatistics(
        buyOrders: List<CopyOrderTracking>,
        sellRecords: List<SellMatchRecord>,
        matchDetails: List<SellMatchDetail>
    ): StatisticsData {
        // 买入统计
        val totalBuyQuantity = buyOrders.sumOf { it.quantity.toSafeBigDecimal() }
        val totalBuyAmount = buyOrders.sumOf { it.quantity.toSafeBigDecimal().multi(it.price) }
        val totalBuyOrders = buyOrders.size.toLong()
        val avgBuyPrice = if (totalBuyQuantity.gt(BigDecimal.ZERO)) {
            totalBuyAmount.div(totalBuyQuantity)
        } else {
            BigDecimal.ZERO
        }
        
        // 卖出统计
        val totalSellQuantity = sellRecords.sumOf { it.totalMatchedQuantity.toSafeBigDecimal() }
        val totalSellAmount = sellRecords.sumOf { it.totalMatchedQuantity.toSafeBigDecimal().multi(it.sellPrice) }
        val totalSellOrders = sellRecords.size.toLong()
        
        // 持仓统计
        val currentPositionQuantity = buyOrders.sumOf { it.remainingQuantity.toSafeBigDecimal() }
        
        // 已实现盈亏
        val totalRealizedPnl = matchDetails.sumOf { it.realizedPnl.toSafeBigDecimal() }
        
        return StatisticsData(
            totalBuyQuantity = totalBuyQuantity.toString(),
            totalBuyOrders = totalBuyOrders,
            totalBuyAmount = totalBuyAmount.toString(),
            avgBuyPrice = avgBuyPrice.toString(),
            totalSellQuantity = totalSellQuantity.toString(),
            totalSellOrders = totalSellOrders,
            totalSellAmount = totalSellAmount.toString(),
            currentPositionQuantity = currentPositionQuantity.toString(),
            totalRealizedPnl = totalRealizedPnl.toString()
        )
    }
    
    /**
     * 获取当前市场价格
     */
    private suspend fun getCurrentMarketPrice(buyOrders: List<CopyOrderTracking>): Map<String, String> {
        val prices = mutableMapOf<String, String>()
        
        // 获取所有不同的市场ID
        val marketIds = buyOrders.map { it.marketId }.distinct()
        
        for (marketId in marketIds) {
            try {
                val result = accountService.getMarketPrice(marketId)
                result.onSuccess { response ->
                    // 使用中间价，如果没有则使用最后价格
                    val price = response.midpoint ?: response.lastPrice
                    if (price != null) {
                        prices[marketId] = price
                    }
                }
            } catch (e: Exception) {
                logger.warn("获取市场价格失败: marketId=$marketId", e)
            }
        }
        
        return prices
    }
    
    /**
     * 计算未实现盈亏
     */
    private fun calculateUnrealizedPnl(
        buyOrders: List<CopyOrderTracking>,
        currentPrices: Map<String, String>
    ): String {
        var totalUnrealizedPnl = BigDecimal.ZERO
        
        for (order in buyOrders) {
            val remainingQty = order.remainingQuantity.toSafeBigDecimal()
            if (remainingQty.lte(BigDecimal.ZERO)) continue
            
            val currentPrice = currentPrices[order.marketId]?.toSafeBigDecimal()
                ?: continue  // 如果没有当前价格，跳过
            
            val buyPrice = order.price.toSafeBigDecimal()
            val unrealizedPnl = currentPrice.subtract(buyPrice).multi(remainingQty)
            totalUnrealizedPnl = totalUnrealizedPnl.add(unrealizedPnl)
        }
        
        return totalUnrealizedPnl.toString()
    }
    
    /**
     * 计算持仓价值
     */
    private fun calculatePositionValue(positionQuantity: String, currentPrices: Map<String, String>): String {
        // 这里简化处理，实际应该根据每个市场的持仓分别计算
        // 暂时返回0，因为需要知道每个市场的持仓数量
        return "0"
    }
    
    /**
     * 计算盈亏百分比
     */
    private fun calculatePnlPercent(
        totalBuyAmount: String,
        totalRealizedPnl: String,
        totalUnrealizedPnl: String
    ): String {
        val buyAmount = totalBuyAmount.toSafeBigDecimal()
        if (buyAmount.lte(BigDecimal.ZERO)) return "0"
        
        val totalPnl = totalRealizedPnl.toSafeBigDecimal().add(totalUnrealizedPnl.toSafeBigDecimal())
        val percent = totalPnl.div(buyAmount).multi(100)
        
        return percent.setScale(2, RoundingMode.HALF_UP).toString()
    }
    
    /**
     * 统计数据结构
     */
    private data class StatisticsData(
        val totalBuyQuantity: String,
        val totalBuyOrders: Long,
        val totalBuyAmount: String,
        val avgBuyPrice: String,
        val totalSellQuantity: String,
        val totalSellOrders: Long,
        val totalSellAmount: String,
        val currentPositionQuantity: String,
        val totalRealizedPnl: String
    )
}

