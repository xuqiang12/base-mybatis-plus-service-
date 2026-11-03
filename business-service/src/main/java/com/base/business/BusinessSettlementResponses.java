package com.base.business;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public final class BusinessSettlementResponses {
    private BusinessSettlementResponses() {}
    public record Allocation(String tradeId,String direction,long amountCents) {}
    public record Settlement(String id,String customerId,String kind,long amountCents,LocalDate bizDate,
                             String accountName,List<Allocation> allocations) {}
    public record BillItem(String id,String productName,BigDecimal quantity,String unit,long remainingCents) {}
    public record Bill(String id,String tradeId,String kind,String title,LocalDate bizDate,Long totalCents,Long remainingCents,
                       BigDecimal weight,BigDecimal unitPrice,BigDecimal quantity,String unit,List<BillItem> items) {}
    public record BillPage(List<Bill> list,long total,int pageNum,int pageSize,long fertilizerCents,BigDecimal grainWeight) {}
}
