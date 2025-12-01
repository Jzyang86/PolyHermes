package com.wrbug.polymarketbot.repository

import com.wrbug.polymarketbot.entity.FailedTrade
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 失败交易Repository
 */
@Repository
interface FailedTradeRepository : JpaRepository<FailedTrade, Long> {
    
    /**
     * 根据Leader ID和交易ID查询
     */
    fun findByLeaderIdAndLeaderTradeId(leaderId: Long, leaderTradeId: String): FailedTrade?
    
    /**
     * 检查是否存在失败的交易
     */
    fun existsByLeaderIdAndLeaderTradeId(leaderId: Long, leaderTradeId: String): Boolean
}

