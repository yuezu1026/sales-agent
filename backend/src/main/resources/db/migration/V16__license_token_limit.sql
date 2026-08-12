-- M4.5 试用 token 额度：授权时在签名激活码中设定 token 消耗上限（0=不限制，正式版）
ALTER TABLE license ADD COLUMN token_limit BIGINT NOT NULL DEFAULT 0;
