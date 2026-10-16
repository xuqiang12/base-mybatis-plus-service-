-- 更正为邮箱验证码注册并设置密码，登录统一使用邮箱或手机号加密码；不改已发布迁移。
ALTER TABLE relations.sys_user RENAME COLUMN email_login_enabled TO email_verified;
COMMENT ON COLUMN relations.sys_user.email_verified IS '是否已通过邮箱验证码验证，不用于免密码登录';
COMMENT ON COLUMN relations.sys_user.phone IS '用户绑定的手机号，唯一，可为空，当前未做短信归属验证';
COMMENT ON TABLE relations.sys_email_code IS '邮箱注册验证码，仅保留每个邮箱最近成功发送的一条';
-- 原登录用途的验证码不能继续用于新注册流程。
UPDATE relations.sys_email_code SET used=TRUE;
