-- M4: 签名激活码（Ed25519）远长于旧版 22 字符格式，扩大 license_key 列
ALTER TABLE license ALTER COLUMN license_key TYPE VARCHAR(512);
