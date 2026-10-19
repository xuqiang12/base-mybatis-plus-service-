# base-mybatis-plus-service：按业务模块组织的单服务基础工程

当前有三个 Maven 模块，其中两个是公共类库、一个是独立运行的 Spring Boot 服务。只需启动 `SystemApplication`，默认端口 8080。

| 模块 | 独立启动 | 职责 |
| --- | --- | --- |
| [base-common](base-common/README.md) | 否 | 统一返回、分页结构、校验、异常、时间和请求日志 |
| [base-security](base-security/README.md) | 否 | 公钥校验 Token、当前身份、接口权限检查、统一安全错误 |
| [system-service](system-service/README.md) | 是，默认 8080 | 登录、用户、角色、菜单、权限及后续亲友业务 |

职责速记：**common 管通用请求处理，security 管身份与权限检查，system 管账号和权限数据。** 三个模块各自目录里的 README 都有简短说明，可以随时查阅。

## 阅读导航

- [目录与依赖](#目录与依赖)：哪些是类库，哪些是运行服务。
- [数据库脚本](sql/README.md)：按发布版本集中查阅建表和改表 SQL。
- [礼账后端接口与数据库规划](docs/Java后端接口与数据库规划.md)：小程序业务、拟新增接口和表、开发顺序及待确认事项，尚未实施。
- [技术和构建](#技术和构建)、[配置和启动](#配置和启动)：怎么构建、运行。
- [登录与授权](#登录与授权)：单应用如何校验身份和权限。
- [邮箱注册与密码登录](docs/Java学习/04_邮箱注册与密码登录.md)：SMTP 配置、验证码注册、邮箱/手机号密码登录及绑定流程。
- [为什么比 Node 项目多一些文件](#为什么比-node-项目多一些文件)：对应关系和额外设计。
- [先认识文件类型](#先认识文件类型)、[逐个文件的用途](#逐个文件的用途)：按模块查文件。
- [沿一个请求读代码](#沿一个请求读代码)：建议的阅读顺序。

## 目录与依赖

```text
base-mybatis-plus-service/
├── pom.xml                          父工程，管理三个模块和依赖版本
├── .mvn/settings.xml                项目 Maven 仓库配置
├── sql/                     集中维护 SQL，按发布版本分目录
│   ├── README.md                    脚本用途、命名和执行说明
│   └── 1.0.0/                      首次初始化，按表拆分
│       ├── V1_1__user.sql           用户账号
│       ├── V1_2__role.sql           角色
│       ├── V1_3__permission.sql     操作权限
│       ├── V1_4__menu.sql           菜单
│       ├── V1_5__user_role.sql      用户与角色关联
│       ├── V1_6__role_permission.sql 角色与权限关联
│       ├── V1_7__menu_role.sql      菜单与角色关联
│       ├── V1_8__menu_button.sql    菜单按钮
│       └── V1_9__seed.sql           初始权限数据
├── base-common/
│   └── src/
│       ├── main/java/com/base/common/
│       │   ├── BaseCommonConfiguration.java
│       │   ├── config/JacksonConfig.java
│       │   ├── dto/IdRequest.java
│       │   ├── exception/BusinessException.java
│       │   ├── result/               ApiResponse、PageResult
│       │   └── web/                  包装、异常、日志、健康检查
│       └── test/java/com/base/foundation/FoundationHttpTest.java
├── base-security/
│   └── src/main/java/com/base/security/
│       ├── BaseSecurityConfiguration.java
│       ├── SecurityConfig.java       Token 校验和权限配置
│       ├── CurrentUserProvider.java  当前身份提供接口
│       ├── LoginUser.java            共享身份结构，不携带密码
│       ├── JwtVerificationProperties.java
│       ├── PublicEndpointProperties.java
│       ├── OpenApiConfig.java
│       └── SecurityErrorHandler.java、SecurityExceptionHandler.java
├── system-service/
│   └── src/
│       ├── main/
│       │   ├── java/com/base/system/
│       │   │   ├── SystemApplication.java
│       │   │   ├── config/           MyBatis 和数据库错误处理
│       │   │   ├── security/         私钥签发、BCrypt、管理员初始化
│       │   │   └── modules/          auth、user、role、menu、permission
│       │   └── resources/
│       │       ├── application.yml
│       │       └── application-local.example.yml
│       └── test/                     身份管理与单服务 HTTP 集成测试
└── docs/                             设计、运行说明和学习记录
```

依赖方向：`system-service → base-security → base-common`。只有 system 生成可执行运行包；公共模块通过显式 `@Import` 加载。

`base-common` 负责通用请求处理，不强制加载数据库或认证。`base-security` 通过 `CurrentUserProvider` 调用应用内的 `AccessService`，读取当前身份和权限，不再发起服务间 HTTP 查询。

功能保持按业务组织，每组包含 Controller、Service、DTO、Entity 和 Mapper。亲友等业务后续放在 `com.base.system.modules.relations` 下，复用同一应用的数据库与认证配置；目前尚未创建业务代码。

## 技术和构建

JDK 21、Maven 3.9.x、Spring Boot 3.5.16、MyBatis-Plus 3.5.17。应用使用 PostgreSQL 和 Flyway。

本机终端配置并构建：

```powershell
Set-Location 'F:\公司项目\gitee\base-mybatis-plus-service'
$env:JAVA_HOME = 'D:\DevTools\jdk-21.0.12.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn.cmd -s .mvn/settings.xml clean verify
```

自动化验证使用 H2 隔离数据库和随机端口，不需要实际 PostgreSQL。覆盖公共模块独立启动、原管理功能、单应用内的授权变更和用户删除后 Token 失效。

本机 `application-local.yml` 被 Git 忽略并从 Maven 资源打包中排除；需要使用时通过 `spring.config.additional-location` 显式加载，配置例子见运行说明。

## 配置和启动

先按 [基础规范与运行说明](docs/基础规范与运行说明.md) 准备数据库、本机 RSA 密钥和管理员配置。数据库、公钥和私钥都配置在 system。

从项目根目录运行一个应用：

```powershell
# 已通过环境变量提供数据库和密钥配置
& "$env:JAVA_HOME\bin\java.exe" -jar .\system-service\target\system-service-0.0.1-SNAPSHOT-exec.jar
```

`-exec.jar` 是可执行包；无 `-exec` 的普通 JAR 用于模块依赖，不能用 `java -jar` 启动。IDEA 只运行 `SystemApplication-local`，该运行项会显式加载现有本机配置，详见 [IDEA配置与运行流程](docs/IDEA配置与运行流程.md)。

健康检查：

```powershell
Invoke-RestMethod 'http://localhost:8080/health'
```

返回 `code: 0`、`status: "UP"` 和 `service: "system-service"`。健康接口仅确认 HTTP 响应，数据库迁移和登录需结合日志与实际接口检查。

## 登录与授权

前端向同一个应用登录、获取当前用户与菜单并调用业务接口，认证请求携带 Bearer Token。应用校验签名、有效期、issuer，再由本地 `AccessService` 查询当前身份和授权。菜单显示与后端接口权限分别控制。

每次从本地数据库查询最新授权，不缓存；撤权、删除用户对已有 Token 立即生效。公开业务接口通过 `security.public-endpoints.get/post` 显式配置。

`/auth/login`、`/auth/userInfo` 和用户角色菜单权限路径保留，默认使用 8080 端口。多服务专用的 `GET /auth/access` 已移除。签名算法保留 RS256，本次合并不更换密钥或 issuer。

邮箱注册使用 `POST /auth/email/code` 发码、`POST /auth/email/register` 提交昵称/邮箱/验证码/密码；昵称 `nickname` 必填，去除首尾空白后最多 100 个字符，允许重复，通过 `/auth/userInfo` 返回。注册成功后使用 `/auth/login` 以邮箱或手机号加密码登录。登录后可调用 `/user/bindPhone` 填写手机号绑定，暂不发短信。旧 `/user/register` 仅允许超级管理员调用；原验证码直接登录入口已移除。V2.2 迁移保留 V2.1 历史，邮箱标记更正为 `email_verified`；V2.3 增加昵称字段，历史账号昵称允许为空。配置及文件职责见 [邮箱注册与密码登录](docs/Java学习/04_邮箱注册与密码登录.md)。

数据库表和 Flyway 历史使用 `relations` schema。首次初始化脚本集中在根目录 `sql/1.0.0/`，按 8 张表拆分，另有 1 个初始权限数据脚本；每张表和全部字段都有中文数据库注释。拆分前已只读确认本机配置指向的数据库尚未初始化，实际数据库未执行迁移。system 的打包配置将这 9 个脚本放到运行包的 `db/system/1.0.0/`，Flyway 从 `classpath:db/system` 递归加载，按 1.1 到 1.9 执行。已执行的迁移不修改，后续脚本按发布版本归档，并在所属服务的资源配置中登记；其他环境若已执行原单文件 V1，不能直接使用这组初始脚本替换历史，详见 [数据库脚本说明](sql/README.md)。

## 当前范围

当前采用单服务、内部按业务模块划分。亲友资料、借还款、微信登录、前端管理页面、菜单应用归属和更多业务权限留待实际业务开发。未引入网关、注册中心或缓存。

真实 PostgreSQL 实机运行尚未验证；测试成功不代表该数据库或生产部署已经启动成功。此工程不依赖公司的 PMS 或现有 Node 项目。

## 为什么比 Node 项目多一些文件

多数功能在原来的 `nestjs-prisma` 项目里也有，只是名字和组织方式不同。当前文件更多，主要来自 MyBatis 的数据库写法、Spring 的组件配置，以及公共基础与安全代码的模块划分；并不是 Java 要求每个功能都拆成很多文件。

| 当前 Java 文件或机制 | 原 Node 项目中的对应位置 | 为什么看起来不同 |
| --- | --- | --- |
| Controller、Service | `*.controller.ts`、`*.service.ts` | 都是接口入口和业务逻辑，职责基本一致 |
| Request DTO | `dto/*.ts`、class-validator | 都定义允许传入的字段，并校验参数 |
| Entity | `prisma/schema.prisma` 和 Prisma 生成类型 | Prisma 集中描述表并生成类型；当前 MyBatis 使用 Java 实体类映射表 |
| Mapper | `prisma.user.findMany()` 等 Prisma 自动提供的方法 | 当前每张主要业务表有一个 Mapper，继承 BaseMapper 获得基础增删改查 |
| ApiResponse、ResponseAdvice、JacksonConfig | `src/common/interceptors/response.interceptor.ts` | 原拦截器同时包装响应、转换时间；这里分开定义返回结构、执行包装、设置序列化 |
| PageResult | `src/common/utils/pagination.ts` 中的 `formatPage` | 都定义分页结果 |
| 异常处理器 | `src/common/filters/all-exception.filter.ts`，以及 main.ts 注册的 PrismaClientExceptionFilter | 都把异常转成响应；当前按通用、安全、数据库职责分布到不同模块 |
| SecurityConfig、方法权限检查 | JWT Guard、PermissionsGuard、RolesGuard，及 app.module.ts 中的注册 | 都处理身份认证和接口权限 |
| `*Properties` 配置类 | `common/configs/config.ts`、`config.interface.ts`、ConfigService、`.env` | Java 将一组配置绑定为带类型的对象；Node 同样有配置读取和类型定义 |
| MybatisConfig、Flyway SQL | PrismaService、schema、migrations | ORM 和迁移工具不同，目标都是访问数据库、维护表结构 |
| BootstrapAdminRunner | Prisma 用户 seed | 都能初始化账户；当前选择按显式配置在启动时初始化首位管理员 |

Node 对照用于理解职责，不代表两个项目实现完全相同，也不表示本工程运行时依赖 Node 项目。

### 哪些是这次设计增加的

| 设计或规则 | 为什么增加 | 是否 Java 必须 |
| --- | --- | --- |
| CurrentUserProvider | 让安全类库通过统一接口调用应用内的 AccessService 查数据库 | 否，用于隔离安全类库与具体业务数据访问 |
| 每次认证读取最新授权 | 让撤权、删除用户对已有 Token 立即生效 | 否，是当前选择；会增加查询开销，认证依赖本地数据库可用 |
| RSA 公私钥分离、签发与验签配置分开 | 同一应用使用私钥签发、公钥验签 | 否，是当前认证方案 |
| 独立 permission 管理模块 | 提供权限字典的独立增删改查接口 | 否，是功能范围；原 Node 已有权限模型、seed 和按钮绑定，但未发现同样独立的管理模块 |
| 最后一位管理员保护、基础权限码保护 | 避免管理能力被误删或全部撤销 | 否，是具体业务规则 |
| 启动时初始化首位管理员 | 方便首次部署创建管理入口 | 否，也可以采用手动 seed |
| 各类设计、实施和学习文档 | 记录决策、操作流程和历史问题 | 否，不参与程序运行 |

原 Node 的 JWT Guard 校验后把 Token payload 放到 `request.user`，权限 Guard 使用 Token 内的权限。`AuthService.userInfo()` 会查询数据库，但普通业务请求并不会因此全部读取最新授权。当前 Java 的本地 AccessService，才是“每次认证取最新授权”的来源。

### 哪些分开是有实际用途的

- Requests 定义前端能传什么，Entity 定义数据库存什么，Response 定义允许返回什么；例如返回用户时排除密码散列。
- ApiResponse 定义 JSON 结构，ResponseAdvice 执行自动包装，二者职责不同。
- SecurityErrorHandler 处理 Spring 安全过滤器阶段的错误；SecurityExceptionHandler 处理 MVC 方法权限检查阶段的错误。
- DatabaseExceptionHandler 放在 system，避免公共基础为了处理数据库错误而引入数据库依赖。

目前按业务把用户、角色、菜单、权限各自放在同一目录里，基础功能集中在 common、安全功能集中在 security，不需要继续按每个小动作拆文件。`UserRequests` 等已经把同一业务的多个请求类型合在一个文件中。Entity、Mapper 也来自当前 MyBatis 用法，换用其他 ORM 时文件形态可能不同。

## 先认识文件类型

| 名称 | 通俗解释 | 当前例子 |
| --- | --- | --- |
| Controller | 接收接口请求，调用业务逻辑 | `UserController` |
| Service | 实现业务规则，例如检查角色、处理密码 | `UserService` |
| Mapper | 向数据库发起查询、新增、修改、删除 | `UserMapper` |
| Entity | Java 中表示数据库表字段的对象 | `UserEntity` |
| Request DTO | 定义客户端能传哪些字段、字段是否合法 | `UserRequests` |
| Response DTO | 定义返回给客户端哪些字段 | `UserResponse` |
| Config / Configuration | 注册和设置框架组件 | `SecurityConfig` |
| Filter / Advice / Handler | 处理多个接口共用的日志、响应、错误 | `RequestLogFilter`、`ResponseAdvice` |

当前基础 Mapper 继承 MyBatis-Plus 的 `BaseMapper`，可直接使用 `selectById`、`insert` 等方法。auth 的自定义 SQL 放在 `resources/mapper/auth/` 下的 XML 中；业务接口保留在 auth 根包，具体实现统一放在 `auth/impl/`。

## 逐个文件的用途

### 工程与运行配置

以下路径相对于项目根目录。

| 文件 | 用途 |
| --- | --- |
| `pom.xml` | 父工程，登记三个模块，统一 Java 21、Spring Boot 和 MyBatis-Plus 版本；自身不启动服务 |
| `base-common/pom.xml` | Web、参数校验、Lombok、Swagger 和基础测试依赖，不含数据库、安全 starter |
| `base-security/pom.xml` | 引用 common，加入 Spring Security 和 JWT 公钥校验 |
| `system-service/pom.xml` | 引用 security，加入 MyBatis、PostgreSQL、Flyway、H2 测试及打包；登记根目录中归本服务执行的 SQL |
| `.mvn/settings.xml` | 项目专用 Maven 仓库设置；使用默认公共仓库，避免沿用其他项目的私服设置；使用时须在 IDEA 或命令中明确指定 |
| `.gitignore` | 告诉 Git 忽略编译产物、IDE 文件、日志和本机私有配置 |
| `system-service/src/main/java/com/base/system/SystemApplication.java` | 统一管理服务启动入口，扫描自身管理代码并导入安全模块 |
| `system-service/src/main/resources/application.yml` | 8080、数据库、Flyway、公私钥、管理员、文档与 MyBatis 配置 |
| `system-service/src/main/resources/application-local.example.yml` | 管理服务本机模板：数据库、密钥、管理员配置 |
| `system-service/src/test/resources/application-test.yml` | H2 内存数据库和测试 RSA 密钥路径，不是生产配置 |

### 公共基础代码

以下表格中的路径均位于 `base-common/src/main/java/com/base/common/` 下。

| 文件 | 用途 |
| --- | --- |
| `BaseCommonConfiguration.java` | 公共模块加载入口，扫描 `com.base.common` 下的组件 |
| `result/ApiResponse.java` | 定义统一 JSON 结构 `code/message/data`；成功消息为 `success`，失败时 data 为 null |
| `result/PageResult.java` | 定义分页数据结构 `list/total/pageNum/pageSize`，外层仍由统一响应包装 |
| `dto/IdRequest.java` | 删除等操作共用的请求对象，要求 id 非空且为正数 |
| `exception/BusinessException.java` | 表示可告知用户的业务错误，例如“用户不存在”，包含 HTTP 状态码和说明 |
| `config/JacksonConfig.java` | 设置 JSON 时间输出格式；Instant 按北京时间转换，LocalDateTime 使用统一格式 |
| `web/HealthController.java` | 提供 `GET /health`，返回服务名和 `UP`；只确认 HTTP 响应能力 |
| `web/ResponseAdvice.java` | 自动为 `com.base.*` 下 Controller 的普通返回套上 ApiResponse，处理字符串返回，避免重复包装 |
| `web/GlobalExceptionHandler.java` | 通用业务、参数和未知异常；数据库及权限异常由所属模块处理 |
| `web/RequestLogFilter.java` | 记录每个请求的方法、路径、状态和耗时；不记录正文、密码和 Token |

### 公共安全模块

以下文件位于 `base-security/src/main/java/com/base/security/`。

| 文件 | 用途 |
| --- | --- |
| `BaseSecurityConfiguration.java` | 安全模块入口，只扫描本模块并导入 common |
| `SecurityConfig.java` | 公钥校验 JWT；通过身份提供接口取得当前授权，启用方法权限 |
| `CurrentUserProvider.java` | 当前身份提供接口，由应用内 AccessService 查询本地数据库 |
| `JwtVerificationProperties.java` | issuer 和公钥文件，不包含私钥 |
| `PublicEndpointProperties.java` | 服务明确指定匿名 GET/POST 业务路径 |
| `LoginUser.java` | 共享身份、角色和权限结构，无密码 |
| `SecurityErrorHandler.java` | 过滤器返回统一 401/403 |
| `SecurityExceptionHandler.java` | 方法权限失败返回统一 403 |
| `OpenApiConfig.java` | Swagger Bearer Token 认证配置 |

### 统一管理服务的登录与初始化

以下文件位于 `system-service/src/main/java/com/base/system/`。

| 文件 | 用途 |
| --- | --- |
| `config/MybatisConfig.java` | 只扫描 system Mapper，启用 PostgreSQL 分页，最多 100 条 |
| `config/DatabaseExceptionHandler.java` | 数据库约束错误返回统一 400 |
| `security/JwtSigningProperties.java` | 私钥、issuer、有效期配置；有效期须为正 |
| `security/JwtSigningConfig.java` | 校验公私钥匹配、签发 RS256 Token、提供 BCrypt |
| `security/BootstrapAdminProperties.java` | 读取首位管理员初始化所需的用户名、邮箱、手机和密码配置 |
| `security/BootstrapAdminRunner.java` | 启动后按显式配置初始化管理员；未配置邮箱时跳过，用户表已有记录时不再新增 |
| `modules/auth/AuthController.java` | 接收发码、注册、登录和当前用户信息请求，调用业务接口并包装响应 |
| `modules/auth/AuthService.java` | 登录与当前用户信息业务接口 |
| `modules/auth/AccessService.java` | 当前身份与授权业务接口，继承安全模块的 CurrentUserProvider |
| `modules/auth/EmailCodeService.java` | 验证码发送与邮箱注册业务接口 |
| `modules/auth/impl/AuthServiceImpl.java` | 校验密码、签发 Token，组装用户资料、权限和菜单 |
| `modules/auth/impl/AccessServiceImpl.java` | 从本地数据库查询最新有效用户、角色和权限 |
| `modules/auth/impl/EmailCodeServiceImpl.java` | 参数处理、发码限额、邮件发送、验证码校验、创建用户与定时清理 |
| `modules/auth/dto/SendEmailCodeRequest.java` | 发送验证码请求，定义邮箱及其校验规则 |
| `modules/auth/dto/EmailRegisterRequest.java` | 注册请求，定义昵称、邮箱、验证码、密码及其校验规则 |
| `modules/auth/dto/LoginRequest.java` | 登录请求，定义 account/password，兼容原 email 字段 |
| `modules/auth/dto/LoginResponse.java` | 登录返回的 Token |
| `modules/auth/dto/UserInfoResponse.java` | 当前用户资料、权限和菜单的返回结构 |
| `modules/auth/dto/UserProfileResponse.java` | 用户基本资料，不包含密码散列 |
| `modules/auth/entity/EmailCodeEntity.java` | 验证码查询结果，含摘要、过期时间、错误次数和使用状态 |
| `modules/auth/entity/EmailSendLimitEntity.java` | 邮箱或 IP 的发送限额查询结果 |
| `modules/auth/mapper/AccessMapper.java` | 声明角色、菜单、操作权限关联的数据库访问方法 |
| `modules/auth/mapper/EmailCodeMapper.java` | 声明验证码和发送限额的数据库访问方法 |
| `system-service/src/main/resources/mapper/auth/AccessMapper.xml` | 角色、菜单、操作权限关联 SQL，以及有效超级管理员加锁查询 |
| `system-service/src/main/resources/mapper/auth/EmailCodeMapper.xml` | 验证码与发送限额 SQL、结果映射、事务行锁 |
| `modules/auth/EmailCodeProperties.java` | 绑定发件地址与验证码摘要密钥 |

### 后续亲友业务

代码放在 `system-service/src/main/java/com/base/system/modules/relations/` 下，复用应用内认证、数据源和事务。目前尚未实现，不创建空目录或占位接口。

### 用户管理

文件位于 `system-service/src/main/java/com/base/system/modules/user/` 下。

| 文件 | 用途 |
| --- | --- |
| `UserController.java` | 用户注册、分页查询、详情、创建、修改、删除接口，入口处进行参数和操作权限校验 |
| `UserService.java` | 实现用户筛选、密码散列、角色分配、软删除、管理员初始化及最后一位管理员保护；相关写操作使用事务 |
| `dto/UserRequests.java` | 集中定义创建、更新、公开注册、列表查询的入参；列表默认第 1 页、每页 10 条 |
| `dto/UserResponse.java` | 用户返回结构，含角色信息和时间，不返回密码散列 |
| `entity/UserEntity.java` | 映射 `relations.sys_user` 表：用户名、邮箱、手机、密码、头像、管理员和软删除标记等 |
| `mapper/UserMapper.java` | 用户表基础数据库操作，继承 `BaseMapper<UserEntity>` |

软删除的意思：记录仍在表中，只是把 `is_deleted` 改成 true；邮箱、用户名和手机的唯一值仍保留。

### 角色管理

文件位于 `system-service/src/main/java/com/base/system/modules/role/` 下。

| 文件 | 用途 |
| --- | --- |
| `RoleController.java` | 角色列表、详情、创建、修改、删除和授权接口 |
| `RoleService.java` | 角色维护、使用人数检查、菜单与权限整体替换授权；授权失败时事务回滚 |
| `dto/RoleRequests.java` | 创建、修改、授权请求；授权分别提交 menuIds 与 permissionIds |
| `dto/RoleResponses.java` | 列表返回 id/name/userCount，详情返回菜单 ID 和权限 ID |
| `entity/RoleEntity.java` | 映射 `relations.sys_role` 表，包含 id 和角色名 |
| `mapper/RoleMapper.java` | 角色表基础数据库操作 |

### 菜单与按钮管理

文件位于 `system-service/src/main/java/com/base/system/modules/menu/` 下。

| 文件 | 用途 |
| --- | --- |
| `MenuController.java` | 菜单列表、菜单树、菜单增删改及菜单下按钮的查询、增删改接口 |
| `MenuService.java` | 组装菜单树、按用户权限过滤菜单与按钮、维护层级、防止循环父子关系、检查删除条件、绑定按钮权限 |
| `dto/MenuRequests.java` | 菜单及按钮入参，菜单类型为 DIRECTORY/PAGE；区分不传 parentId 和显式传 null |
| `dto/MenuNode.java` | 树节点返回结构，带 children 和 buttons；管理树返回按钮详情，用户菜单返回按钮权限码 |
| `dto/ButtonResponse.java` | 按钮返回结构，包含所属菜单、权限 ID、权限码、名称和排序 |
| `entity/MenuEntity.java` | 映射 `relations.sys_menu` 表，保存名称、父级、路由、组件、图标、类型和排序等 |
| `entity/MenuButtonEntity.java` | 映射 `relations.sys_menu_button` 表，把一个菜单按钮关联到一个权限 |
| `mapper/MenuMapper.java` | 菜单表基础数据库操作 |
| `mapper/MenuButtonMapper.java` | 菜单按钮表基础数据库操作 |

这里的按钮是数据库中的按钮配置，不是已经写好的网页按钮。菜单可见与接口操作权限分别控制：能看到某页面，并不自动获得该页面所有操作权限。

### 操作权限码管理

文件位于 `system-service/src/main/java/com/base/system/modules/permission/` 下。

| 文件 | 用途 |
| --- | --- |
| `PermissionController.java` | 权限码列表、创建、修改、删除接口 |
| `PermissionService.java` | 维护权限字典；保护 system: 基础权限码，阻止删除仍被角色或按钮引用的权限 |
| `dto/PermissionRequests.java` | 权限新增和更新请求，定义 code/name 及参数校验 |
| `entity/PermissionEntity.java` | 映射 `relations.sys_permission` 表，包含 id、权限码和名称 |
| `mapper/PermissionMapper.java` | 权限表基础数据库操作 |

### 数据库迁移与测试

以下路径相对于项目根目录。

| 文件 | 用途 |
| --- | --- |
| `sql/README.md` | 集中的 SQL 目录说明：发布版本、文件用途、命名和各服务打包执行方式 |
| `sql/1.0.0/V1_1__user.sql` | 用户账号表及 relations schema，包含表和全部字段的中文注释 |
| `sql/1.0.0/V1_2__role.sql` | 角色表及中文注释 |
| `sql/1.0.0/V1_3__permission.sql` | 操作权限表及中文注释 |
| `sql/1.0.0/V1_4__menu.sql` | 菜单表、父菜单索引及中文注释 |
| `sql/1.0.0/V1_5__user_role.sql` | 用户与角色关联表、角色索引及中文注释 |
| `sql/1.0.0/V1_6__role_permission.sql` | 角色与权限关联表、权限索引及中文注释 |
| `sql/1.0.0/V1_7__menu_role.sql` | 菜单与角色关联表、菜单索引及中文注释 |
| `sql/1.0.0/V1_8__menu_button.sql` | 菜单按钮表、菜单索引及中文注释 |
| `sql/1.0.0/V1_9__seed.sql` | 初始化 18 个操作权限；不插入默认账户或密码 |
| `sql/1.0.1/V2_3__user_nickname.sql` | 增加用户昵称字段，允许重复，历史账号可为空 |
| `system-service/src/test/java/com/base/system/FlywayMigrationTest.java` | 隔离数据库迁移测试：验证执行顺序、全部表和字段的注释、初始权限及重复执行 |
| `base-common/src/test/java/com/base/foundation/FoundationHttpTest.java` | 4 项 HTTP 测试，检查统一返回、时间格式、字符串包装、参数校验、404/500 错误；测试中的 /probe 接口不会出现在正式服务 |
| `system-service/src/test/java/com/base/system/IdentityHttpTest.java` | 18 项 HTTP 集成测试，覆盖登录、用户/角色/菜单/权限管理、授权变化、回滚、Token 失效、管理员初始化及并发保护 |
| `system-service/src/test/java/com/base/system/SingleServiceHttpTest.java` | 2 项单服务 HTTP 测试，覆盖本地身份、授权和撤销、用户删除以及移除远程身份接口 |

8 张应用表的关系：

| 表 | 保存什么 |
| --- | --- |
| `sys_user` | 用户账户 |
| `sys_role` | 角色 |
| `sys_permission` | 操作权限码 |
| `sys_menu` | 菜单 |
| `sys_user_role` | 哪个用户有哪些角色 |
| `sys_role_permission` | 哪个角色有哪些操作权限 |
| `sys_menu_role` | 哪个角色能看到哪些菜单 |
| `sys_menu_button` | 菜单下的按钮与权限绑定 |

另外 Flyway 会管理迁移历史表，用于记住哪些 SQL 版本已经执行。已执行到真实数据库的 V1 不应直接修改，后续变更增加 V2 等脚本。

### 本机配置、密钥与运行配置

这些文件按本机需要准备，不要求全部提交到仓库。只需了解用途，不应把真实密码或私钥内容放进 README。

| 文件或路径 | 用途 |
| --- | --- |
| `system-service/src/main/resources/application-local.yml` | 从 example 模板准备的本机配置；被 Git 忽略，并被 Maven 排除打包，需要通过 `spring.config.additional-location` 显式加载 |
| `config/jwt-private.pem` | 应用签发 Token 的实际 RSA 私钥 |
| `config/jwt-public.pem` | 应用验签使用的实际 RSA 公钥 |
| `system-service/src/test/resources/keys/test-private.pem` | 集成测试专用签发私钥，不用于实际部署 |
| `system-service/src/test/resources/keys/test-public.pem` | 与测试私钥配套的验签公钥 |
| `.run/SystemApplication-local.run.xml` | IDEA 中 system 的本机运行配置，设置入口和本机配置加载参数 |

### 文档与 IDEA 文件

以下路径相对于项目根目录。

| 文件 | 用途 |
| --- | --- |
| `README.md` | 项目入口：模块、启动、逐文件用途、Node 对照和阅读顺序 |
| `docs/统一账号多服务设计.md` | 历史多服务设计，已由单服务合并方案替代 |
| `docs/Java后端接口与数据库规划.md` | 礼账小程序的前后端现状、接口与表设计、业务规则、分阶段验收和待确认事项；由前端迁入，尚未实施 |
| `docs/多服务拆分实施计划.md` | 历史四模块拆分记录 |
| `docs/单服务合并实施计划.md` | 当前三个模块、唯一入口和本地鉴权的合并设计、步骤及验收 |
| `docs/第一阶段实施计划.md` | 早期只搭建骨架和健康检查的计划及当时验证记录 |
| `docs/Java基础搭建实施计划.md` | 用户权限基础搭建的范围、实现顺序与验证记录 |
| `docs/基础规范与运行说明.md` | 当前基础功能的配置、数据库准备、启动要求和完整接口清单 |
| `docs/IDEA配置与运行流程.md` | IDEA 中配置 JDK、Maven、运行入口的操作；早期“无需数据库”等说明已不适用于当前实现 |
| `docs/Java学习/学习流程.md` | 你亲自学习操作的进度及文档索引，不代表已理解所有现有源码 |
| `docs/Java学习/01_认识项目与启动.md` | 早期项目认识、运行和停止记录；其中无需数据库、成功消息等旧说明须结合当前代码理解 |
| `docs/Java学习/02_创建服务子模块.md` | 创建/删除 Maven 子模块、IDEA 补全手写流程、实际遇到的问题；其中简单启动类与接口是学习阶段示例 |
| `docs/Java学习/03_包名含义与命名规范.md` | 包名、模块名、类名的含义及规范 |
| `docs/Java学习/当前项目功能与文件说明.md` | 学习版功能与文件说明，包含调用链和验证范围 |
| `docs/Java学习/启动报错与数据库配置.md` | 实际启动失败的日志根因、数据库检查结果和待执行的配置步骤 |
| `.idea/misc.xml` | IDEA 的项目 JDK、语言级别和 Maven 导入入口 |
| `.idea/compiler.xml` | IDEA 编译与注解处理配置，供 Lombok 等生成代码使用 |
| `.idea/encodings.xml` | IDEA 文件编码设置 |
| `.idea/jarRepositories.xml` | IDEA 记录的依赖仓库列表；实际 Maven 下载设置还须核对 settings.xml |
| `.idea/vcs.xml` | IDEA 与 Git 的关联 |
| `.idea/workspace.xml` | 本机运行配置、界面状态及个人操作记录 |
| `.idea/.gitignore` | 忽略 IDEA 中个人工作区和本机数据等文件 |

`.git/` 是 Git 内部记录；`target/` 是自动生成的产物，不属于需要逐个学习的手写业务文件。

`target/classes` 存编译结果，`target/test-classes` 存测试编译结果，`target/generated-sources` 和 `target/generated-test-sources` 存生成的源码，`target/maven-status` 与 `target/maven-archiver` 存构建记录，`target/surefire-reports` 存测试报告。业务模块的 `-exec.jar` 是可执行服务，普通 `.jar` 用于模块引用。

## 沿一个请求读代码

以 `GET /user/list?pageNum=1&pageSize=10` 为例：

1. `RequestLogFilter` 开始计时。
2. `SecurityConfig` 校验 Bearer Token，`AccessService` 查询当前用户与最新权限。
3. `UserController.list()` 检查查看用户列表的权限，读取并校验 `UserRequests.ListQuery`。
4. `UserService.list()` 组装筛选条件和分页参数。
5. `UserMapper.selectPage()` 通过 MyBatis-Plus 查询 `relations.sys_user`。
6. `UserService` 将结果转为不含密码的 `UserResponse`，放入 `PageResult`。
7. `ResponseAdvice` 包装为 ApiResponse，`JacksonConfig` 参与时间序列化。
8. 返回 JSON；日志过滤器记录状态与耗时。发生错误时，由安全错误处理器或全局异常处理器返回统一错误。

因此，查一个功能时先看 Controller 路由，再进入 Service，最后找 Mapper 和 Entity。IDEA 的 `Ctrl+B` 可以跳到声明或方法定义。

理解主链路后，再阅读安全和模块加载代码：

1. `SystemApplication`：唯一入口如何导入公共模块。
2. `HealthController` → `ResponseAdvice` → `ApiResponse`：简单请求如何返回。
3. `UserController` → `UserRequests` → `UserService.list()` → `UserMapper` / `UserEntity` → `UserResponse`：一个用户查询如何完成。
4. `AuthController`、`AuthService`：如何登录、校验密码和签发 Token。
5. `SecurityConfig` → `CurrentUserProvider`：应用内使用 AccessService，理解认证时怎样查询本地数据库。

早期学习文档中的单服务、JWT_SECRET 或“无需数据库”等内容属于当时的学习阶段；当前运行方式以本 README 和 [基础规范与运行说明](docs/基础规范与运行说明.md) 为准。
