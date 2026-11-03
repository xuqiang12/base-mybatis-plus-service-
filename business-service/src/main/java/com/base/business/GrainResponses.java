package com.base.business;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class GrainResponses {
    private GrainResponses() {}

    public record TareHistory(String customerId,String customerName,BigDecimal tareWeightKg,LocalDate lastUsedDate) {}
    public record Weighing(String id,String customerId,String grain,BigDecimal grossWeightKg,BigDecimal tareWeightKg,
                           BigDecimal netWeightKg,BigDecimal weight,LocalDate bizDate,String remark,String purchaseId) {}
    public record WeighingPage(List<Weighing> list,long total,int pageNum,int pageSize,BigDecimal pendingNetWeightKg) {}

    public record SourcePricing(String id,BigDecimal weight,BigDecimal unitPrice,long totalCents,LocalDate bizDate) {}

    public record Purchase(String id,String customerId,String grain,BigDecimal grossWeightKg,BigDecimal tareWeightKg,
                           BigDecimal netWeightKg,BigDecimal weight,BigDecimal unitPrice,
                           LocalDate bizDate,String season,String remark,long totalCents,long paidCents,long offsetCents,long remainingCents,
                           List<Weighing> weighings,List<SourcePricing> sourcePricings,String repricedIntoId) {}
}
