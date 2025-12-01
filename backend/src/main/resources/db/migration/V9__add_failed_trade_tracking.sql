-- 修改已处理交易表，添加状态字段
ALTER TABLE processed_trade 
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '处理状态：SUCCESS（成功）、FAILED（失败）' AFTER source;

-- 创建失败交易记录表
CREATE TABLE IF NOT EXISTS failed_trade (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    leader_id BIGINT NOT NULL COMMENT 'Leader ID',
    leader_trade_id VARCHAR(100) NOT NULL COMMENT 'Leader 的交易ID',
    trade_type VARCHAR(10) NOT NULL COMMENT '交易类型：BUY 或 SELL',
    copy_trading_id BIGINT NOT NULL COMMENT '跟单关系ID',
    account_id BIGINT NOT NULL COMMENT '账户ID',
    market_id VARCHAR(100) NOT NULL COMMENT '市场地址',
    side VARCHAR(10) NOT NULL COMMENT '方向：YES/NO',
    price VARCHAR(50) NOT NULL COMMENT '价格',
    size VARCHAR(50) NOT NULL COMMENT '数量',
    error_message TEXT COMMENT '错误信息',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    failed_at BIGINT NOT NULL COMMENT '失败时间（毫秒时间戳）',
    created_at BIGINT NOT NULL COMMENT '创建时间（毫秒时间戳）',
    INDEX idx_leader_trade (leader_id, leader_trade_id),
    INDEX idx_copy_trading (copy_trading_id),
    INDEX idx_failed_at (failed_at),
    FOREIGN KEY (copy_trading_id) REFERENCES copy_trading(id) ON DELETE CASCADE,
    FOREIGN KEY (leader_id) REFERENCES copy_trading_leaders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='失败交易记录表';

