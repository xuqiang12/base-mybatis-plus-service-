-- 初始操作权限，由 system-service 管理；依赖权限表，无默认用户、密码或授权关系。
INSERT INTO relations.sys_permission(code, name) VALUES
('system:user:list', '查询用户'), ('system:user:detail', '用户详情'),
('system:user:add', '新增用户'), ('system:user:edit', '修改用户'), ('system:user:delete', '删除用户'),
('system:role:list', '查询角色'), ('system:role:add', '新增角色'),
('system:role:edit', '修改角色'), ('system:role:delete', '删除角色'), ('system:role:permission', '角色授权'),
('system:menu:list', '查询菜单'), ('system:menu:add', '新增菜单'),
('system:menu:edit', '修改菜单'), ('system:menu:delete', '删除菜单'),
('system:permission:list', '查询权限'), ('system:permission:add', '新增权限'),
('system:permission:edit', '修改权限'), ('system:permission:delete', '删除权限');
