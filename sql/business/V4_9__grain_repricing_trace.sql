ALTER TABLE business.biz_trade ADD COLUMN repriced_into_id BIGINT;
ALTER TABLE business.biz_trade ADD CONSTRAINT fk_biz_trade_repricing
  FOREIGN KEY(owner_id,customer_id,repriced_into_id) REFERENCES business.biz_trade(owner_id,customer_id,id);
CREATE INDEX idx_biz_trade_repricing ON business.biz_trade(owner_id,repriced_into_id);
COMMENT ON COLUMN business.biz_trade.repriced_into_id IS '重新计价后的原单编号；旧价格与原始称重关联保持不变，旧原单不再计入待付';
