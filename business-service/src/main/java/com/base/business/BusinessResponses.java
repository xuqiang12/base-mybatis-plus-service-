package com.base.business;

import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

public final class BusinessResponses {
    private BusinessResponses() {}

    @Getter @Setter
    public static class Customer {
        private String id;
        private String name;
        private String appellation;
        private String phone;
        private String remark;
        private Instant createdAt;
        private Instant updatedAt;
        private long fertilizerBalanceCents;
        private long grainBalanceCents;
        private long balanceCents;
        // 原始未结应收、应付分别返回，净额为零不能代替真正结清。
        private long receivableCents;
        private long payableCents;
        private boolean settled = true;
    }
}
