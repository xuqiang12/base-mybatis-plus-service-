package com.base.business;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.List;

public final class BusinessSettlementRequests {
    private BusinessSettlementRequests() {}

    @Getter @Setter
    public static class BillQuery extends GrainRequests.CustomerQuery {
        private boolean settled;
    }

    @Getter @Setter
    public static class HistoryQuery extends GrainRequests.CustomerQuery {
        @Positive private Long tradeId;
    }

    @Getter @Setter
    public static class Create {
        @NotBlank @Size(max=80) private String requestId;
        @NotNull @Positive private Long customerId;
        @NotBlank @Pattern(regexp="receive|pay|offset") private String kind;
        @NotNull @Size(max=1000) private List<@NotNull @Positive Long> saleIds=List.of();
        @NotNull @Size(max=1000) private List<@NotNull @Positive Long> purchaseIds=List.of();
        @NotNull @Min(1) @Max(9007199254740991L) private Long amountCents;
        @Positive private Long accountId;
        private LocalDate bizDate;
    }
}
