-- V11: 微信管理（M2-1.8）——客户微信号 + 微信沟通消息记录工作台
-- 方案：记录式工作台（先 A 后 B）。在系统中记录与客户的微信往来消息（in/out），
-- AI 根据客户画像 + 沟通时间线生成回复建议，人工确认后复制到微信发送（人机协同红线）。
-- 二期对接企业微信 API 真实收发时，direction/content 结构保持兼容。

-- 1. lead 表新增微信号 / 微信昵称
ALTER TABLE lead ADD COLUMN IF NOT EXISTS wechat_id VARCHAR(64);
ALTER TABLE lead ADD COLUMN IF NOT EXISTS wechat_name VARCHAR(64);

-- 2. 微信沟通消息表
-- direction: in（客户发来）/ out（我方发出）
-- status: recorded（直接记录）/ ai_confirmed（AI 建议确认后发出，ai_reply 存建议原文）
CREATE TABLE wechat_message (
    id          BIGSERIAL PRIMARY KEY,
    lead_id     BIGINT NOT NULL REFERENCES lead(id) ON DELETE CASCADE,
    direction   VARCHAR(8) NOT NULL DEFAULT 'in',
    content     TEXT NOT NULL,
    ai_reply    TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'recorded',
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wm_lead ON wechat_message(lead_id);
