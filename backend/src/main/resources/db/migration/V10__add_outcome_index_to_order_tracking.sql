-- 添加 outcome_index 字段到订单跟踪表和卖出匹配记录表
-- 支持多元市场（不限于YES/NO）

-- 1. 添加 outcome_index 字段到 copy_order_tracking 表
ALTER TABLE copy_order_tracking 
ADD COLUMN outcome_index INT NULL COMMENT '结果索引（0, 1, 2, ...），支持多元市场' AFTER side;

-- 2. 添加 outcome_index 字段到 sell_match_record 表
ALTER TABLE sell_match_record 
ADD COLUMN outcome_index INT NULL COMMENT '结果索引（0, 1, 2, ...），支持多元市场' AFTER side;

-- 3. 添加索引以优化查询性能
ALTER TABLE copy_order_tracking 
ADD INDEX idx_market_outcome (market_id, outcome_index);

ALTER TABLE sell_match_record 
ADD INDEX idx_market_outcome (market_id, outcome_index);

