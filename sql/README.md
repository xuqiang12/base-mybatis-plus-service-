# 数据库脚本说明

所有手写建表、改表和初始化 SQL 集中在这里，按发布版本分目录，不再按服务分目录。初始建表按表拆分，文件名使用简短英文，文件内容使用中文说明和数据库注释。

```text
sql/
├── README.md
└── 1.0.0/
    ├── V1_1__user.sql             用户账号表
    ├── V1_2__role.sql             角色表
    ├── V1_3__permission.sql       操作权限表
    ├── V1_4__menu.sql             菜单表
    ├── V1_5__user_role.sql        用户与角色关联表
    ├── V1_6__role_permission.sql  角色与权限关联表
    ├── V1_7__menu_role.sql        菜单与角色关联表
    ├── V1_8__menu_button.sql      菜单按钮表
    └── V1_9__seed.sql             初始权限数据
```

| 脚本 | 负责服务 | 用途 |
| --- | --- | --- |
| [V1_1__user.sql](1.0.0/V1_1__user.sql) | system-service | 建立 relations schema 和用户账号表，包含登录信息、超级管理员标记及软删除信息 |
| [V1_2__role.sql](1.0.0/V1_2__role.sql) | system-service | 建立角色表 |
| [V1_3__permission.sql](1.0.0/V1_3__permission.sql) | system-service | 建立操作权限表，权限编码用于接口鉴权 |
| [V1_4__menu.sql](1.0.0/V1_4__menu.sql) | system-service | 建立目录和页面菜单表及父菜单索引 |
| [V1_5__user_role.sql](1.0.0/V1_5__user_role.sql) | system-service | 建立用户与角色关联表及角色索引 |
| [V1_6__role_permission.sql](1.0.0/V1_6__role_permission.sql) | system-service | 建立角色与权限关联表及权限索引 |
| [V1_7__menu_role.sql](1.0.0/V1_7__menu_role.sql) | system-service | 建立菜单与角色关联表及菜单索引 |
| [V1_8__menu_button.sql](1.0.0/V1_8__menu_button.sql) | system-service | 建立菜单按钮表及菜单索引 |
| [V1_9__seed.sql](1.0.0/V1_9__seed.sql) | system-service | 初始化 18 个基础权限，不创建默认账号、密码或授权关系 |

每张表的文件包含建表语句、相关索引、中文表注释和全部字段注释。PostgreSQL 的 `COMMENT ON TABLE/COLUMN` 会保存到数据库，数据库工具刷新后可以查看。

## 后续文件怎么放

下一次发布时新增 `1.0.1/`，将该版本的相关 SQL 放在一起。新文件使用简短英文名，例如 `V2__user_status.sql`；文件第一行注释写清具体改动。这是命名示例，当前没有新增业务或 V2 脚本。

目录版本表示发布批次；文件的 `V1_1__` 到 `V1_9__` 表示 Flyway 版本 1.1 到 1.9，按版本号顺序执行，两者独立。Flyway 使用两个下划线分隔版本号和说明，同一服务、同一迁移历史内不能重复版本号。下一次迁移可以使用 `V2__`，它排在 1.9 之后。

已执行的脚本不改文件名和内容，后续变更新增脚本。同一次尚未执行的更新可以包含多个字段改动，不需要每增加一个字段就新增文件。

2026-10-14 拆分前只读核对了本机配置指向的数据库：没有 relations schema、应用表或 Flyway 历史，因此将原单文件 V1 拆成上述 9 个初始脚本。没有在实际数据库执行迁移。这组脚本用于首次初始化；其他环境若已执行原 `V1__identity_foundation.sql`，应保留其原迁移历史并另行编写增量，不能直接替换为这组初始脚本，也不要清空历史来绕过校验。

## 如何执行

每份 SQL 在所属服务的 `pom.xml` 中登记。system 的资源配置将当前 9 个初始脚本从这个目录原样复制到运行包的 `db/system/1.0.0/`，Flyway 按已有 `classpath:db/system` 配置递归发现并执行：先建基础表，再建关联表，最后初始化权限。Flyway 的 `relations.flyway_schema_history` 记录已执行版本，后续启动会校验并跳过它们。

以后新增 system 脚本时，在 `system-service/pom.xml` 的数据库资源 `includes` 中加入对应路径。其他服务的脚本由各自的资源配置选择，不能把整个目录的所有 SQL 无差别打进每个服务。

服务源码目录不再维护另一份 SQL。`target/` 和 JAR 中的 SQL 是自动生成的打包副本，请修改本目录中的原文件。

relations 当前没有业务数据库和迁移脚本，后续有实际表需求时再接入。当前建表仍由 system 启动时自动执行；不需要手动逐个执行这些 SQL。`FlywayMigrationTest` 使用隔离 H2 数据库验证 9 个脚本的执行顺序、8 张表和 37 个字段的注释、18 个初始权限以及重复迁移不重新执行；身份和跨服务测试继续验证业务行为。

[返回项目说明](../README.md)
