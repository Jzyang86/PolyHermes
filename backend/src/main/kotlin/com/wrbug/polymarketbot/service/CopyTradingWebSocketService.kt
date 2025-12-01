package com.wrbug.polymarketbot.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.wrbug.polymarketbot.api.TradeResponse
import com.wrbug.polymarketbot.entity.Leader
import com.wrbug.polymarketbot.repository.CopyTradingTemplateRepository
import com.wrbug.polymarketbot.websocket.PolymarketWebSocketClient
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 跟单WebSocket监听服务
 * 通过WebSocket订阅Polymarket RTDS的用户交易频道
 */
@Service
class CopyTradingWebSocketService(
    private val copyOrderTrackingService: CopyOrderTrackingService,
    private val templateRepository: CopyTradingTemplateRepository
) {
    
    private val logger = LoggerFactory.getLogger(CopyTradingWebSocketService::class.java)
    
    @Value("\${polymarket.websocket.url:wss://ws-live-data.polymarket.com}")
    private var websocketUrl: String = "wss://ws-live-data.polymarket.com"
    
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 存储每个Leader的WebSocket客户端：leaderId -> WebSocketClient
    private val leaderClients = ConcurrentHashMap<Long, PolymarketWebSocketClient>()
    
    // 存储每个Leader的地址：leaderId -> leaderAddress
    private val leaderAddresses = ConcurrentHashMap<Long, String>()
    
    /**
     * 启动WebSocket监听
     */
    fun start(leaders: List<Leader>) {
        logger.info("启动WebSocket监听，Leader数量: ${leaders.size}")
        
        leaders.forEach { leader ->
            try {
                addLeader(leader)
            } catch (e: Exception) {
                logger.error("添加Leader监听失败: leaderId=${leader.id}, address=${leader.leaderAddress}", e)
            }
        }
    }
    
    /**
     * 添加Leader监听
     */
    fun addLeader(leader: Leader) {
        if (leader.id == null) {
            logger.warn("Leader ID为空，跳过: ${leader.leaderAddress}")
            return
        }
        
        if (leaderClients.containsKey(leader.id)) {
            logger.debug("Leader ${leader.id} 已经在监听中，跳过")
            return
        }
        
        val leaderId = leader.id!!
        val leaderAddress = leader.leaderAddress.lowercase()
        leaderAddresses[leaderId] = leaderAddress
        
        // 创建WebSocket客户端
        val client = PolymarketWebSocketClient(
            url = websocketUrl,
            sessionId = "copy-trading-$leaderId",
            onMessage = { message -> handleMessage(leaderId, message) },
            onOpen = {
                // 连接建立后订阅用户交易频道
                val wsClient = leaderClients[leaderId]
                if (wsClient != null) {
                    subscribeUserTrades(wsClient, leaderAddress)
                }
            },
            onReconnect = {
                // 重连后重新订阅
                val wsClient = leaderClients[leaderId]
                if (wsClient != null) {
                    subscribeUserTrades(wsClient, leaderAddress)
                }
            }
        )
        
        leaderClients[leaderId] = client
        
        // 连接WebSocket
        scope.launch {
            try {
                client.connect()
                logger.info("已启动WebSocket监听: leaderId=$leaderId, address=$leaderAddress")
            } catch (e: Exception) {
                logger.error("连接WebSocket失败: leaderId=$leaderId", e)
                leaderClients.remove(leaderId)
                leaderAddresses.remove(leaderId)
            }
        }
    }
    
    /**
     * 移除Leader监听
     */
    fun removeLeader(leaderId: Long) {
        val client = leaderClients.remove(leaderId)
        leaderAddresses.remove(leaderId)
        
        if (client != null) {
            try {
                client.closeConnection()
                logger.info("已停止WebSocket监听: leaderId=$leaderId")
            } catch (e: Exception) {
                logger.error("关闭WebSocket连接失败: leaderId=$leaderId", e)
            }
        }
    }
    
    /**
     * 停止所有监听
     */
    fun stop() {
        logger.info("停止所有WebSocket监听...")
        val leaderIds = leaderClients.keys.toList()
        leaderIds.forEach { leaderId ->
            removeLeader(leaderId)
        }
    }
    
    /**
     * 订阅用户交易频道
     */
    private fun subscribeUserTrades(client: PolymarketWebSocketClient, userAddress: String) {
        try {
            // 根据Polymarket RTDS API文档，订阅用户交易频道的消息格式
            val subscribeMessage = """
                {
                    "type": "subscribe",
                    "channel": "user",
                    "user": "$userAddress"
                }
            """.trimIndent()
            
            client.sendMessage(subscribeMessage)
            logger.info("已订阅用户交易频道: $userAddress")
        } catch (e: Exception) {
            logger.error("订阅用户交易频道失败: $userAddress", e)
        }
    }
    
    /**
     * 处理WebSocket消息
     */
    private fun handleMessage(leaderId: Long, message: String) {
        try {
            // 处理PONG响应
            if (message.trim() == "PONG") {
                logger.debug("收到PONG响应: leaderId=$leaderId")
                return
            }
            
            // 解析JSON消息
            val json = JsonParser.parseString(message).asJsonObject
            
            // 检查消息类型
            val eventType = json.get("event_type")?.asString
            if (eventType != "trade") {
                logger.debug("忽略非交易事件: leaderId=$leaderId, eventType=$eventType")
                return
            }
            
            // 解析交易数据
            val trade = parseTradeMessage(json)
            if (trade != null) {
                // 处理交易
                scope.launch {
                    try {
                        copyOrderTrackingService.processTrade(leaderId, trade, "websocket")
                    } catch (e: Exception) {
                        logger.error("处理交易失败: leaderId=$leaderId, tradeId=${trade.id}", e)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("处理WebSocket消息失败: leaderId=$leaderId, message=$message", e)
        }
    }
    
    /**
     * 解析交易消息
     * 根据Polymarket RTDS API的trade事件格式解析
     */
    private fun parseTradeMessage(json: JsonObject): TradeResponse? {
        return try {
            // 根据实际API响应格式解析
            // 注意：这里需要根据实际的WebSocket消息格式调整
            val id = json.get("id")?.asString ?: json.get("trade_id")?.asString
            val market = json.get("market")?.asString
            val side = json.get("side")?.asString
            val price = json.get("price")?.asString
            val size = json.get("size")?.asString
            val timestamp = json.get("timestamp")?.asString ?: System.currentTimeMillis().toString()
            val user = json.get("user")?.asJsonObject?.get("address")?.asString
            
            if (id == null || market == null || side == null || price == null || size == null) {
                logger.warn("交易消息缺少必需字段: $json")
                return null
            }
            
            TradeResponse(
                id = id,
                market = market,
                side = side,
                price = price,
                size = size,
                timestamp = timestamp,
                user = user
            )
        } catch (e: Exception) {
            logger.error("解析交易消息失败: $json", e)
            null
        }
    }
}

