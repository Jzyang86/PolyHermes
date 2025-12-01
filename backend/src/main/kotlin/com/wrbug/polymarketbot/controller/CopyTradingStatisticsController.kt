package com.wrbug.polymarketbot.controller

import com.wrbug.polymarketbot.dto.*
import com.wrbug.polymarketbot.service.CopyTradingStatisticsService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 跟单统计控制器
 * 提供统计信息和订单列表查询接口
 */
@RestController
@RequestMapping("/api/copy-trading/statistics")
class CopyTradingStatisticsController(
    private val statisticsService: CopyTradingStatisticsService
) {
    
    private val logger = LoggerFactory.getLogger(CopyTradingStatisticsController::class.java)
    
    /**
     * 查询跟单统计详情
     * POST /api/copy-trading/statistics/detail
     */
    @PostMapping("/detail")
    fun getStatisticsDetail(@RequestBody request: StatisticsDetailRequest): ResponseEntity<ApiResponse<CopyTradingStatisticsResponse>> {
        return try {
            if (request.copyTradingId <= 0) {
                return ResponseEntity.ok(ApiResponse.paramError("跟单关系ID无效"))
            }
            
            val result = runBlocking { statisticsService.getStatistics(request.copyTradingId) }
            result.fold(
                onSuccess = { response ->
                    logger.info("成功获取统计信息: copyTradingId=${request.copyTradingId}")
                    ResponseEntity.ok(ApiResponse.success(response))
                },
                onFailure = { e ->
                    logger.error("获取统计信息失败: copyTradingId=${request.copyTradingId}", e)
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.ok(ApiResponse.paramError(e.message ?: "参数错误"))
                        else -> ResponseEntity.ok(ApiResponse.serverError("获取统计信息失败: ${e.message}"))
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("获取统计信息异常: copyTradingId=${request.copyTradingId}", e)
            ResponseEntity.ok(ApiResponse.serverError("获取统计信息失败: ${e.message}"))
        }
    }
}

/**
 * 订单跟踪控制器
 * 提供订单列表查询接口
 */
@RestController
@RequestMapping("/api/copy-trading/orders")
class CopyOrderTrackingController(
    private val statisticsService: CopyTradingStatisticsService
) {
    
    private val logger = LoggerFactory.getLogger(CopyOrderTrackingController::class.java)
    
    /**
     * 查询订单列表（买入/卖出/匹配）
     * POST /api/copy-trading/orders/tracking
     */
    @PostMapping("/tracking")
    fun getOrderList(@RequestBody request: OrderTrackingRequest): ResponseEntity<ApiResponse<OrderListResponse>> {
        return try {
            if (request.copyTradingId <= 0) {
                return ResponseEntity.ok(ApiResponse.paramError("跟单关系ID无效"))
            }
            
            if (request.type.isBlank()) {
                return ResponseEntity.ok(ApiResponse.paramError("订单类型不能为空"))
            }
            
            val validTypes = listOf("buy", "sell", "matched")
            if (!validTypes.contains(request.type.lowercase())) {
                return ResponseEntity.ok(ApiResponse.paramError("订单类型无效，必须是: buy, sell, matched"))
            }
            
            val result = statisticsService.getOrderList(request)
            result.fold(
                onSuccess = { response ->
                    logger.info("成功查询订单列表: copyTradingId=${request.copyTradingId}, type=${request.type}, total=${response.total}")
                    ResponseEntity.ok(ApiResponse.success(response))
                },
                onFailure = { e ->
                    logger.error("查询订单列表失败: copyTradingId=${request.copyTradingId}, type=${request.type}", e)
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.ok(ApiResponse.paramError(e.message ?: "参数错误"))
                        else -> ResponseEntity.ok(ApiResponse.serverError("查询订单列表失败: ${e.message}"))
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("查询订单列表异常: copyTradingId=${request.copyTradingId}, type=${request.type}", e)
            ResponseEntity.ok(ApiResponse.serverError("查询订单列表失败: ${e.message}"))
        }
    }
}

