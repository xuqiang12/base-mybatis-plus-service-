# 独立 database-seed 模块

目标：用户已确认的独立 Maven 初始化模块，JSON 与执行代码集中归属；运行 SeedApplication 初始化后退出，SystemApplication 不触发任何账号或系统 Seed。

边界：沿用当前工作区权限及登录实现。只迁移已实现的系统 Seed，不增加首页装修、底部导航、AI 或账本导入。不连接真实数据库。不提交或更改其他会话的工作。

- [x] 添加 SystemApplication 携带旧 --seed 和管理员配置仍不初始化的隔离 H2 测试，确认现有代码失败。
- [x] 新建 database-seed Maven 模块、无 Web 入口和配置；通过 system-service 普通 JAR 复用 SQL 与 Java Flyway 迁移，不扫描业务组件，不运行 SystemApplication。
- [x] 搬迁系统 Seed 及管理员配置，拆分 permissions/menus/buttons/roles JSON，保留 system-base/1 与 admin-account/1 历史及事务语义。管理员创建使用 JDBC，保留家庭、成员与默认现金账户初始化。
- [x] 移除业务模块 Seed、旧启动管理员 Runner 及 Seed 配置，保留业务账号创建功能；移除失去调用方的旧 bootstrap 方法及对应旧入口测试，管理员初始化测试统一归属 Seed。
- [x] 迁移原 Seed 集成测试至新模块，使用真实 Flyway 和独立 H2，验证默认数据、账号冲突回滚、重复执行、无 Web/业务/JWT 依赖及 main 执行后退出。
- [x] 增加 IDEA SeedApplication-local 配置，复用现有忽略的本机连接文件；更新文档和终端脚本入口，不重复写真实凭证。
- [x] 使用本机 JDK21 执行 clean verify，检查打包后只有 database-seed 包含初始化代码/JSON；H2 命令行执行首次与重复验证，无真实库写入。

验收：默认数据保持 18 权限、6 菜单、14 按钮、1 角色及管理员关联；普通服务不创建用户/Seed 历史；重复执行保留人工修改；启动命令无 --seed；独立程序成功退出；不监听端口。

## 验证结果

- 首次红灯：旧业务启动 `--seed` 创建用户，SystemStartupTest 预期 0、实际 1，验证回归测试有效。
- 最终 JDK 21 Maven verify：base-common 4、system-service 150、database-seed 6，共 160 项通过，无失败、错误或跳过。
- 原工作区的旧运行包被现有 Java 进程占用。未停止进程或覆盖旧后端包；在临时、排除 application-local.yml 的源码副本 clean verify 后，清理旧初始化方法，再次 verify 全部通过。最终构建输入与当前源码一致。
- 检查 JAR：system-service 无 Seed 类、JSON 或 BootstrapAdmin；database-seed 包含独立入口与 4 份 JSON，没有本机配置。已将新 Seed 的两个 JAR 放回该模块 target，旧业务运行包未改。
- H2 文件库的独立进程验证：通过打包的 Spring Boot PropertiesLauncher 加载测试 H2 驱动和约束回调，首次执行 2 个版本、第二次无管理员密码执行 0 个版本，均关闭连接池并以 0 退出，没有 HTTP 监听。
- IDEA 运行项 XML、本机配置路径和配置字段存在性校验通过；PowerShell 入口语法及 git diff --check 通过。
- 未使用本机私有配置运行程序，未连接 Neon 或阿里云；没有导入真实用户/账本，也没有提交 Git。
