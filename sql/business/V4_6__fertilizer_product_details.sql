ALTER TABLE business.biz_product ADD COLUMN brand VARCHAR(50) NOT NULL DEFAULT '';
ALTER TABLE business.biz_product ADD COLUMN specification VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE business.biz_product ADD COLUMN remark VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE business.biz_product ALTER COLUMN sale_price DROP NOT NULL;

COMMENT ON COLUMN business.biz_product.brand IS '化肥品牌，选填';
COMMENT ON COLUMN business.biz_product.specification IS '化肥规格或养分配比，选填';
COMMENT ON COLUMN business.biz_product.remark IS '商品备注，选填';
COMMENT ON COLUMN business.biz_product.sale_price IS '每袋常用售价，未定价时为空，实际销售单价另行填写';
