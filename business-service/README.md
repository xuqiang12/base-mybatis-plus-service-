# 生意记账模块

更新日期：2026-11-02。当前只管理农户往来账：卖化肥记应收、逐车收粮称重、客户详情集中计价记应付，以及所选账单收款结清、付款结清和抵账。

## 模块与数据边界

- 包名 `com.base.business`，仅依赖 `base-security`（含 `base-common`）和JDBC，不依赖家庭业务。由 `system-service` 通过runtime依赖和自动配置装配，无需额外服务。
- `business.enabled=false` 可关闭生意接口及Bean，登录和家庭业务继续使用。共用技术基础不代表共享客户、账户、流水或统计。
- 所有接口需要现有Token，返回 `{ code, message, data }`。服务端从Token取得 `owner_id`，一账号一本总账，不接受客户端指定归属，不需要账套或生意成员；家庭成员不能访问其他账号的生意。
- ID返回字符串，金额返回整数分。后端使用精确小数计算数量、重量和单价；前端不根据当前页推算客户完整余额。
- API及前端不再区分客户类型。数据库旧 `biz_customer.type` 列仅作历史兼容：新建固定写入 `farmer`，修改不改写历史类型；所有未删除的历史客户继续可查可改，不删除或隐藏旧客户及其账单。

## 当前接口

| 方法 | 路径 | 用途或请求 |
| --- | --- | --- |
| GET | `/business/customer/list` | `keyword?、pageNum=1、pageSize=20`，页大小最多100，不再按类型筛选 |
| GET | `/business/customer/detail` | `id`，返回资料与剩余未结款 |
| POST | `/business/customer/create` | `name、requestId`；选填 `appellation、phone、remark` |
| POST | `/business/customer/update` | `id、name`；选填项可清空，不接收客户类型 |
| POST | `/business/customer/delete` | `id`，校验未结款及待计价粮食后软删除 |
| GET | `/business/product/list` | 当前账号可用化肥商品 |
| POST | `/business/product/create` | 商品资料及 `requestId` |
| POST | `/business/product/update` | `id`及完整商品资料 |
| POST | `/business/product/delete` | `id`，隐藏商品，保留原单快照 |
| GET | `/business/account/list` | 独立生意收付款账户 |
| POST | `/business/account/create` | `name`，同名复用已有账户 |
| POST | `/business/fertilizer-sale/create` | `customerId、bizDate、items、remark?、requestId`，只保存应收 |
| GET | `/business/fertilizer-sale/list` | `customerId、pageNum、pageSize`，销售原单分页 |
| GET | `/business/fertilizer-sale/detail` | `id`，返回原始商品快照及结算金额 |
| POST | `/business/grain-weighing/create` | 每车原始称重，不写金额欠款 |
| GET | `/business/grain-weighing/list` | `customerId、pageNum、pageSize`，待计价车次及总净重 |
| GET | `/business/grain-weighing/tare-history` | `customerId`，当前客户最近一次成功保存的皮重 |
| POST | `/business/grain-purchase/create` | `customerId、weighingIds、unitPrice、remark?、requestId`，只计价生成应付 |
| POST | `/business/grain-purchase/reprice` | `customerId、weighingIds、purchaseIds、unitPrice、requestId`，混选已计价及待计价粮食时统一重新计价 |
| GET | `/business/grain-purchase/list` | `customerId、pageNum、pageSize`，已计价粮款分页 |
| GET | `/business/grain-purchase/detail` | `id`，粮款单及原始车次 |
| POST | `/business/settlement/create` | 所选账单结清或抵账，见下方规则 |
| GET | `/business/settlement/list` | `customerId、pageNum、pageSize、tradeId?`，可按原单查询真实收付和抵账历史 |
| GET | `/business/customer/bills` | `customerId、settled、pageNum、pageSize`，统一分页返回待处理或已结清账单行及全部记录汇总；一次销售中的不同化肥商品各占一行 |

客户资料为姓名、称谓、电话、备注四字段：姓名必填最多30字，称谓／电话最多30字，备注最多200字；创建请求编号最多80字。按姓名、称谓包含搜索，按创建时间和ID倒序分页。删除要求应收、应付同时为0且没有待计价粮食；重复删除可重试，跨账号或不存在返回404，删除后历史买卖、称重与结算不删除。

## 商品和销售

商品资料为 `name、brand、specification、bagWeightKg、salePrice、remark`。名称及品牌最多50字，规格最多100字，备注最多200字；袋重大于0且最多三位小数，常用售价可空，填写时大于0且最多两位小数。商品列表显示品牌、规格、袋重和可空常用售价。

销售 `items` 包含 `productId、quantity、unitPrice`，数量为整袋、单价为元／袋。后端校验客户和商品归属，重算金额，保存名称、袋重、数量、单价及金额快照，不写即时收款或分配。商品修改／删除不改写旧单。账期按销售日期分为含年份的春耕（1—6月）和秋耕（7—12月），不复制跨季欠款。

## 原始称重和集中计价

