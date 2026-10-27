-- 借贷按实际账户发生资金变化；旧借贷回填现金，不补造家庭存款统计。
ALTER TABLE relations.ledger_loan ADD COLUMN project_id BIGINT REFERENCES relations.ledger_money_project(id);
UPDATE relations.ledger_loan SET project_id = (
  SELECT id FROM relations.ledger_money_project
  WHERE family_id = relations.ledger_loan.family_id AND cash_key = 'cash'
) WHERE project_id IS NULL;
CREATE INDEX idx_ledger_loan_family_project ON relations.ledger_loan(family_id, project_id);

ALTER TABLE relations.ledger_household_item ADD COLUMN loan_id BIGINT REFERENCES relations.ledger_loan(id);
ALTER TABLE relations.ledger_household_item ADD COLUMN saving_effect VARCHAR(10) NOT NULL DEFAULT 'add'
  CHECK (saving_effect IN ('add', 'subtract'));
CREATE UNIQUE INDEX idx_ledger_household_item_loan ON relations.ledger_household_item(loan_id);

COMMENT ON COLUMN relations.ledger_loan.project_id IS '借贷资金账户；空值兼容为所属家庭现金';
COMMENT ON COLUMN relations.ledger_household_item.loan_id IS '自动生成存款统计所关联的借贷流水，普通收支为空';
COMMENT ON COLUMN relations.ledger_household_item.saving_effect IS '存款统计方向：add增加，subtract冲减；金额保持正数';
