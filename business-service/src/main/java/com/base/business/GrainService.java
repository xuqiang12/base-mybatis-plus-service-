package com.base.business;

import com.base.common.exception.BusinessException;
import com.base.common.result.PageResult;
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
public class GrainService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private static final long MAX_CENTS=9007199254740991L;
    private static final String PURCHASE_SQL="""
        SELECT t.*,i.id AS item_id,i.product_name,i.quantity,i.unit_price,i.amount_cents,i.gross_weight_kg,i.tare_weight_kg,
          (SELECT COALESCE(SUM(a.amount_cents),0) FROM business.biz_settlement_item a
           JOIN business.biz_settlement s ON s.id=a.settlement_id AND s.owner_id=a.owner_id
           WHERE a.trade_item_id=i.id AND a.owner_id=i.owner_id AND s.status='posted' AND s.kind='pay') AS paid_cents,
          (SELECT COALESCE(SUM(a.amount_cents),0) FROM business.biz_settlement_item a
           JOIN business.biz_settlement s ON s.id=a.settlement_id AND s.owner_id=a.owner_id
           WHERE a.trade_item_id=i.id AND a.owner_id=i.owner_id AND s.status='posted' AND s.kind='offset') AS offset_cents
        FROM business.biz_trade t JOIN business.biz_trade_item i ON i.trade_id=t.id AND i.owner_id=t.owner_id
        WHERE t.owner_id=? AND t.direction='purchase' AND t.status='posted' AND i.category='grain'
        """;

    @Transactional
    public GrainResponses.Weighing createWeighing(int owner,GrainRequests.WeighingCreate request) {
        lockAccount(owner);
        String payload=payload(request);
        var previous=jdbc.queryForList("SELECT * FROM business.biz_grain_weighing WHERE owner_id=? AND request_id=?",owner,request.getRequestId());
        if(!previous.isEmpty()) {
            if(!payload.equals(previous.getFirst().get("request_payload"))) throw new BusinessException(409,"该称重请求已使用，请重新提交");
            return weighing(previous.getFirst());
        }
        requireCustomer(owner,request.getCustomerId());
        if(request.getGrossWeightKg().compareTo(request.getTareWeightKg())<=0)
            throw new BusinessException(400,"净重必须大于0，请核对毛重和皮重");
        long id=insert("INSERT INTO business.biz_grain_weighing(owner_id,customer_id,grain,gross_weight_kg,tare_weight_kg,biz_date,request_id,request_payload) VALUES (?,?,?,?,?,CURRENT_DATE,?,?)",
                owner,request.getCustomerId(),request.getGrain(),request.getGrossWeightKg(),request.getTareWeightKg(),request.getRequestId(),payload);
        return weighing(jdbc.queryForMap("SELECT * FROM business.biz_grain_weighing WHERE owner_id=? AND id=?",owner,id));
    }

    public GrainResponses.WeighingPage pendingWeighings(int owner,GrainRequests.CustomerQuery query) {
        requireCustomer(owner,query.getCustomerId());
        var summary=jdbc.queryForMap("SELECT COUNT(*) AS total,COALESCE(SUM(gross_weight_kg-tare_weight_kg),0) AS weight FROM business.biz_grain_weighing WHERE owner_id=? AND customer_id=? AND trade_id IS NULL",owner,query.getCustomerId());
        var rows=jdbc.queryForList("SELECT * FROM business.biz_grain_weighing WHERE owner_id=? AND customer_id=? AND trade_id IS NULL ORDER BY biz_date DESC,id DESC LIMIT ? OFFSET ?",
                owner,query.getCustomerId(),query.getPageSize(),(long)(query.getPageNum()-1)*query.getPageSize());
        return new GrainResponses.WeighingPage(rows.stream().map(this::weighing).toList(),((Number)summary.get("total")).longValue(),query.getPageNum(),query.getPageSize(),(BigDecimal)summary.get("weight"));
    }

    public List<GrainResponses.TareHistory> tareHistory(int owner,long customerId) {
        requireCustomer(owner,customerId);
        var rows=jdbc.queryForList("""
                SELECT w.customer_id,c.name,w.tare_weight_kg,w.biz_date AS last_used_date
                FROM business.biz_grain_weighing w JOIN business.biz_customer c ON c.owner_id=w.owner_id AND c.id=w.customer_id
                WHERE w.owner_id=? AND w.customer_id=?
                ORDER BY w.id DESC
                LIMIT 1
                """,owner,customerId);
        return rows.stream().map(row->new GrainResponses.TareHistory(row.get("customer_id").toString(),row.get("name").toString(),
                (BigDecimal)row.get("tare_weight_kg"),((java.sql.Date)row.get("last_used_date")).toLocalDate())).toList();
    }

    @Transactional
    public GrainResponses.Purchase createPurchase(int owner,GrainRequests.PurchaseCreate request) {
        lockAccount(owner);
        String payload=payload(request);
        var previous=jdbc.queryForList("SELECT id,request_payload FROM business.biz_trade WHERE owner_id=? AND request_id=?",owner,request.getRequestId());
        if(!previous.isEmpty()) {
            if(!payload.equals(previous.getFirst().get("request_payload"))) throw new BusinessException(409,"该计价请求已使用，请重新提交");
            return purchase(owner,((Number)previous.getFirst().get("id")).longValue());
        }
        requireCustomer(owner,request.getCustomerId());
        if(new HashSet<>(request.getWeighingIds()).size()!=request.getWeighingIds().size()) throw new BusinessException(400,"请勿重复选择同一车称重记录");
        String marks=String.join(",",Collections.nCopies(request.getWeighingIds().size(),"?"));
        var args=new ArrayList<Object>();args.add(owner);args.add(request.getCustomerId());args.addAll(request.getWeighingIds());
        var rows=jdbc.queryForList("SELECT * FROM business.biz_grain_weighing WHERE owner_id=? AND customer_id=? AND id IN ("+marks+") ORDER BY id",args.toArray());
        if(rows.size()!=request.getWeighingIds().size()) throw new BusinessException(404,"称重记录不存在或不属于该客户");
        if(rows.stream().anyMatch(row->row.get("trade_id")!=null)) throw new BusinessException(409,"所选称重记录已计价，请刷新后重新选择");
        String grain=rows.getFirst().get("grain").toString();
        if(rows.stream().anyMatch(row->!grain.equals(row.get("grain")))) throw new BusinessException(400,"不同粮食品种请分开计价");
        BigDecimal net=rows.stream().map(row->((BigDecimal)row.get("gross_weight_kg")).subtract((BigDecimal)row.get("tare_weight_kg"))).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal weight=net.multiply(BigDecimal.valueOf(2));
        long trade=createPricedTrade(owner,request.getCustomerId(),grain,weight,request.getUnitPrice(),request.getRemark().trim(),request.getRequestId(),payload);
        for(long id:request.getWeighingIds()) jdbc.update("UPDATE business.biz_grain_weighing SET trade_id=? WHERE owner_id=? AND id=? AND trade_id IS NULL",trade,owner,id);
        return purchase(owner,trade);
    }

    @Transactional
    public GrainResponses.Purchase repricePurchase(int owner,GrainRequests.PurchaseReprice request) {
        lockAccount(owner);
        String payload=payload(request);
        var previous=jdbc.queryForList("SELECT id,request_payload FROM business.biz_trade WHERE owner_id=? AND request_id=?",owner,request.getRequestId());
        if(!previous.isEmpty()) {
            if(!payload.equals(previous.getFirst().get("request_payload"))) throw new BusinessException(409,"该计价请求已使用，请重新提交");
            return purchase(owner,((Number)previous.getFirst().get("id")).longValue());
        }
        requireCustomer(owner,request.getCustomerId());
        if(new HashSet<>(request.getWeighingIds()).size()!=request.getWeighingIds().size()
                || new HashSet<>(request.getPurchaseIds()).size()!=request.getPurchaseIds().size())
            throw new BusinessException(400,"请勿重复选择同一张账单或称重记录");
        String weighingMarks=String.join(",",Collections.nCopies(request.getWeighingIds().size(),"?"));
        var args=new ArrayList<Object>(List.of(owner,request.getCustomerId()));args.addAll(request.getWeighingIds());
        var weighings=jdbc.queryForList("SELECT * FROM business.biz_grain_weighing WHERE owner_id=? AND customer_id=? AND id IN ("+weighingMarks+") ORDER BY id",args.toArray());
        if(weighings.size()!=request.getWeighingIds().size()) throw new BusinessException(404,"称重记录不存在或不属于该客户");
        if(weighings.stream().anyMatch(row->row.get("trade_id")!=null)) throw new BusinessException(409,"所选称重记录已计价，请刷新后重新选择");
        String purchaseMarks=String.join(",",Collections.nCopies(request.getPurchaseIds().size(),"?"));
        args=new ArrayList<Object>(List.of(owner,request.getCustomerId()));args.addAll(request.getPurchaseIds());
        var originals=jdbc.queryForList(PURCHASE_SQL+" AND t.customer_id=? AND t.id IN ("+purchaseMarks+") ORDER BY t.id",args.toArray());
        if(originals.size()!=request.getPurchaseIds().size()) throw new BusinessException(409,"所选粮款不存在或状态已变化，请刷新后重新选择");
        if(originals.stream().anyMatch(row->((Number)row.get("paid_cents")).longValue()>0 || ((Number)row.get("offset_cents")).longValue()>0))
            throw new BusinessException(409,"已发生付款或抵账的粮款不能重新计价");
        String grain=weighings.getFirst().get("grain").toString();
        if(weighings.stream().anyMatch(row->!grain.equals(row.get("grain"))) || originals.stream().anyMatch(row->!grain.equals(row.get("product_name"))))
            throw new BusinessException(400,"不同粮食品种请分开计价");
        BigDecimal weight=weighings.stream().map(row->((BigDecimal)row.get("gross_weight_kg")).subtract((BigDecimal)row.get("tare_weight_kg")).multiply(BigDecimal.valueOf(2))).reduce(BigDecimal.ZERO,BigDecimal::add);
        weight=weight.add(originals.stream().map(row->(BigDecimal)row.get("quantity")).reduce(BigDecimal.ZERO,BigDecimal::add));
        long trade=createPricedTrade(owner,request.getCustomerId(),grain,weight,request.getUnitPrice(),"",request.getRequestId(),payload);
        // 旧单保留原价及原始称重关联，仅退出当前待付统计；后续通过新单追溯。
        for(long id:request.getPurchaseIds()) jdbc.update("UPDATE business.biz_trade SET status='void',repriced_into_id=? WHERE owner_id=? AND id=?",trade,owner,id);
        for(long id:request.getWeighingIds()) jdbc.update("UPDATE business.biz_grain_weighing SET trade_id=? WHERE owner_id=? AND id=?",trade,owner,id);
        return purchase(owner,trade);
    }

    private long createPricedTrade(int owner,long customer,String grain,BigDecimal weight,BigDecimal price,String remark,String requestId,String payload) {
        BigDecimal cents=weight.multiply(price).movePointRight(2).setScale(0,RoundingMode.HALF_UP);
        if(cents.signum()<=0 || cents.compareTo(BigDecimal.valueOf(MAX_CENTS))>0) throw new BusinessException(400,"粮款金额应至少为0.01元且不超过可记录上限");
        var date=jdbc.queryForObject("SELECT CURRENT_DATE",java.sql.Date.class).toLocalDate();
        String season=date.getYear()+"年"+(date.getMonthValue()<=6?"春耕账期":"秋耕账期");
        long trade=insert("INSERT INTO business.biz_trade(owner_id,customer_id,direction,biz_date,season,created_by,remark,request_id,request_payload) VALUES (?,?,'purchase',?,?,?,?,?,?)",
                owner,customer,date,season,owner,remark,requestId,payload);
        insert("INSERT INTO business.biz_trade_item(owner_id,customer_id,trade_id,category,product_name,quantity,unit,unit_price,amount_cents) VALUES (?,?,?,'grain',?,?,'斤',?,?)",
                owner,customer,trade,grain,weight,price,cents.longValueExact());
        return trade;
    }

    public PageResult<GrainResponses.Purchase> purchases(int owner,GrainRequests.CustomerQuery query) {
        requireCustomer(owner,query.getCustomerId());
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_trade t WHERE t.owner_id=? AND t.customer_id=? AND t.direction='purchase' AND t.status='posted' AND EXISTS (SELECT 1 FROM business.biz_trade_item i WHERE i.owner_id=t.owner_id AND i.trade_id=t.id AND i.category='grain')",Long.class,owner,query.getCustomerId());
        var rows=jdbc.queryForList(PURCHASE_SQL+" AND t.customer_id=? ORDER BY t.biz_date DESC,t.id DESC LIMIT ? OFFSET ?",owner,query.getCustomerId(),query.getPageSize(),(long)(query.getPageNum()-1)*query.getPageSize());
        return new PageResult<>(rows.stream().map(row->purchase(row,List.of(),List.of())).toList(),total,query.getPageNum(),query.getPageSize());
    }

    public GrainResponses.Purchase purchase(int owner,long id) {
        var rows=jdbc.queryForList(PURCHASE_SQL.replace("t.status='posted'","(t.status='posted' OR t.repriced_into_id IS NOT NULL)")+" AND t.id=?",owner,id);
        if(rows.isEmpty()) throw new BusinessException(404,"收粮计价单不存在或无访问权限");
        var weights=jdbc.queryForList("""
            WITH RECURSIVE sources(id) AS (
              SELECT id FROM business.biz_trade WHERE owner_id=? AND id=?
              UNION ALL
              SELECT t.id FROM business.biz_trade t JOIN sources s ON t.repriced_into_id=s.id WHERE t.owner_id=?
            )
            SELECT w.* FROM business.biz_grain_weighing w JOIN sources s ON s.id=w.trade_id WHERE w.owner_id=? ORDER BY w.id
            """,owner,id,owner,owner);
        var sources=jdbc.query("SELECT t.id,t.biz_date,i.quantity,i.unit_price,i.amount_cents FROM business.biz_trade t JOIN business.biz_trade_item i ON i.owner_id=t.owner_id AND i.trade_id=t.id WHERE t.owner_id=? AND t.repriced_into_id=? ORDER BY t.id",
                (r,i)->new GrainResponses.SourcePricing(r.getString("id"),r.getBigDecimal("quantity"),r.getBigDecimal("unit_price"),r.getLong("amount_cents"),r.getDate("biz_date").toLocalDate()),owner,id);
        return purchase(rows.getFirst(),weights.stream().map(this::weighing).toList(),sources);
    }

    private GrainResponses.Weighing weighing(Map<String,Object> row) {
        BigDecimal gross=(BigDecimal)row.get("gross_weight_kg"),tare=(BigDecimal)row.get("tare_weight_kg"),net=gross.subtract(tare);
        return new GrainResponses.Weighing(row.get("id").toString(),row.get("customer_id").toString(),row.get("grain").toString(),gross,tare,net,net.multiply(BigDecimal.valueOf(2)),
                ((java.sql.Date)row.get("biz_date")).toLocalDate(),row.get("remark").toString(),row.get("trade_id")==null?null:row.get("trade_id").toString());
    }

    private GrainResponses.Purchase purchase(Map<String,Object> row,List<GrainResponses.Weighing> weights,List<GrainResponses.SourcePricing> sources) {
        long total=((Number)row.get("amount_cents")).longValue(),paid=((Number)row.get("paid_cents")).longValue(),offset=((Number)row.get("offset_cents")).longValue();
        BigDecimal weight=(BigDecimal)row.get("quantity");
        return new GrainResponses.Purchase(row.get("id").toString(),row.get("customer_id").toString(),row.get("product_name").toString(),(BigDecimal)row.get("gross_weight_kg"),(BigDecimal)row.get("tare_weight_kg"),
                weight.divide(BigDecimal.valueOf(2)),weight,(BigDecimal)row.get("unit_price"),((java.sql.Date)row.get("biz_date")).toLocalDate(),row.get("season").toString(),row.get("remark").toString(),total,paid,offset,total-paid-offset,weights,sources,
                row.get("repriced_into_id")==null?null:row.get("repriced_into_id").toString());
    }

    private void lockAccount(int owner) { jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE id=? FOR UPDATE",Integer.class,owner); }
    private void requireCustomer(int owner,long id) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_customer WHERE owner_id=? AND id=? AND deleted_at IS NULL",Long.class,owner,id)==0) throw new BusinessException(404,"客户不存在或已删除，请重新选择");
    }
    private String payload(Object request) { ObjectNode tree=mapper.valueToTree(request);tree.remove("requestId");return tree.toString(); }
    private long insert(String sql,Object...args) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement(sql,new String[]{"id"});for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
