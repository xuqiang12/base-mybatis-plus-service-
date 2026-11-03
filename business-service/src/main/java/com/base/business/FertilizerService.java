package com.base.business;

import com.base.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FertilizerService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private static final long MAX_CENTS=9007199254740991L;

    public List<FertilizerResponses.Product> products(int owner) {
        return jdbc.query("SELECT * FROM business.biz_product WHERE owner_id=? AND deleted_at IS NULL ORDER BY id",
                (r,i)->new FertilizerResponses.Product(r.getString("id"),r.getString("name"),r.getBigDecimal("bag_weight_kg"),r.getBigDecimal("sale_price"),r.getString("brand"),r.getString("specification"),r.getString("remark")),owner);
    }

    @Transactional
    public FertilizerResponses.Product createProduct(int owner,FertilizerRequests.ProductCreate request) {
        lockOwner(owner);
        String payload=payload(request);
        var previous=jdbc.queryForList("SELECT id,request_payload,deleted_at FROM business.biz_product WHERE owner_id=? AND request_id=?",owner,request.getRequestId());
        if(!previous.isEmpty()) {
            var row=previous.getFirst();
            if(row.get("deleted_at")!=null || !payload.equals(row.get("request_payload"))) throw new BusinessException(409,"该新增请求已使用，请重新操作");
            return product(owner,((Number)row.get("id")).longValue());
        }
        long id=insert("INSERT INTO business.biz_product(owner_id,name,bag_weight_kg,sale_price,brand,specification,remark,request_id,request_payload) VALUES (?,?,?,?,?,?,?,?,?)",
                owner,request.getName().trim(),request.getBagWeightKg(),request.getSalePrice(),request.getBrand().trim(),request.getSpecification().trim(),request.getRemark().trim(),request.getRequestId(),payload);
        return product(owner,id);
    }

    @Transactional
    public FertilizerResponses.Product updateProduct(int owner,FertilizerRequests.ProductUpdate request) {
        lockOwner(owner); product(owner,request.getId());
        jdbc.update("UPDATE business.biz_product SET name=?,bag_weight_kg=?,sale_price=?,brand=?,specification=?,remark=? WHERE owner_id=? AND id=? AND deleted_at IS NULL",
                request.getName().trim(),request.getBagWeightKg(),request.getSalePrice(),request.getBrand().trim(),request.getSpecification().trim(),request.getRemark().trim(),owner,request.getId());
        return product(owner,request.getId());
    }

    @Transactional
    public void deleteProduct(int owner,long id) {
        lockOwner(owner);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_product WHERE owner_id=? AND id=?",Long.class,owner,id)==0) throw new BusinessException(404,"商品不存在或无访问权限");
        jdbc.update("UPDATE business.biz_product SET deleted_at=CURRENT_TIMESTAMP WHERE owner_id=? AND id=? AND deleted_at IS NULL",owner,id);
    }

    public List<FertilizerResponses.Account> accounts(int owner) {
        return jdbc.query("SELECT id,name FROM business.biz_account WHERE owner_id=? ORDER BY id",
                (r,i)->new FertilizerResponses.Account(r.getString("id"),r.getString("name")),owner);
    }

    @Transactional
    public FertilizerResponses.Account createAccount(int owner,String name) {
        lockOwner(owner);
        var old=accounts(owner).stream().filter(a->a.name().equals(name.trim())).findFirst();
        if(old.isPresent()) return old.get();
        long id=insert("INSERT INTO business.biz_account(owner_id,name) VALUES (?,?)",owner,name.trim());
        return new FertilizerResponses.Account(Long.toString(id),name.trim());
    }

    @Transactional
    public FertilizerResponses.Sale createSale(int owner,FertilizerRequests.SaleCreate request) {
        // 与客户删改、商品删改共用账号锁；重试先查原结果，避免档案后来改变导致已成功请求无法重试。
        lockOwner(owner);
        String payload=payload(request);
        var previous=jdbc.queryForList("SELECT id,request_payload FROM business.biz_trade WHERE owner_id=? AND request_id=?",owner,request.getRequestId());
        if(!previous.isEmpty()) {
            var row=previous.getFirst();
            if(!payload.equals(row.get("request_payload"))) throw new BusinessException(409,"该保存请求已使用，请重新提交");
            return sale(owner,((Number)row.get("id")).longValue());
        }
        if(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_customer WHERE owner_id=? AND id=? AND deleted_at IS NULL",Long.class,owner,request.getCustomerId())==0)
            throw new BusinessException(404,"客户不存在或已删除，请重新选择");
        var selected=new ArrayList<FertilizerResponses.Product>();
        var amounts=new ArrayList<Long>();
        var ids=new HashSet<Long>();
        long total=0;
        for(var item:request.getItems()) {
            if(!ids.add(item.productId())) throw new BusinessException(400,"同一商品请合并数量");
            selected.add(product(owner,item.productId()));
            BigDecimal cents=item.quantity().multiply(item.unitPrice()).movePointRight(2).setScale(0,RoundingMode.HALF_UP);
            if(cents.compareTo(BigDecimal.valueOf(MAX_CENTS-total))>0) throw new BusinessException(400,"成交金额过大，请调整数量或单价");
            long amount=cents.longValueExact(); amounts.add(amount); total+=amount;
        }
        String season=request.getBizDate().getYear()+"年"+(request.getBizDate().getMonthValue()<=6?"春耕账期":"秋耕账期");
        long trade=insert("INSERT INTO business.biz_trade(owner_id,customer_id,direction,biz_date,season,created_by,remark,request_id,request_payload) VALUES (?,?,'sale',?,?,?,?,?,?)",
                owner,request.getCustomerId(),request.getBizDate(),season,owner,request.getRemark().trim(),request.getRequestId(),payload);
        for(int i=0;i<request.getItems().size();i++) {
            var item=request.getItems().get(i); var p=selected.get(i); long amount=amounts.get(i);
            insert("INSERT INTO business.biz_trade_item(owner_id,customer_id,trade_id,category,product_id,product_name,quantity,unit,unit_price,bag_weight_kg,amount_cents) VALUES (?,?,?,'fertilizer',?,?,?,'袋',?,?,?)",
                    owner,request.getCustomerId(),trade,item.productId(),p.name(),item.quantity(),item.unitPrice(),p.bagWeightKg(),amount);

        }
        return sale(owner,trade);
    }

    public FertilizerResponses.Sale sale(int owner,long id) {
        var headers=jdbc.queryForList("SELECT * FROM business.biz_trade WHERE owner_id=? AND id=? AND direction='sale' AND status='posted' AND EXISTS (SELECT 1 FROM business.biz_trade_item i WHERE i.owner_id=business.biz_trade.owner_id AND i.trade_id=business.biz_trade.id AND i.category='fertilizer')",owner,id);
        if(headers.isEmpty()) throw new BusinessException(404,"销售单不存在或无访问权限");
        var h=headers.getFirst();
        var items=jdbc.query("SELECT * FROM business.biz_trade_item WHERE owner_id=? AND trade_id=? ORDER BY id",
                (r,i)->new FertilizerResponses.SaleItem(r.getString("id"),r.getString("product_id"),r.getString("product_name"),r.getBigDecimal("bag_weight_kg"),r.getBigDecimal("quantity"),r.getString("unit"),r.getBigDecimal("unit_price"),r.getLong("amount_cents")),owner,id);
        long total=items.stream().mapToLong(FertilizerResponses.SaleItem::amountCents).sum();
        var settled=jdbc.queryForMap("SELECT COALESCE(SUM(CASE WHEN s.kind='receive' THEN a.amount_cents ELSE 0 END),0) AS received,COALESCE(SUM(CASE WHEN s.kind='offset' THEN a.amount_cents ELSE 0 END),0) AS offset_amount FROM business.biz_settlement_item a JOIN business.biz_settlement s ON s.id=a.settlement_id AND s.owner_id=a.owner_id JOIN business.biz_trade_item i ON i.id=a.trade_item_id AND i.owner_id=a.owner_id WHERE a.owner_id=? AND i.trade_id=? AND s.status='posted'",owner,id);
        long received=((Number)settled.get("received")).longValue(),offset=((Number)settled.get("offset_amount")).longValue();
        return new FertilizerResponses.Sale(Long.toString(id),h.get("customer_id").toString(),((java.sql.Date)h.get("biz_date")).toLocalDate(),h.get("season").toString(),h.get("remark").toString(),total,received,offset,total-received-offset,items);
    }

    private FertilizerResponses.Product product(int owner,long id) {
        var rows=jdbc.query("SELECT * FROM business.biz_product WHERE owner_id=? AND id=? AND deleted_at IS NULL",
                (r,i)->new FertilizerResponses.Product(r.getString("id"),r.getString("name"),r.getBigDecimal("bag_weight_kg"),r.getBigDecimal("sale_price"),r.getString("brand"),r.getString("specification"),r.getString("remark")),owner,id);
        if(rows.isEmpty()) throw new BusinessException(404,"商品不存在或已删除，请重新选择");
        return rows.getFirst();
    }

    private void lockOwner(int owner) { jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE id=? FOR UPDATE",Integer.class,owner); }
    private String payload(Object request) { ObjectNode tree=mapper.valueToTree(request); tree.remove("requestId"); return tree.toString(); }
    private long insert(String sql,Object...args) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement(sql,new String[]{"id"});for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);return statement;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
