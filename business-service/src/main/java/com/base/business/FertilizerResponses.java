package com.base.business;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FertilizerResponses {
    private FertilizerResponses() {}
    public record Product(String id,String name,BigDecimal bagWeightKg,BigDecimal salePrice,
                          String brand,String specification,String remark) {}
    public record Account(String id,String name) {}
    public record SaleItem(String id,String productId,String productName,BigDecimal bagWeightKg,
                           BigDecimal quantity,String unit,BigDecimal unitPrice,long amountCents) {}
    public record Sale(String id,String customerId,LocalDate bizDate,String season,String remark,
                       long totalCents,long receivedCents,long offsetCents,long remainingCents,List<SaleItem> items) {}
}
