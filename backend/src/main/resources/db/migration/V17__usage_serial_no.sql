-- M4.5b token 消耗绑定激活码：ai_usage_log 记录每次调用所属的激活码序列号，
-- 额度统计按 serial_no 独立计算（重新激活后新激活码从 0 开始，不受历史消耗影响）
ALTER TABLE ai_usage_log ADD COLUMN serial_no VARCHAR(64);
CREATE INDEX idx_usage_serial ON ai_usage_log (serial_no);
