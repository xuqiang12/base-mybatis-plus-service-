ALTER TABLE relations.ledger_category
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN relations.ledger_category.deleted_at IS '软删除时间，非空时不再作为可见或可用分类';
