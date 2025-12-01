-- 创建订单跟踪表
CREATE TABLE IF NOT EXISTS copy_order_tracking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    copy_trading_id BIGINT NOT NULL COMMENT '跟单关系ID',
    account_id BIGINT NOT NULL COMMENT '账户ID',
    leader_id BIGINT NOT NULL COMMENT 'Leader ID',
    template_id BIGINT NOT NULL COMMENT '模板ID',
    market_id VARCHAR(100) NOT NULL COMMENT '市场地址',
    side VARCHAR(10) NOT NULL COMMENT '方向：YES/NO',
    buy_order_id VARCHAR(100) NOT NULL COMMENT '跟单买入订单ID',
    leader_buy_trade_id VARCHAR(100) NOT NULL COMMENT 'Leader 买入交易ID',
    quantity DECIMAL(20, 8) NOT NULL COMMENT '买入数量',
    price DECIMAL(20, 8) NOT NULL COMMENT '买入价格',
    matched_quantity DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '已匹配卖出数量',
    remaining_quantity DECIMAL(20, 8) NOT NULL COMMENT '剩余未匹配数量',
    status VARCHAR(20) NOT NULL COMMENT '状态：filled, fully_matched, partially_matched',
    created_at BIGINT NOT NULL COMMENT '创建时间（毫秒时间戳）',
    updated_at BIGINT NOT NULL COMMENT '更新时间（毫秒时间戳）',
    INDEX idx_copy_trading (copy_trading_id),
    INDEX idx_remaining (remaining_quantity, status),
    INDEX idx_market_side (market_id, side),
    INDEX idx_leader_trade (leader_id, leader_buy_trade_id),
    FOREIGN KEY (copy_trading_id) REFERENCES copy_trading(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单跟踪表';

-- 创建卖出匹配记录表
CREATE TABLE IF NOT EXISTS sell_match_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    copy_trading_id BIGINT NOT NULL COMMENT '跟单关系ID',
    sell_order_id VARCHAR(100) NOT NULL COMMENT '跟单卖出订单ID',
    leader_sell_trade_id VARCHAR(100) NOT NULL COMMENT 'Leader 卖出交易ID',
    market_id VARCHAR(100) NOT NULL COMMENT '市场地址',
    side VARCHAR(10) NOT NULL COMMENT '方向：YES/NO',
    total_matched_quantity DECIMAL(20, 8) NOT NULL COMMENT '总匹配数量',
    sell_price DECIMAL(20, 8) NOT NULL COMMENT '卖出价格',
    total_realized_pnl DECIMAL(20, 8) NOT NULL COMMENT '总已实现盈亏',
    created_at BIGINT NOT NULL COMMENT '创建时间（毫秒时间戳）',
    INDEX idx_copy_trading (copy_trading_id),
    INDEX idx_sell_order (sell_order_id),
    INDEX idx_leader_trade (leader_sell_trade_id),
    FOREIGN KEY (copy_trading_id) REFERENCES copy_trading(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卖出匹配记录表';

-- 创建匹配明细表
CREATE TABLE IF NOT EXISTS sell_match_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_record_id BIGINT NOT NULL COMMENT '关联 sell_match_record.id',
    tracking_id BIGINT NOT NULL COMMENT '关联 copy_order_tracking.id',
    buy_order_id VARCHAR(100) NOT NULL COMMENT '买入订单ID',
    matched_quantity DECIMAL(20, 8) NOT NULL COMMENT '匹配的数量',
    buy_price DECIMAL(20, 8) NOT NULL COMMENT '买入价格',
    sell_price DECIMAL(20, 8) NOT NULL COMMENT '卖出价格',
    realized_pnl DECIMAL(20, 8) NOT NULL COMMENT '盈亏 = (sell_price - buy_price) * matched_quantity',
    created_at BIGINT NOT NULL COMMENT '创建时间（毫秒时间戳）',
    INDEX idx_match_record (match_record_id),
    INDEX idx_tracking (tracking_id),
    INDEX idx_buy_order (buy_order_id),
    FOREIGN KEY (match_record_id) REFERENCES sell_match_record(id) ON DELETE CASCADE,
    FOREIGN KEY (tracking_id) REFERENCES copy_order_tracking(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='匹配明细表';

-- 创建已处理交易表（用于去重）
CREATE TABLE IF NOT EXISTS processed_trade (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    leader_id BIGINT NOT NULL COMMENT 'Leader ID',
    leader_trade_id VARCHAR(100) NOT NULL COMMENT 'Leader 的交易ID（trade.id，唯一标识）',
    trade_type VARCHAR(10) NOT NULL COMMENT '交易类型：BUY 或 SELL',
    source VARCHAR(20) NOT NULL COMMENT '数据来源：websocket 或 polling',
    processed_at BIGINT NOT NULL COMMENT '处理时间（毫秒时间戳）',
    created_at BIGINT NOT NULL COMMENT '创建时间（毫秒时间戳）',
    UNIQUE KEY uk_leader_trade (leader_id, leader_trade_id),
    INDEX idx_processed_at (processed_at),
    INDEX idx_leader_id (leader_id),
    FOREIGN KEY (leader_id) REFERENCES copy_trading_leaders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已处理交易表（用于去重）';

