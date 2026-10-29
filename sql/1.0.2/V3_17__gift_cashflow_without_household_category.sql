-- 人情通过 gift_id 归入人情往来，普通收支继续使用家庭分类。
ALTER TABLE relations.ledger_household_item ALTER COLUMN category_id DROP NOT NULL;

-- 先识别仅供人情使用的旧分类；有普通收支引用的分类继续保留。
UPDATE relations.ledger_category
SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
WHERE deleted_at IS NULL
  AND EXISTS (
    SELECT 1 FROM relations.ledger_household_item item
    WHERE item.category_id = ledger_category.id AND item.gift_id IS NOT NULL
  )
  AND NOT EXISTS (
    SELECT 1 FROM relations.ledger_household_item item
    WHERE item.category_id = ledger_category.id AND item.gift_id IS NULL
  );

UPDATE relations.ledger_household_item SET category_id = NULL WHERE gift_id IS NOT NULL;

ALTER TABLE relations.ledger_household_item
    ADD CONSTRAINT ck_household_item_category_source CHECK (
        (gift_id IS NOT NULL AND category_id IS NULL)
        OR (gift_id IS NULL AND category_id IS NOT NULL)
    );

COMMENT ON COLUMN relations.ledger_household_item.category_id IS '普通家庭收支的分类；人情关联收支为空，按人情来源归组';
