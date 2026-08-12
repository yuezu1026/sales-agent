-- V23 个人资料：users 表新增邮箱/微信/电话（公司名沿用 tenants.name）
ALTER TABLE users ADD COLUMN email VARCHAR(128);
ALTER TABLE users ADD COLUMN wechat VARCHAR(64);
ALTER TABLE users ADD COLUMN phone VARCHAR(32);
