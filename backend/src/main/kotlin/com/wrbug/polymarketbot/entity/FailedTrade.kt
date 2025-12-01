package com.wrbug.polymarketbot.entity

import jakarta.persistence.*

/**
 * 失败交易实体
 * 记录处理失败的交易信息
 */
@Entity
@Table(name = "failed_trade")
data class FailedTrade(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(name = "leader_id", nullable = false)
    val leaderId: Long,
    
    @Column(name = "leader_trade_id", nullable = false, length = 100)
    val leaderTradeId: String,  // Leader 的交易ID
    
    @Column(name = "trade_type", nullable = false, length = 10)
    val tradeType: String,  // BUY 或 SELL
    
    @Column(name = "copy_trading_id", nullable = false)
    val copyTradingId: Long,
    
    @Column(name = "account_id", nullable = false)
    val accountId: Long,
    
    @Column(name = "market_id", nullable = false, length = 100)
    val marketId: String,
    
    @Column(name = "side", nullable = false, length = 10)
    val side: String,  // YES/NO
    
    @Column(name = "price", nullable = false, length = 50)
    val price: String,  // 价格（字符串格式）
    
    @Column(name = "size", nullable = false, length = 50)
    val size: String,  // 数量（字符串格式）
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,  // 错误信息
    
    @Column(name = "retry_count", nullable = false)
    val retryCount: Int = 0,  // 重试次数
    
    @Column(name = "failed_at", nullable = false)
    val failedAt: Long = System.currentTimeMillis(),
    
    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis()
)

