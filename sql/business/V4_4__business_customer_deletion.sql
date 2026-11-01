-- 删除客户仅隐藏档案，不删除历史买卖及结算。
ALTER TABLE business.biz_customer ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
COMMENT ON COLUMN business.biz_customer.deleted_at IS '客户删除时间，空表示正常；删除保留历史业务记录';
