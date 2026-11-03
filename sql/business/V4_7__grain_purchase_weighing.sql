ALTER TABLE business.biz_trade_item ADD COLUMN gross_weight_kg NUMERIC(12,3);
ALTER TABLE business.biz_trade_item ADD COLUMN tare_weight_kg NUMERIC(12,3);
ALTER TABLE business.biz_trade_item ADD CONSTRAINT ck_biz_grain_weighing CHECK (
  (gross_weight_kg IS NULL AND tare_weight_kg IS NULL)
  OR (gross_weight_kg IS NOT NULL AND tare_weight_kg IS NOT NULL
      AND tare_weight_kg >= 0 AND gross_weight_kg > tare_weight_kg
      AND category = 'grain' AND unit = '斤'
      AND quantity = (gross_weight_kg - tare_weight_kg) * 2)
);

COMMENT ON COLUMN business.biz_trade_item.gross_weight_kg IS '收粮毛重，千克；历史未称重记录可空';
COMMENT ON COLUMN business.biz_trade_item.tare_weight_kg IS '收粮皮重，千克；净重为毛重减皮重，quantity保留结算斤数';
