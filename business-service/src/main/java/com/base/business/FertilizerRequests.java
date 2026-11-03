package com.base.business;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FertilizerRequests {
    private FertilizerRequests() {}

    @Getter @Setter
    public static class ProductFields {
        @NotBlank @Size(max=50) private String name;
        @Size(max=50) @JsonSetter(nulls=Nulls.SKIP) private String brand="";
        @Size(max=100) @JsonSetter(nulls=Nulls.SKIP) private String specification="";
        @Size(max=200) @JsonSetter(nulls=Nulls.SKIP) private String remark="";
        @NotNull @DecimalMin("0.001") @Digits(integer=6,fraction=3) private BigDecimal bagWeightKg;
        @DecimalMin("0.01") @Digits(integer=9,fraction=2) private BigDecimal salePrice;
    }
    @Getter @Setter
    public static class ProductCreate extends ProductFields {
        @NotBlank @Size(max=80) private String requestId;
    }
    @Getter @Setter
    public static class ProductUpdate extends ProductFields {
        @NotNull @Positive private Long id;
    }
    public record Id(@NotNull @Positive Long id) {}
    public record AccountCreate(@NotBlank @Size(max=30) String name) {}
    public record SaleItem(@NotNull @Positive Long productId,
                           @NotNull @DecimalMin("1") @Digits(integer=9,fraction=0) BigDecimal quantity,
                           @NotNull @DecimalMin("0.01") @Digits(integer=9,fraction=2) BigDecimal unitPrice) {}
    @Getter @Setter
    public static class SaleCreate {
        @NotBlank @Size(max=80) private String requestId;
        @NotNull @Positive private Long customerId;
        @NotNull private LocalDate bizDate;
        @NotEmpty @Size(max=100) @Valid private List<SaleItem> items;
        @Size(max=200) @JsonSetter(nulls=Nulls.SKIP) private String remark="";
    }
}
