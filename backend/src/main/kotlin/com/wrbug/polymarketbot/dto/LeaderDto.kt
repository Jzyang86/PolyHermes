package com.wrbug.polymarketbot.dto

/**
 * Leader 添加请求
 */
data class LeaderAddRequest(
    val leaderAddress: String,
    val leaderName: String? = null,
    val category: String? = null,  // sports 或 crypto
    val remark: String? = null,  // Leader 备注（可选）
    val website: String? = null  // Leader 网站（可选）
)

/**
 * Leader 更新请求
 */
data class LeaderUpdateRequest(
    val leaderId: Long,
    val leaderName: String? = null,
    val category: String? = null,
    val remark: String? = null,  // Leader 备注（可选）
    val website: String? = null  // Leader 网站（可选）
)

/**
 * Leader 删除请求
 */
data class LeaderDeleteRequest(
    val leaderId: Long
)

/**
 * Leader 列表请求
 */
data class LeaderListRequest(
    val category: String? = null  // sports 或 crypto
)

/**
 * Leader 信息响应
 */
data class LeaderDto(
    val id: Long,
    val leaderAddress: String,
    val leaderName: String?,
    val category: String?,
    val remark: String? = null,  // Leader 备注（可选）
    val website: String? = null,  // Leader 网站（可选）
    val copyTradingCount: Long = 0,  // 跟单关系数量
    val totalOrders: Long? = null,  // 总订单数（可选）
    val totalPnl: String? = null,  // 总盈亏（可选）
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Leader 列表响应
 */
data class LeaderListResponse(
    val list: List<LeaderDto>,
    val total: Long
)

