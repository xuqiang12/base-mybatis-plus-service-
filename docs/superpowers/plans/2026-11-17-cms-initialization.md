# 运营配置与初始化实施计划

**目标：** 在已确认的 CMS 归属下完成首页装修、页面绑定和底部导航的表结构、独立 Seed、Java 接口及 Vue 管理页面。

**约束：** 沿用当前 feature 分支和模块结构；不提交、不部署；只向 gift_ledger 增加 CMS 数据，不导入阿里云账号账目；不增加 AI 配置；保留已有配置和授权。JDK 21、Node 22 仅在本次命令中指定。

**前提：** 小程序真实入口为 /pages/index/index、/pages/category/index、/pages/tools/index、/pages/mine/index。当前首页运行家庭账本视图，旧通用装修 API 仍指向 Nest；本阶段提供 Java CMS 与管理后台，不替换家庭账本页面或更改小程序请求路由。默认装修使用现有支持的 homePageTitle/notice/cube 组件格式，导航使用实际页面和静态图标。

## 数据与接口契约

- relations.cms_home_decoration：id VARCHAR(64) 主键、code 唯一稳定编码、name、scene、status(0/1)、时间。
- relations.cms_home_component：id VARCHAR(64)、decoration_id 外键、template_id、template_name、info JSONB、sort_no、status，随方案一起事务保存。
- relations.cms_page_binding：id VARCHAR(64)、name、page_key 唯一、decoration_id 限制删除外键、status、时间。
- relations.cms_tabbar_config：id VARCHAR(64)、code 唯一、name、config JSONB、status、active_key 唯一可空；status=1 时 active_key='active'，否则为空，保证同时最多一个启用方案。
- Java 包 modules/cms，沿用现有 ResponseAdvice 包装 ApiResponse。
- GET /cms/home/list 返回 Home[]（包含 components）；GET /cms/home/detail?id；POST /cms/home/save（完整保存）；POST /cms/home/delete {id}。
- GET /cms/page/list 返回 PageBinding[]；POST /cms/page/save；POST /cms/page/delete {id}。
- GET /cms/tabbar/list 返回 Tabbar[]；GET /cms/tabbar/detail?id；POST /cms/tabbar/save；POST /cms/tabbar/delete {id}。
- Home：{id,code,name,scene,status,components:[{id,templateId,templateName,info,sortNo,status}]}。Save id 可空，code 创建后不改，组件 id 可空。scene 默认 homePage。
- PageBinding：{id,name,pageKey,decorationId,status}，pageKey 创建后不改。仅一个 home 默认绑定。
- Tabbar：{id,code,name,status,config:{tabBarMode,bgColorMode,bgColor,textColorMode,textColor,activeColor,radiusMode,rootPagePath,items:[{id,name,icon,activeIcon,linkType:'page',pagePath,sortNo}]}}。
- Save 返回保存后的对象；Delete 返回成功文字。未找到404、重复编码/绑定引用409、无效配置400、权限不足403。
- 管理权限 gl:cms:home:list/add/edit/delete、gl:cms:page:list/add/edit/delete、gl:cms:tabbar:list/add/edit/delete；详情复用 list 权限。修改有 id 使用 edit，无 id 使用 add；超级管理员放行。
- 登录用户 GET /cms/client/page?pageKey=home：返回启用绑定对应的启用 Home（仅启用组件，按 sortNo）；GET /cms/client/tabbar：返回启用导航的 config 并带顶层 id/name。无有效配置404。沿用现有登录要求，避免新增匿名配置入口。
- 后台路径 /cms/home/index、/cms/page/index、/cms/tabbar/index，父目录 /cms“运营管理”。

## 任务

- [x] 1. 先编写 CMS Seed 保留/冲突回滚测试与真实 HTTP 权限/保存测试，确认缺少功能失败。
- [x] 2. 追加 V3.21（不改历史 SQL），四张 CMS 表及必要唯一/外键/状态约束、中文注释。原迁移测试最终版本断言同步。
- [x] 3. database-seed 增加 CmsSeedService 与 seeds/system-service/cms 的 home.json/page-bindings.json/tabbar.json 及独立管理权限/菜单/按钮 JSON；cms-home/1、cms-tabbar/1、cms-management/1 分别登记历史，默认配置只补缺；重复跳过、已有内容不覆盖、不给现有角色自动扩权。旧 system-base/1、admin-account/1 不变。
- [x] 4. Java CMS 服务和 Controller：完整保存组件事务、引用保护、底部导航单启用、结构校验；HTTP 用真实鉴权验证普通用户不能写、按授权可读写、撤权立即生效。
- [x] 5. Vue 新增三张运营页面、API 类型和真实表单交互测试。组件内容通过具名组件行编辑 info 对象，导航提供普通表单及项目列表；不新增通用表单框架。
- [x] 6. JDK21 Maven verify、Node22 前端测试/类型检查/相关 ESLint/构建；独立代码复核。
- [x] 7. gift_ledger 初始化前保存只读指纹，执行新 Seed，验证旧业务及系统行未变、CMS 默认内容可读、重复前后全库相同；启动临时 Java 验证真实 PostgreSQL HTTP 后停止。
- [x] 8. 更新模块 README、操作文档和实际结果，明确小程序页面接入边界。

## 验收

从后台新增或编辑装修组件、页面绑定及导航后重新加载能读到保存值；未授权请求拒绝；被页面引用的装修不能删除；重复 Seed 不覆盖人工修改或恢复撤销授权；默认无 AI，导航入口与礼账实际页面一致；原库和阿里云不写入。
