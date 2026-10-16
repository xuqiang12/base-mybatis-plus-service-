-- 邮箱验证码登录：旧账号不自动启用，验证码及发送限额由 system-service 管理。
ALTER TABLE relations.sys_user ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE relations.sys_user ALTER COLUMN password DROP NOT NULL;
ALTER TABLE relations.sys_user ADD COLUMN email_login_enabled BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN relations.sys_user.phone IS '手机号，唯一，邮箱验证码账号可为空';
COMMENT ON COLUMN relations.sys_user.password IS '密码的 BCrypt 散列值，未设置密码时为空';
COMMENT ON COLUMN relations.sys_user.email_login_enabled IS '是否由邮箱验证码流程创建并允许验证码登录，旧账号默认关闭';

CREATE TABLE relations.sys_email_code (
    email VARCHAR(254) PRIMARY KEY,
    code_hash VARCHAR(64),
    expires_at TIMESTAMP WITH TIME ZONE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    used BOOLEAN NOT NULL DEFAULT TRUE
);
COMMENT ON TABLE relations.sys_email_code IS '邮箱登录验证码，仅保留每个邮箱最近成功发送的一条';
COMMENT ON COLUMN relations.sys_email_code.email IS '已规范化的邮箱';
COMMENT ON COLUMN relations.sys_email_code.code_hash IS '验证码的 HMAC-SHA256 摘要，不保存明文';
COMMENT ON COLUMN relations.sys_email_code.expires_at IS '验证码过期时间';
COMMENT ON COLUMN relations.sys_email_code.failed_attempts IS '当前验证码校验失败次数';
COMMENT ON COLUMN relations.sys_email_code.used IS '验证码是否已使用';

CREATE TABLE relations.sys_email_send_limit (
    scope_key VARCHAR(320) PRIMARY KEY,
    send_date DATE NOT NULL,
    send_count INTEGER NOT NULL DEFAULT 0,
    last_sent_at TIMESTAMP WITH TIME ZONE NOT NULL
);
COMMENT ON TABLE relations.sys_email_send_limit IS '按邮箱和来源 IP 分别统计邮件发送尝试，行锁保护并发';
COMMENT ON COLUMN relations.sys_email_send_limit.scope_key IS '限额对象，email:邮箱或ip:来源地址';
COMMENT ON COLUMN relations.sys_email_send_limit.send_date IS '按上海时区统计的日期';
COMMENT ON COLUMN relations.sys_email_send_limit.send_count IS '当日发送尝试次数，包含 SMTP 失败';
COMMENT ON COLUMN relations.sys_email_send_limit.last_sent_at IS '最近一次发送尝试时间';