- 称重请求为 `customerId、grain、grossWeightKg、tareWeightKg、requestId`，不再传日期和备注，后端自动记录创建时间，既有日期列写数据库当天日期，备注为空。品种为小麦或水稻。毛皮重最多9位整数、3位小数，皮重允许0，毛重须大于皮重；后端计算净重kg和斤，1kg=2斤。
- 未计价车次 `purchaseId=null`，只统计重量，不产生金额欠款。待计价接口返回 `list、total、pageNum、pageSize、pendingNetWeightKg`。
- 历史皮重按Token账号及客户隔离，每个客户只返回最近一次成功保存的皮重，已计价车次也可使用，原始称重不删除；前端点击回填，值匹配时才高亮，切换客户清空皮重，查询失败仍允许手动输入，无新增车辆档案。
- 计价最多选1000车，要求同账号、同客户、同品种、尚未计价且不重复。元／斤单价最多四位小数，先汇总净重再计算粮款，四舍五入一次到整数分。原单、汇总明细及称重关联在同一事务保存，不产生付款。
- 原始称重不改写，保留首次计价关联。历史粮款单继续可查可结，没有新车次时 `weighings` 为空，保留原有重量、金额及可用的毛皮重，不补造车次。

计价及重新计价均使用数据库当天日期，不使用客户端日期。重新计价须同时提供待计价车次及已计价粮款，要求同账号、同客户、同品种；有有效付款或抵账分配（含部分）的原单返回409。事务生成新粮款单，旧单标为void并通过 `repriced_into_id` 关联新单；旧重量、单价、金额和称重关联保留。详情返回 `sourcePricings`（直接原计价单快照）及 `repricedIntoId`，可逐层查询旧单；新单 `weighings` 包含所有来源车次，支持多次重新计价追溯。旧单不计入待付或已结清列表，不产生付款或抵账。

## 统一结算

客户详情统一展示化肥、已计价粮款与待计价车次。`settled=false` 返回待处理记录，`settled=true` 只返回剩余金额为0的原单；未计价车次的金额为null，不进入已结清历史。返回 `fertilizerCents` 为全部筛选记录的化肥未结金额，`grainWeight` 为粮食总净重斤数，均不局限当前分页。原始账单和逐次结算通过原单详情及 `settlement/list?tradeId=...` 追溯。

化肥账单行直接返回商品快照：`id` 使用 `biz_trade_item.id`，`tradeId` 保留原销售单，另含 `title、quantity、unit、unitPrice、totalCents、remainingCents`；粮食计价单仍使用原采购单ID，未计价车次单独返回。商品剩余金额按有效结算分配逐行计算，前端可直接逐商品选择、结算或抵账；原销售单仍可通过 `tradeId` 打开详情和查询历史。

请求为 `customerId、kind、saleIds、purchaseIds、amountCents、accountId?、bizDate?、requestId`：

- `receive`：只选择化肥销售账单，按所选剩余应收全额结清。
- `pay`：只选择粮款账单，按所选剩余应付全额结清。
- `offset`：同时选择双边原单，以双边剩余较小值抵扣；两侧分配同金额，不产生现金流水、不允许资金账户。
- 收款和付款均无需选择账户，仅记录结算金额及账单分配；旧请求携带账户时仍校验归属。付款保留结算日期选择。收款及抵账统一使用数据库当天日期，不使用客户端日期；后端重算有效余额并核对预览金额，余额变化或已结清返回409。分配仅针对所选有效原单，不能跨客户、跨账号或重复选择。
- 使用账号锁、事务及请求幂等，重复同内容返回原结果，不同内容返回409；失败回滚，拒绝并发重复结算。一次可结多单，抵账后的余额可继续结清。
- 历史返回日期、实际账户、关联原单和分配；原单分别返回实际 `receivedCents`／`paidCents`、`offsetCents` 和 `remainingCents`。历史部分付款继续保留，不把抵账当作现金收付。
- 旧 `/business/grain-purchase/pay` 已删除，统一使用独立结算接口，不再提供独立输入部分付款金额的入口。

客户余额按原单减有效分配计算。`fertilizerBalanceCents`、`grainBalanceCents` 为各类未结应收减应付，`balanceCents` 为净额；`receivableCents`、`payableCents` 分别返回未结应收／应付，只有两者同时为0才 `settled=true`，净额0仍有双向未结款时显示待抵账。

## 迁移与范围

当前使用 `biz_customer、biz_product、biz_account、biz_trade、biz_trade_item、biz_grain_weighing、biz_settlement、biz_settlement_item`。原单、结算及分配是当前追溯和余额查询所需结构，不属于进销存遗留。

V4.1／V4.2 保留旧建表历史；Java迁移V4.3将原账套归到账号并移除账套、成员及 `book_id`；V4.4软删除，V4.5商品与账户及原单字段，V4.6商品扩展，V4.7旧粮款毛皮重，V4.8独立称重及结算幂等，V4.9重新计价原单关系。启动模块统一打包SQL并保留一条Flyway历史，不修改或删除已执行迁移，不因关闭生意删除历史数据。本次新增V4.9迁移，但不执行真实数据库变更。

当前不做商家管理、进化肥、卖粮、库存、成本、费用、利润、文字／语音／图片填单、扣水杂、称重修改／删除或跨客户抵账。旧进销存和自动填单规划不作为待开发要求。

测试使用隔离H2及真实HTTP，覆盖账号隔离、客户资料、历史保留、商品快照、称重计价、分配与抵账、重试、并发、回滚及停用隔离。本地测试与构建不代表真实PostgreSQL迁移、真机验收或部署完成。
