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

当前 `1.0.1/` 有三份已登记的增量：`V2_1__email_login.sql` 建立验证码、限额存储并允许手机号/密码为空；`V2_2__email_registration.sql` 将标记更正为 `email_verified` 并使旧用途验证码作废；`V2_3__user_nickname.sql` 增加可重复的 `nickname VARCHAR(100)`。最终业务为邮箱验证码注册且必须设置昵称和密码，邮箱或手机号加密码登录，手机号登录后填写绑定。V2.1 保留历史不改，V2.2 不为旧无密码账号自动设置密码，V2.3 保留历史账号昵称为空。

目录版本表示发布批次；文件的 `V1_1__` 到 `V1_9__` 表示 Flyway 版本 1.1 到 1.9，按版本号顺序执行，两者独立。Flyway 使用两个下划线分隔版本号和说明，同一服务、同一迁移历史内不能重复版本号。下一次迁移可以使用 `V2__`，它排在 1.9 之后。

已执行的脚本不改文件名和内容，后续变更新增脚本。同一次尚未执行的更新可以包含多个字段改动，不需要每增加一个字段就新增文件。

2026-10-14 拆分前只读核对了本机配置指向的数据库：没有 relations schema、应用表或 Flyway 历史，因此将原单文件 V1 拆成上述 9 个初始脚本。没有在实际数据库执行迁移。这组脚本用于首次初始化；其他环境若已执行原 `V1__identity_foundation.sql`，应保留其原迁移历史并另行编写增量，不能直接替换为这组初始脚本，也不要清空历史来绕过校验。

## 如何执行

system-service 的 `pom.xml` 使用 `<include>**/V*__*.sql</include>` 自动包含本目录下符合命名规则的各版本脚本，原样复制到运行包的 `db/system/` 并保留版本子目录。Flyway 按已有 `classpath:db/system` 配置递归发现，并按文件版本执行：先建基础表，再建关联表，最后初始化权限。Flyway 的 `relations.flyway_schema_history` 记录已执行版本，后续启动会校验并跳过它们。

以后新增 system 脚本时，使用未占用的 Flyway 版本并遵守 `V版本号__说明.sql` 命名即可，不必逐个修改 POM；重新构建复制资源，再启动服务执行迁移。当前只有一个运行服务，后续业务脚本统一由 system 打包和执行。完整练习见 [从表设计到接口开发：亲友实战模板](../从表设计到接口开发_亲友实战模板.md)。

服务源码目录不再维护另一份 SQL。`target/` 和 JAR 中的 SQL 是自动生成的打包副本，请修改本目录中的原文件。

V3.1 是已执行的家庭及原账目建表历史，原文保持不可变。2026-10-19 按用户要求新增 [V3_2__remove_prebuilt_ledger_tables.sql](1.0.2/V3_2__remove_prebuilt_ledger_tables.sql)：仅删除 ledger_invitation、ledger_friend、ledger_book、ledger_gift、ledger_loan、ledger_category、ledger_household 七张表，保留 ledger_family、ledger_member 及账号权限表。删除按外键依赖顺序执行，不使用 CASCADE，不清空 Flyway 历史。随后 [V3_3__friend.sql](1.0.2/V3_3__friend.sql) 重新建立亲友档案表，供当前家庭范围的亲友 CRUD 使用。

2026-10-23 按完整开发授权新增：

| 脚本 | 内容 |
| --- | --- |
| [V3_4__ledger_books_and_gifts.sql](1.0.2/V3_4__ledger_books_and_gifts.sql) | 家庭账簿、人情流水、亲友与姓名快照 |
| [V3_5__ledger_loans.sql](1.0.2/V3_5__ledger_loans.sql) | 借出、收款、借入、归还流水 |
| [V3_6__monthly_household_ledger.sql](1.0.2/V3_6__monthly_household_ledger.sql) | 分类、唯一家庭月份表、分类月合计 |

原 V3.1～V3.3 内容未修改。本机配置对应的 PostgreSQL 已从 3.3 升级至 3.6，Flyway 校验 19 份迁移通过。迁移前备份保存在 `F:\公司项目\数据库备份\base-mybatis-plus-service\2026-10-23_162935_礼账迁移前`。加入前旧单人家庭复用 ledger_family.hidden_owner 作为本人只读归属，不需要改写迁移。分类默认项在家庭首次读取时初始化，不插入演示账目。完整验证见[交付报告](../docs/礼账开发交付与验收报告.md)。

2026-10-27 新增 [V3_11__loan_money_project_and_saving_link.sql](1.0.2/V3_11__loan_money_project_and_saving_link.sql)：借贷保存资金账户，历史借贷回填所属家庭现金；家庭收支新增借贷关联与存款增加/冲减标记，不为历史借贷补造收支。新借出和收款在业务事务内生成存款统计记录，账户只按借贷流水调整一次。本次仅完成隔离测试，未在真实 PostgreSQL 执行该迁移。

2026-10-27 新增 [V3_12__savings_total_and_money_opening_balance.sql](1.0.2/V3_12__savings_total_and_money_opening_balance.sql)：账户增加整数分期初余额；历史非现金收入统一归入现金，并补一笔等额现金转回原账户的调拨，保留各账户已有余额。资金汇总新增总存款 `savingsCents = heldCents - cashCents + lentCents - debtCents`，原 `totalCents` 继续表示净资产。新收入只允许现金，借贷辅助记录不计入普通家庭存款合计。本次仅通过隔离迁移和 HTTP 测试，未执行真实 PostgreSQL 迁移；部署时需包含此脚本。

2026-10-27 新增 [V3_13__money_timeline.sql](1.0.2/V3_13__money_timeline.sql)：资金操作日志保存每次操作后的净资产、总存款、非现金账户余额合计；与账户期初余额、转账、家庭收支增改删及借贷在同一家庭锁和事务内写入。历史记录不回填推算快照。`GET /ledger/money/timeline` 只读当前家庭日志，使用 `beforeId` 倒序游标分页，每页20条；修改和删除追加日志，保留先前快照。隔离 H2 HTTP 与迁移回归41项通过；未部署、未连接真实 PostgreSQL 执行迁移。

资金时间轴支持按实际操作时间（北京时间）分月：`GET /ledger/money/timeline/months?keyword=...` 返回倒序月份及各月真实匹配笔数；`GET /ledger/money/timeline?month=2026-10&keyword=...&beforeId=...` 查询月内日志并沿用每页20条的游标分页。不传 `month` 时保留原全量时间轴接口行为。两个接口都只搜索当前家庭，`keyword` 对分类/亲友标题、备注和账户流向进行文字包含匹配；数字另按元精确匹配本笔操作金额（如 `4000`、`4000.00`、`4,000.00`），不匹配三项余额快照。本次折叠与搜索无需新增数据库迁移；隔离 H2 关联回归45项通过，未部署或连接真实 PostgreSQL。

[返回项目说明](../README.md)
