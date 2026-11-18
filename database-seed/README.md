# database-seed

独立运行的数据库初始化程序。JSON 默认数据、读取和写入代码、首次管理员配置及测试集中在本模块；执行完成后自动退出，不提供 HTTP 接口。

## IDEA 使用

1. Maven 面板重新加载项目。
2. 顶部运行配置选择 **SeedApplication-local**，点击运行。
3. 控制台显示“数据库初始化完成”，进程退出。

现有运行项复用 `system-service/src/main/resources/application-local.yml` 中的数据库和 `app.seed-admin`，不需要 JWT 密钥或邮箱配置。新环境可使用本模块的 `application-local.example.yml`，复制为被忽略的本机文件，再调整运行配置的文件路径。

日常业务运行 **SystemApplication-local**，不会执行本模块。

## 默认数据

`src/main/resources/seeds/system-service/` 按服务归属维护：

| 文件 | 内容 |
| --- | --- |
| `permissions.json` | 18 个操作权限 |
| `menus.json` | 6 个目录和页面菜单，父子关系按路径引用 |
| `buttons.json` | 14 个页面与操作权限关联 |
| `roles.json` | 内置角色及默认菜单、权限关联，使用路径和权限码引用 |

首次管理员的用户名、邮箱和密码通过本机配置或环境变量提供，真实凭证不放入 JSON。创建管理员时生成初始家庭、成员和现金账户。

`SystemSeedService` 在同一事务中写入默认数据和 `relations.sys_seed_history`。已有 `system-base/1`、`admin-account/1` 历史继续复用；重复执行直接跳过，不恢复人工删除的授权、不覆盖修改后的密码和菜单。JSON 是首次默认值，修改它不会更新已完成的批次。

运营配置位于同目录的 `cms/`，由 `CmsSeedService` 维护首页方案及组件、页面绑定、默认底部导航，以及运营菜单/权限/按钮。独立记录 `cms-home/1`、`cms-tabbar/1`、`cms-management/1`，已存在内容保留，重复跳过，不为已有角色增加授权。详见 [运营配置与初始化](../docs/运营配置与初始化.md)。

## 运行与依赖

入口为 `com.base.seed.SeedApplication`。只扫描 `com.base.seed`，无 Web 服务，运行结束关闭连接池。依赖 `system-service` 的普通 JAR 复用 Flyway SQL 与 Java 迁移，避免另一套表结构；不会运行 `SystemApplication`。

JDK 21 构建后，也可在项目根目录使用 `./database-seed/seed.ps1`，或运行 `database-seed/target/database-seed-0.0.1-SNAPSHOT-exec.jar`。没有 `--seed` 开关，启动本程序即初始化配置所指数据库。

完整配置、已有数据处理及后续导入边界见 [系统初始化与新库使用](../docs/系统初始化与新库使用.md)。
