# system-service：唯一后端应用

记住一句话：**提供登录、账号权限管理，并承载后续亲友业务。**

这是项目唯一独立运行的服务，默认端口 **8080**，启动入口是 `SystemApplication.java`。它引用 `base-security`，并通过它引入 `base-common`。

| 负责什么 | 例子 |
| --- | --- |
| 注册与登录 | 邮箱验证码注册并设置密码，邮箱或手机号加密码登录，签发 Token |
| 用户管理 | 创建用户、修改用户、分配角色 |
| 角色管理 | 维护角色，给角色分配菜单和操作权限 |
| 菜单与按钮管理 | 保存前端导航、菜单层级和按钮权限绑定 |
| 权限码管理 | 维护 `system:user:list` 等操作权限 |
| 本地身份授权 | AccessService 接口由 impl/AccessServiceImpl 实现，每次从数据库读取最新用户状态与权限 |
| 后续亲友业务 | 在 `modules/relations/` 内按业务分目录开发，目前尚未实现 |

账号、角色、菜单、权限数据保存在这个服务连接的数据库里。前端向同一个应用登录、获取菜单并请求业务接口。

建表 SQL 集中在根目录的 [数据库脚本](../sql/README.md)，按发布版本分目录。当前 V1、V2.1 和注册流程更正增量 V2.2 由本服务的 POM 打包，启动时通过 Flyway 自动执行。

邮箱注册接口为 `POST /auth/email/code`、`POST /auth/email/register`，注册时必须设置昵称 `nickname` 和密码；昵称去除首尾空白后为 1～100 个字符，允许重复，通过 `/auth/userInfo` 返回。`POST /auth/login` 使用邮箱或手机号加密码。登录后可通过 `POST /user/bindPhone` 填写手机号绑定，暂不短信验证。SMTP 配置及验收见 [邮箱注册与密码登录](../docs/Java学习/04_邮箱注册与密码登录.md)。旧 `/user/register` 仅允许超级管理员调用。

请求由 security 校验 Token，经 CurrentUserProvider 调用本地 AccessServiceImpl 查询最新授权，无需服务间 HTTP 通信。auth 的业务类统一采用 Service 接口与 impl/ServiceImpl 实现类，自定义 SQL 位于 resources/mapper/auth/。

职责速记：**common 管通用请求处理，security 管身份与权限检查，system 管账号和权限数据。**

具体配置和启动步骤见 [项目说明](../README.md) 与 [基础规范与运行说明](../docs/基础规范与运行说明.md)。
