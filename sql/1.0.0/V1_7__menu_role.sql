-- 菜单与角色关联表，由 system-service 管理；删除角色时自动删除其菜单关联。
CREATE TABLE relations.sys_menu_role (
    role_id INTEGER NOT NULL REFERENCES relations.sys_role(id) ON DELETE CASCADE,
    menu_id INTEGER NOT NULL REFERENCES relations.sys_menu(id),
    PRIMARY KEY (role_id, menu_id)
);
CREATE INDEX idx_menu_role_menu ON relations.sys_menu_role(menu_id);

COMMENT ON TABLE relations.sys_menu_role IS '菜单与角色关联表';
COMMENT ON COLUMN relations.sys_menu_role.role_id IS '角色ID，关联角色表';
COMMENT ON COLUMN relations.sys_menu_role.menu_id IS '菜单ID，关联菜单表';
