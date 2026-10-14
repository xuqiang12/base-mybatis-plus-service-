-- 角色与权限关联表，由 system-service 管理；删除角色时自动删除其授权关系。
CREATE TABLE relations.sys_role_permission (
    role_id INTEGER NOT NULL REFERENCES relations.sys_role(id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES relations.sys_permission(id),
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX idx_role_permission_permission ON relations.sys_role_permission(permission_id);

COMMENT ON TABLE relations.sys_role_permission IS '角色与权限关联表';
COMMENT ON COLUMN relations.sys_role_permission.role_id IS '角色ID，关联角色表';
COMMENT ON COLUMN relations.sys_role_permission.permission_id IS '权限ID，关联权限表';
