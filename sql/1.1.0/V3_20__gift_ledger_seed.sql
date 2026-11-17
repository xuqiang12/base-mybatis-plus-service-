-- 权限前缀统一为 gift ledger；保留原权限 ID 与全部关联，重复编码冲突时回滚。
UPDATE relations.sys_permission SET code = 'gl:' || SUBSTRING(code FROM 5) WHERE code LIKE 'pms:%';
UPDATE relations.sys_permission SET code = 'gl:' || code WHERE code LIKE 'system:%';

ALTER TABLE relations.sys_role ADD COLUMN seed_code VARCHAR(100) UNIQUE;
COMMENT ON COLUMN relations.sys_role.seed_code IS '内置角色稳定编码，后台改名后仍用于初始化匹配，自建角色为空';

CREATE TABLE relations.sys_seed_history (
    module VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (module, version)
);
COMMENT ON TABLE relations.sys_seed_history IS '系统数据初始化记录，与初始化数据在同一事务提交';
COMMENT ON COLUMN relations.sys_seed_history.module IS '初始化模块编码';
COMMENT ON COLUMN relations.sys_seed_history.version IS '模块初始化版本，升级通过新增版本执行';
COMMENT ON COLUMN relations.sys_seed_history.completed_at IS '该版本初始化完成时间';
