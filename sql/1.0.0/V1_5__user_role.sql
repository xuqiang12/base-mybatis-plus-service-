-- 用户与角色关联表，由 system-service 管理；依赖用户表和角色表。
CREATE TABLE relations.sys_user_role (
    user_id INTEGER NOT NULL REFERENCES relations.sys_user(id),
    role_id INTEGER NOT NULL REFERENCES relations.sys_role(id),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_role_role ON relations.sys_user_role(role_id);

COMMENT ON TABLE relations.sys_user_role IS '用户与角色关联表';
COMMENT ON COLUMN relations.sys_user_role.user_id IS '用户ID，关联用户表';
COMMENT ON COLUMN relations.sys_user_role.role_id IS '角色ID，关联角色表';
