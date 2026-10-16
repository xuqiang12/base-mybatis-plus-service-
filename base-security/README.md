# base-security：公共身份验证与权限检查

记住一句话：**检查来访者是谁，以及有没有权限调用接口。**

这是公共类库，不单独启动、不占端口。应用引用它，它同时引入 `base-common`。

| 负责什么 | 例子 |
| --- | --- |
| 验证 Token | 用公钥检查签名、有效期和签发者 |
| 取得当前身份 | 通过 `CurrentUserProvider` 获取最新用户、角色和权限 |
| 检查接口权限 | 判断当前用户是否有 `system:user:list` 等权限 |
| 配置公开接口 | 明确哪些接口允许匿名访问 |
| 统一安全错误 | 未登录返回 401、无权限返回 403 |

它不负责用户、角色、菜单的增删改查。账号权限数据由 `system-service` 管理：通过应用内 AccessService 在本地查询，不发起服务间 HTTP 请求。

`CurrentUserProvider` 只传入用户 ID，不转发 Token。

职责速记：**common 管通用请求处理，security 管身份与权限检查，system 管账号和权限数据。**

[返回项目说明](../README.md)
