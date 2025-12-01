package com.wrbug.polymarketbot.repository

import com.wrbug.polymarketbot.entity.CopyTrading
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 跟单关系 Repository
 */
@Repository
interface CopyTradingRepository : JpaRepository<CopyTrading, Long> {
    
    /**
     * 根据账户ID查找跟单列表
     */
    fun findByAccountId(accountId: Long): List<CopyTrading>
    
    /**
     * 根据模板ID查找跟单列表
     */
    fun findByTemplateId(templateId: Long): List<CopyTrading>
    
    /**
     * 根据 Leader ID 查找跟单列表
     */
    fun findByLeaderId(leaderId: Long): List<CopyTrading>
    
    /**
     * 根据账户ID和模板ID查找跟单列表
     */
    fun findByAccountIdAndTemplateId(accountId: Long, templateId: Long): List<CopyTrading>
    
    /**
     * 根据账户ID、模板ID和Leader ID查找跟单
     */
    fun findByAccountIdAndTemplateIdAndLeaderId(
        accountId: Long,
        templateId: Long,
        leaderId: Long
    ): CopyTrading?
    
    /**
     * 查找所有启用的跟单
     */
    fun findByEnabledTrue(): List<CopyTrading>
    
    /**
     * 根据账户ID查找启用的跟单
     */
    fun findByAccountIdAndEnabledTrue(accountId: Long): List<CopyTrading>
    
    /**
     * 根据Leader ID查找启用的跟单
     */
    fun findByLeaderIdAndEnabledTrue(leaderId: Long): List<CopyTrading>
    
    /**
     * 统计使用指定模板的跟单数量
     */
    fun countByTemplateId(templateId: Long): Long
    
    /**
     * 统计指定 Leader 的跟单数量
     */
    fun countByLeaderId(leaderId: Long): Long
}

