-- 使用独立标识区分系统现金，保留 V3_7 的项目类型约束及已应用迁移校验和。
ALTER TABLE relations.ledger_money_project ADD COLUMN cash_key VARCHAR(4);
ALTER TABLE relations.ledger_money_project ADD CONSTRAINT ck_ledger_money_cash_key
  CHECK(cash_key IS NULL OR (cash_key = 'cash' AND type = 'other'));
ALTER TABLE relations.ledger_money_project ADD CONSTRAINT uq_ledger_money_family_cash UNIQUE(family_id, cash_key);

-- 每个当前或历史家庭都有一条现金项目；旧版空项目编号统一接到该项目。
INSERT INTO relations.ledger_money_project(family_id,type,name,cash_key)
SELECT id, 'other', '现金', 'cash' FROM relations.ledger_family;

UPDATE relations.ledger_household_item item SET project_id = (
  SELECT project.id FROM relations.ledger_money_project project
  WHERE project.family_id = item.family_id AND project.cash_key = 'cash'
) WHERE item.project_id IS NULL;

UPDATE relations.ledger_money_transfer transfer SET from_project_id = (
  SELECT project.id FROM relations.ledger_money_project project
  WHERE project.family_id = transfer.family_id AND project.cash_key = 'cash'
) WHERE transfer.from_project_id IS NULL;

UPDATE relations.ledger_money_transfer transfer SET to_project_id = (
  SELECT project.id FROM relations.ledger_money_project project
  WHERE project.family_id = transfer.family_id AND project.cash_key = 'cash'
) WHERE transfer.to_project_id IS NULL;

ALTER TABLE relations.ledger_household_item ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE relations.ledger_money_transfer ALTER COLUMN from_project_id SET NOT NULL;
ALTER TABLE relations.ledger_money_transfer ALTER COLUMN to_project_id SET NOT NULL;

COMMENT ON COLUMN relations.ledger_money_project.cash_key IS '系统现金标识，每家庭唯一现金项目；其他项目为空';
COMMENT ON COLUMN relations.ledger_money_project.type IS '项目类型：银行、股票或其他；系统现金由现金标识区分';
COMMENT ON COLUMN relations.ledger_household_item.project_id IS '收支资金项目编号，现金也保存真实项目编号；旧存款记录不计入资产';
COMMENT ON COLUMN relations.ledger_money_transfer.from_project_id IS '调拨来源项目编号，包含系统现金项目';
COMMENT ON COLUMN relations.ledger_money_transfer.to_project_id IS '调拨目标项目编号，包含系统现金项目';
