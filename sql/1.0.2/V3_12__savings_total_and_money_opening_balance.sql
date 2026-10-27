-- 总存款需要保留非现金账户的期初余额；历史收入统一归入现金。
ALTER TABLE relations.ledger_money_project ADD COLUMN initial_balance_cents BIGINT NOT NULL DEFAULT 0
  CHECK(initial_balance_cents >= 0 AND initial_balance_cents <= 9007199254740991);

-- 先补偿账户调拨，再统一收入账户，避免已有账户支出因迁移而透支。
INSERT INTO relations.ledger_money_transfer(family_id,from_project_id,to_project_id,amount_cents,note,created_by)
SELECT item.family_id, project.id, item.project_id, item.amount_cents,
       '历史收入归入现金，同步保留原账户资金', item.created_by
FROM relations.ledger_household_item item
JOIN relations.ledger_money_project project ON project.family_id=item.family_id AND project.cash_key='cash'
WHERE item.type='income' AND item.project_id <> project.id;

UPDATE relations.ledger_household_item item SET project_id = (
  SELECT project.id FROM relations.ledger_money_project project
  WHERE project.family_id = item.family_id AND project.cash_key = 'cash'
) WHERE item.type = 'income' AND item.project_id <> (
  SELECT project.id FROM relations.ledger_money_project project
  WHERE project.family_id = item.family_id AND project.cash_key = 'cash'
);

COMMENT ON COLUMN relations.ledger_money_project.initial_balance_cents IS '账户期初余额，整数分；修改账户资料不能改变';
