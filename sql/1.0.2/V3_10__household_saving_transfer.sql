-- 旧存款仅保留统计；只有新版录入的存款才从现金转入指定资金项目。
ALTER TABLE relations.ledger_household_item ADD COLUMN saving_transfer BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN relations.ledger_household_item.saving_transfer IS '是否联动存款资金：新存款从现金转入项目，旧记录默认否，仅保留统计';
COMMENT ON COLUMN relations.ledger_household_item.project_id IS '收入去向、支出来源或存款去向；旧存款不参与资金余额计算';
