-- 统一完整权限码并初始化管理后台菜单；保留既有权限 ID 与角色关联，不创建账号或默认授权。
UPDATE relations.sys_permission SET code = 'pms:' || code WHERE code LIKE 'system:%';

ALTER TABLE relations.sys_menu ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN relations.sys_menu.hidden IS '是否隐藏侧边栏入口，隐藏页面仍可按菜单授权注册路由';

INSERT INTO relations.sys_menu(name,path,component,icon,sort,type)
SELECT '首页','/home/index','/home/index','HomeFilled',0,'PAGE'
WHERE NOT EXISTS (SELECT 1 FROM relations.sys_menu WHERE path='/home/index');
INSERT INTO relations.sys_menu(name,path,component,icon,sort,type)
SELECT '关于项目','/about/index','/about/index','InfoFilled',99,'PAGE'
WHERE NOT EXISTS (SELECT 1 FROM relations.sys_menu WHERE path='/about/index');
INSERT INTO relations.sys_menu(name,path,component,icon,sort,type)
SELECT '系统管理','/system','Layout','Setting',10,'DIRECTORY'
WHERE NOT EXISTS (SELECT 1 FROM relations.sys_menu WHERE path='/system');

INSERT INTO relations.sys_menu(parent_id,name,path,component,icon,sort,type)
SELECT MIN(id),'用户管理','/system/user/index','/system/user/index','User',1,'PAGE'
FROM relations.sys_menu WHERE path='/system'
HAVING NOT EXISTS (SELECT 1 FROM relations.sys_menu WHERE path='/system/user/index');
INSERT INTO relations.sys_menu(parent_id,name,path,component,icon,sort,type)
SELECT MIN(id),'角色管理','/system/role/index','/system/role/index','UserFilled',2,'PAGE'
FROM relations.sys_menu WHERE path='/system'
HAVING NOT EXISTS (SELECT 1 FROM relations.sys_menu WHERE path='/system/role/index');
INSERT INTO relations.sys_menu(parent_id,name,path,component,icon,sort,type)
SELECT MIN(id),'菜单管理','/system/menu/index','/system/menu/index','Menu',3,'PAGE'
FROM relations.sys_menu WHERE path='/system'
HAVING NOT EXISTS (SELECT 1 FROM relations.sys_menu WHERE path='/system/menu/index');

INSERT INTO relations.sys_menu_button(menu_id,permission_id,name,sort)
SELECT (SELECT MIN(id) FROM relations.sys_menu WHERE path='/system/user/index'),p.id,p.name,p.id
FROM relations.sys_permission p WHERE p.code LIKE 'pms:system:user:%'
AND NOT EXISTS (SELECT 1 FROM relations.sys_menu_button b WHERE b.permission_id=p.id);
INSERT INTO relations.sys_menu_button(menu_id,permission_id,name,sort)
SELECT (SELECT MIN(id) FROM relations.sys_menu WHERE path='/system/role/index'),p.id,p.name,p.id
FROM relations.sys_permission p WHERE p.code LIKE 'pms:system:role:%'
AND NOT EXISTS (SELECT 1 FROM relations.sys_menu_button b WHERE b.permission_id=p.id);
INSERT INTO relations.sys_menu_button(menu_id,permission_id,name,sort)
SELECT (SELECT MIN(id) FROM relations.sys_menu WHERE path='/system/menu/index'),p.id,p.name,p.id
FROM relations.sys_permission p WHERE p.code LIKE 'pms:system:menu:%'
AND NOT EXISTS (SELECT 1 FROM relations.sys_menu_button b WHERE b.permission_id=p.id);
