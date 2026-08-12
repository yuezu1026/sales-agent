-- V20: 登录日志增加 IP 与地理归属（M7.13 访问者地理分布图）
-- ip：登录时客户端真实 IP（nginx 透传 X-Forwarded-For / X-Real-IP）
-- geo：登录时用内置 ip2region.xdb 一次性解析固化，格式 国家|省|市|ISP|国家码（如 中国|江苏省|南京市|0|CN）
-- 历史记录（V20 之前）两列为 NULL，地图只统计有 geo 的记录。

ALTER TABLE login_logs ADD COLUMN ip  VARCHAR(64);
ALTER TABLE login_logs ADD COLUMN geo VARCHAR(128);
