package com.base.business;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class GrainRequests {
    private GrainRequests() {}

    @Getter @Setter
    public static class WeighingCreate {
        @NotBlank @Size(max=80) private String requestId;
        @NotNull @Positive private Long customerId;
        @NotBlank @Pattern(regexp="小麦|水稻") private String grain;
        @NotNull @DecimalMin("0.001") @Digits(integer=9,fraction=3) private BigDecimal grossWeightKg;
        @NotNull @DecimalMin("0") @Digits(integer=9,fraction=3) private BigDecimal tareWeightKg;
    }

    @Getter @Setter
    public static class PurchaseCreate {
        @NotBlank @Size(max=80) private String requestId;
        @NotNull @Positive private Long customerId;
        @NotEmpty @Size(max=1000) private List<@NotNull @Positive Long> weighingIds;
        @NotNull @DecimalMin("0.0001") @Digits(integer=6,fraction=4) private BigDecimal unitPrice;
        private LocalDate bizDate;
        @Size(max=200) @JsonSetter(nulls=Nulls.SKIP) private String remark="";
    }

    @Getter @Setter
    public static class PurchaseReprice {
        @NotBlank @Size(max=80) private String requestId;
        @NotNull @Positive private Long customerId;
        @NotEmpty @Size(max=1000) private List<@NotNull @Positive Long> weighingIds;
        @NotEmpty @Size(max=1000) private List<@NotNull @Positive Long> purchaseIds;
        @NotNull @DecimalMin("0.0001") @Digits(integer=6,fraction=4) private BigDecimal unitPrice;
    }

    @Getter @Setter
    public static class CustomerQuery {
        @NotNull @Positive private Long customerId;
        @Min(1) private int pageNum=1;
        @Min(1) @Max(100) private int pageSize=20;
    }

}
