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
import java.util.*;

@Service
@RequiredArgsConstructor
public class BusinessSettlementService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final FertilizerService fertilizer;
    private static final long MAX_CENTS=9007199254740991L;

    private static final String BILLS_SQL="""
        WITH fertilizer_bills AS (
          SELECT i.id,i.trade_id,'sale' AS kind,t.biz_date,i.product_name AS title,
            i.amount_cents AS total_cents,
            i.amount_cents-COALESCE((SELECT SUM(a.amount_cents)
              FROM business.biz_settlement_item a JOIN business.biz_settlement s
                ON s.owner_id=a.owner_id AND s.id=a.settlement_id AND s.status='posted'
              WHERE a.owner_id=i.owner_id AND a.trade_item_id=i.id),0) AS remaining_cents,
            CAST(NULL AS NUMERIC) AS weight,i.unit_price,i.quantity,i.unit
          FROM business.biz_trade_item i JOIN business.biz_trade t
            ON t.owner_id=i.owner_id AND t.id=i.trade_id
          WHERE i.owner_id=? AND i.customer_id=? AND i.category='fertilizer'
            AND t.direction='sale' AND t.status='posted'
        ), purchase_amounts AS (
          SELECT t.id,t.biz_date,MIN(i.product_name) AS title,SUM(i.amount_cents) AS total_cents,
            SUM(i.quantity) AS weight,MIN(i.unit_price) AS unit_price
          FROM business.biz_trade t JOIN business.biz_trade_item i
            ON i.owner_id=t.owner_id AND i.trade_id=t.id AND i.category='grain'
          WHERE t.owner_id=? AND t.customer_id=? AND t.direction='purchase' AND t.status='posted'
          GROUP BY t.id,t.biz_date
        ), allocations AS (
          SELECT i.trade_id,SUM(a.amount_cents) AS amount_cents
          FROM business.biz_settlement_item a
          JOIN business.biz_settlement s ON s.owner_id=a.owner_id AND s.id=a.settlement_id AND s.status='posted'
          JOIN business.biz_trade_item i ON i.owner_id=a.owner_id AND i.id=a.trade_item_id
          WHERE a.owner_id=? AND a.customer_id=? GROUP BY i.trade_id
        ), bills AS (
          SELECT id,trade_id,kind,biz_date,title,total_cents,remaining_cents,weight,unit_price,quantity,unit
          FROM fertilizer_bills
          UNION ALL
          SELECT t.id,t.id,'purchase',t.biz_date,t.title,t.total_cents,
            t.total_cents-COALESCE(a.amount_cents,0),t.weight,t.unit_price,
            CAST(NULL AS NUMERIC),CAST(NULL AS VARCHAR)
          FROM purchase_amounts t LEFT JOIN allocations a ON a.trade_id=t.id
          UNION ALL
          SELECT w.id,w.id,'weighing',w.biz_date,w.grain,CAST(NULL AS BIGINT),CAST(NULL AS BIGINT),
            (w.gross_weight_kg-w.tare_weight_kg)*2,CAST(NULL AS NUMERIC),CAST(NULL AS NUMERIC),CAST(NULL AS VARCHAR)
          FROM business.biz_grain_weighing w WHERE w.owner_id=? AND w.customer_id=? AND w.trade_id IS NULL
        )
        """;

    public BusinessSettlementResponses.BillPage bills(int owner,BusinessSettlementRequests.BillQuery query) {
        requireCustomer(owner,query.getCustomerId());
        String where=query.isSettled()?" WHERE remaining_cents=0":" WHERE kind='weighing' OR remaining_cents>0";
        Object[] args={owner,query.getCustomerId(),owner,query.getCustomerId(),owner,query.getCustomerId(),owner,query.getCustomerId()};
        var summary=jdbc.queryForMap(BILLS_SQL+"SELECT COUNT(*) AS total,COALESCE(SUM(CASE WHEN kind='sale' THEN remaining_cents ELSE 0 END),0) AS fertilizer,COALESCE(SUM(CASE WHEN kind<>'sale' THEN weight ELSE 0 END),0) AS weight FROM bills"+where,args);
        var pageArgs=new ArrayList<Object>(Arrays.asList(args));
        pageArgs.add(query.getPageSize());pageArgs.add((long)(query.getPageNum()-1)*query.getPageSize());
        var list=jdbc.query(BILLS_SQL+"SELECT * FROM bills"+where+" ORDER BY biz_date DESC,id DESC,kind LIMIT ? OFFSET ?",
                (r,i)->new BusinessSettlementResponses.Bill(r.getString("id"),r.getString("trade_id"),r.getString("kind"),r.getString("title"),r.getDate("biz_date").toLocalDate(),
                        r.getObject("total_cents")==null?null:r.getLong("total_cents"),r.getObject("remaining_cents")==null?null:r.getLong("remaining_cents"),r.getBigDecimal("weight"),r.getBigDecimal("unit_price"),r.getBigDecimal("quantity"),r.getString("unit"),List.of()),pageArgs.toArray());
        return new BusinessSettlementResponses.BillPage(list,((Number)summary.get("total")).longValue(),query.getPageNum(),query.getPageSize(),((Number)summary.get("fertilizer")).longValue(),(java.math.BigDecimal)summary.get("weight"));
    }

    public PageResult<FertilizerResponses.Sale> sales(int owner,GrainRequests.CustomerQuery query) {
        requireCustomer(owner,query.getCustomerId());
        String where="FROM business.biz_trade t WHERE t.owner_id=? AND t.customer_id=? AND t.direction='sale' AND t.status='posted' AND EXISTS (SELECT 1 FROM business.biz_trade_item i WHERE i.owner_id=t.owner_id AND i.trade_id=t.id AND i.category='fertilizer')";
        long total=jdbc.queryForObject("SELECT COUNT(*) "+where,Long.class,owner,query.getCustomerId());
        var ids=jdbc.queryForList("SELECT t.id "+where+" ORDER BY t.biz_date DESC,t.id DESC LIMIT ? OFFSET ?",Long.class,owner,query.getCustomerId(),query.getPageSize(),(long)(query.getPageNum()-1)*query.getPageSize());
        return new PageResult<>(ids.stream().map(id->fertilizer.sale(owner,id)).toList(),total,query.getPageNum(),query.getPageSize());
    }

    public PageResult<BusinessSettlementResponses.Settlement> history(int owner,BusinessSettlementRequests.HistoryQuery query) {
        requireCustomer(owner,query.getCustomerId());
        String where=" FROM business.biz_settlement s WHERE s.owner_id=? AND s.customer_id=? AND s.status='posted'";
        var args=new ArrayList<Object>(List.of(owner,query.getCustomerId()));
        if(query.getTradeId()!=null) {
            where+=" AND EXISTS (SELECT 1 FROM business.biz_settlement_item a JOIN business.biz_trade_item i ON i.owner_id=a.owner_id AND i.id=a.trade_item_id WHERE a.owner_id=s.owner_id AND a.settlement_id=s.id AND i.trade_id=?)";
            args.add(query.getTradeId());
        }
        long total=jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,args.toArray());
        args.add(query.getPageSize());args.add((long)(query.getPageNum()-1)*query.getPageSize());
        var ids=jdbc.queryForList("SELECT s.id"+where+" ORDER BY s.biz_date DESC,s.id DESC LIMIT ? OFFSET ?",Long.class,args.toArray());
        return new PageResult<>(ids.stream().map(id->settlement(owner,id)).toList(),total,query.getPageNum(),query.getPageSize());
    }

    @Transactional
    public BusinessSettlementResponses.Settlement create(int owner,BusinessSettlementRequests.Create request) {
        jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE id=? FOR UPDATE",Integer.class,owner);
        ObjectNode tree=mapper.valueToTree(request);tree.remove("requestId");String payload=tree.toString();
        var previous=jdbc.queryForList("SELECT id,request_payload FROM business.biz_settlement WHERE owner_id=? AND request_id=?",owner,request.getRequestId());
        if(!previous.isEmpty()) {
            if(!payload.equals(previous.getFirst().get("request_payload"))) throw new BusinessException(409,"该结算请求已使用，请重新提交");
            return settlement(owner,((Number)previous.getFirst().get("id")).longValue());
        }
        requireCustomer(owner,request.getCustomerId());
        String kind=request.getKind();
        boolean receive=kind.equals("receive"),pay=kind.equals("pay");
        if((receive && (request.getSaleIds().isEmpty() || !request.getPurchaseIds().isEmpty()))
                || (pay && (request.getPurchaseIds().isEmpty() || !request.getSaleIds().isEmpty()))
                || (kind.equals("offset") && (request.getSaleIds().isEmpty() || request.getPurchaseIds().isEmpty())))
            throw new BusinessException(400,"请选择与结算方式对应的未结账单");
        var sales=items(owner,request.getCustomerId(),request.getSaleIds(),"sale","fertilizer");
        var purchases=items(owner,request.getCustomerId(),request.getPurchaseIds(),"purchase","grain");
        long receivable=total(sales),payable=total(purchases);
        long amount=receive?receivable:pay?payable:Math.min(receivable,payable);
        if(amount<=0 || amount!=request.getAmountCents()) throw new BusinessException(409,"所选账单余额已变化，请刷新账单后重新结算");
        if(kind.equals("offset")) {
            if(request.getAccountId()!=null) throw new BusinessException(400,"抵账不产生实际收付款，无需选择账户");
        } else if(request.getAccountId()!=null && jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_account WHERE owner_id=? AND id=?",Long.class,owner,request.getAccountId())==0)
            throw new BusinessException(404,"请选择可用的生意收付款账户");
        if(pay && request.getBizDate()==null) throw new BusinessException(400,"请选择有效结算日期");
        var bizDate=pay?request.getBizDate():jdbc.queryForObject("SELECT CURRENT_DATE",java.sql.Date.class).toLocalDate();
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection->{
            var statement=connection.prepareStatement("INSERT INTO business.biz_settlement(owner_id,customer_id,kind,amount_cents,biz_date,created_by,account_id,request_id,request_payload) VALUES (?,?,?,?,?,?,?,?,?)",new String[]{"id"});
            Object[] args={owner,request.getCustomerId(),kind,amount,bizDate,owner,request.getAccountId(),request.getRequestId(),payload};
            for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);return statement;
        },keys);
        long id=Objects.requireNonNull(keys.getKey()).longValue();
        // 抵账分别分配同一个金额到应收与应付；不会生成 receive/pay 现金记录。
        if(!sales.isEmpty()) allocate(owner,request.getCustomerId(),id,sales,amount);
        if(!purchases.isEmpty()) allocate(owner,request.getCustomerId(),id,purchases,amount);
        return settlement(owner,id);
    }

    private List<Map<String,Object>> items(int owner,long customer,List<Long> ids,String direction,String category) {
        if(ids.isEmpty()) return List.of();
        if(new HashSet<>(ids).size()!=ids.size()) throw new BusinessException(400,"请勿重复选择同一张账单");
        String marks=String.join(",",Collections.nCopies(ids.size(),"?"));
        var args=new ArrayList<Object>(List.of(owner,customer,direction,category));args.addAll(ids);
        String filter=direction.equals("sale")?"i.id IN ("+marks+")":"t.id IN ("+marks+")";
        var rows=jdbc.queryForList("""
            SELECT i.id,i.trade_id,i.amount_cents-COALESCE((SELECT SUM(a.amount_cents)
              FROM business.biz_settlement_item a JOIN business.biz_settlement s ON s.id=a.settlement_id AND s.owner_id=a.owner_id
              WHERE a.owner_id=i.owner_id AND a.trade_item_id=i.id AND s.status='posted'),0) AS remaining
            FROM business.biz_trade_item i JOIN business.biz_trade t ON t.owner_id=i.owner_id AND t.id=i.trade_id
            WHERE t.owner_id=? AND t.customer_id=? AND t.direction=? AND t.status='posted' AND i.category=?
            """+" AND "+filter+" ORDER BY t.biz_date,t.id,i.id",args.toArray());
        var found=new HashSet<Long>();var unpaid=new HashSet<Long>();
        for(var row:rows) {
            long id=((Number)row.get(direction.equals("sale")?"id":"trade_id")).longValue();
            found.add(id);
            if(((Number)row.get("remaining")).longValue()>0) unpaid.add(id);
        }
        if(found.size()!=ids.size()) throw new BusinessException(404,"账单不存在或不属于该客户");
        if(unpaid.size()!=ids.size()) throw new BusinessException(409,"所选账单已结清，请刷新账单");
        return rows;
    }

    private long total(List<Map<String,Object>> rows) {
        long total=0;
        for(var row:rows) { long remaining=((Number)row.get("remaining")).longValue();if(remaining>MAX_CENTS-total)throw new BusinessException(400,"结算金额过大，请减少所选账单");total+=remaining; }
        return total;
    }
    private void allocate(int owner,long customer,long settlement,List<Map<String,Object>> items,long amount) {
        for(var item:items) {
            long allocated=Math.min(amount,((Number)item.get("remaining")).longValue());
            if(allocated>0) jdbc.update("INSERT INTO business.biz_settlement_item(owner_id,customer_id,settlement_id,trade_item_id,amount_cents) VALUES (?,?,?,?,?)",owner,customer,settlement,item.get("id"),allocated);
            amount-=allocated;
            if(amount==0) break;
        }
    }
    private BusinessSettlementResponses.Settlement settlement(int owner,long id) {
        var row=jdbc.queryForMap("SELECT s.*,a.name AS account_name FROM business.biz_settlement s LEFT JOIN business.biz_account a ON a.owner_id=s.owner_id AND a.id=s.account_id WHERE s.owner_id=? AND s.id=?",owner,id);
        var allocations=jdbc.query("SELECT t.id,t.direction,SUM(a.amount_cents) AS amount FROM business.biz_settlement_item a JOIN business.biz_trade_item i ON i.owner_id=a.owner_id AND i.id=a.trade_item_id JOIN business.biz_trade t ON t.owner_id=i.owner_id AND t.id=i.trade_id WHERE a.owner_id=? AND a.settlement_id=? GROUP BY t.id,t.direction ORDER BY t.id",(r,i)->new BusinessSettlementResponses.Allocation(r.getString("id"),r.getString("direction"),r.getLong("amount")),owner,id);
        return new BusinessSettlementResponses.Settlement(Long.toString(id),row.get("customer_id").toString(),row.get("kind").toString(),((Number)row.get("amount_cents")).longValue(),((java.sql.Date)row.get("biz_date")).toLocalDate(),(String)row.get("account_name"),allocations);
    }
    private void requireCustomer(int owner,long id) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_customer WHERE owner_id=? AND id=? AND deleted_at IS NULL",Long.class,owner,id)==0)throw new BusinessException(404,"客户不存在或无访问权限");
    }
}
