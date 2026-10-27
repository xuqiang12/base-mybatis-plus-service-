-- 只关联启用后的新增人情，不回填旧记录的账户或家庭收支。
ALTER TABLE relations.ledger_gift ADD COLUMN project_id BIGINT REFERENCES relations.ledger_money_project(id);
ALTER TABLE relations.ledger_household_item ADD COLUMN gift_id BIGINT REFERENCES relations.ledger_gift(id);
ALTER TABLE relations.ledger_household_item ADD COLUMN record_date DATE;
CREATE UNIQUE INDEX idx_ledger_household_item_gift ON relations.ledger_household_item(gift_id);

COMMENT ON COLUMN relations.ledger_gift.project_id IS '新增收送礼的实际资金账户；旧记录为空，不参与账户余额';
COMMENT ON COLUMN relations.ledger_household_item.gift_id IS '自动生成收支关联的人情流水，不可单独修改或删除';
COMMENT ON COLUMN relations.ledger_household_item.record_date IS '关联人情的实际日期；旧月度收支为空，沿用月初日期';
