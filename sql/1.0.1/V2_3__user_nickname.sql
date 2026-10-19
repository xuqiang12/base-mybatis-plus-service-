-- 昵称用于用户展示，允许重复；已有账号保留为空，新注册请求必须填写。
ALTER TABLE relations.sys_user ADD COLUMN nickname VARCHAR(100);
COMMENT ON COLUMN relations.sys_user.nickname IS '用户昵称，允许重复，历史账号可为空';
