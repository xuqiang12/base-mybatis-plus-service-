# Java 基础搭建实施计划

> 本文为拆分前的历史实施记录。当前采用四模块、两个运行服务，见 [统一账号多服务设计](统一账号多服务设计.md) 和 [多服务拆分实施计划](多服务拆分实施计划.md)。

## 目标和已确认范围

在当前仓库使用 Java 实现原 NestJS 项目的公共底层、登录、用户、角色、菜单和操作权限。保留现有两模块和 `com.base` 包名前缀，一个 Spring Boot 服务启动。不修改 Node 项目，不迁移已有数据，不提交或推送 Git。

## 技术和接口约定

- Java 21、Spring Boot 3.5.16、MyBatis-Plus 3.5.17、PostgreSQL。
- 使用独立 `relations` schema；Flyway 维护版本化建表脚本。
- GET 查询，POST 新增、修改、删除和授权；Java 用户接口采用 `/user/create`、`/user/update`、`/user/delete`，不兼容旧 Node 的 PATCH/DELETE 路由。
- 成功为 `{code:0,message:"success",data:...}`；分页数据为 `{list,total,pageNum,pageSize}`。
- 参数、认证、权限、资源不存在、系统异常分别返回 HTTP 400/401/403/404/500，同时返回统一 JSON。与旧 Node 的部分错误返回 HTTP 200 不同。
- 时间统一使用北京时间 `yyyy-MM-dd HH:mm:ss`。错误响应不包含内部异常、SQL、密码或 Token。
- Spring Security 校验 JWT，BCrypt 存储密码；JWT 密钥来自环境变量。每次请求读取当前用户和权限，删除用户、修改授权立即生效。
- 权限码沿用 `system:user:list` 等形式；菜单可见性和操作权限分别授权。
- 首个管理员只在数据库没有用户且显式提供初始化配置时建立，无默认账号密码。公共注册只创建普通用户，不能自行指定管理员或角色。

## 文件职责

- `base-common/.../result/ApiResponse.java`：统一返回，扩展现有类。
- `base-common/.../result/PageResult.java`：分页结果。
- `base-common/.../exception/BusinessException.java`：受控业务错误。
- `relations-service/.../RelationsApplication.java`：补齐现有空启动类。
- `base-common/.../com/base/common/web`：响应包装、异常和请求日志。
- `base-common/.../com/base/common/config`：分页、时间、接口文档配置。
- `base-common/.../com/base/common/security`：JWT、当前用户、认证与权限错误。
- `base-common/.../com/base/common/modules/{auth,user,role,menu,permission}`：基础管理 Controller、Service、Mapper、Entity、DTO。
- `base-common/src/main/resources/db/migration`：独立表和基础权限码。
- `relations-service/src/main/resources/application.yml`：当前服务的数据库、JWT、管理员初始化和端口等运行配置。
- `relations-service/src/test`：真实 HTTP、认证、授权、数据库和事务测试。

## 顺序和验收

- [x] 公共请求链：先写返回、时间、非法参数、错误响应测试，确认失败，再实现；测试不得重复包装响应，异常不得泄露内部信息。
- [x] 数据库和登录：创建版本化迁移与实体；实现 JWT、BCrypt、显式管理员初始化；错误密码、伪造 Token、删除用户后旧 Token 均不能访问。
- [x] 用户、角色和授权：实现分页与软删除、用户角色关联、角色菜单和权限关联；校验无权访问、授权实时生效、失败时整体回滚。
- [x] 菜单和按钮：实现列表、树、CRUD 和权限绑定；防止循环父子关系，阻止删除仍被使用的菜单和角色。
- [x] 文档和验证：补齐配置示例、接口表和学习入口；运行 Maven verify 和打包验证，并进行只读代码审查。

## 验证命令和边界

PowerShell 当前终端使用 `D:\DevTools\jdk-21.0.12.1+1`，从仓库根目录执行 `mvn.cmd -s .mvn/settings.xml clean verify`。

本机 Docker 引擎未运行。自动化集成测试先使用隔离的 H2 PostgreSQL 兼容模式，执行真实迁移、SQL 和 HTTP，不连接已有 Node 数据库。若没有可用的独立 PostgreSQL 实例，最终明确保留 PostgreSQL 实机验证边界。

最终 `mvn.cmd -s .mvn/settings.xml clean verify` 成功，21 项测试全部通过。只读审查发现的权限身份混淆、删除按钮撤销独立授权和并发删除最后管理员问题，以及自查复现的并发菜单循环，均已补回归测试并修复。

已检查可执行 JAR 包含公共模块依赖，公共 JAR 包含基础源码和 Flyway 脚本；业务 JAR 不再残留迁移前的基础模块类。只读复查通过，`git diff --check` 通过。测试使用 H2，真实 PostgreSQL 尚未验证。配置和接口说明见 [基础规范与运行说明](基础规范与运行说明.md)。
