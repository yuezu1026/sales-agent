-- M4: License 签名激活码改造
-- license 表新增 serial_no（激活码内唯一序列号，防重复使用）
ALTER TABLE license ADD COLUMN IF NOT EXISTS serial_no VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_license_serial ON license (serial_no);
