package com.base.business;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

public final class BusinessRequests {
    private BusinessRequests() {}

    @Getter @Setter
    public static class CustomerFields {
        @NotBlank @Size(max = 30) private String name;
        @Size(max = 30) @JsonSetter(nulls = Nulls.SKIP) private String appellation = "";
        @Size(max = 30) @JsonSetter(nulls = Nulls.SKIP) private String phone = "";
        @Size(max = 200) @JsonSetter(nulls = Nulls.SKIP) private String remark = "";
    }

    @Getter @Setter
    public static class CustomerCreate extends CustomerFields {
        @NotBlank @Size(max = 80) private String requestId;
    }

    @Getter @Setter
    public static class CustomerUpdate extends CustomerFields {
        @NotNull @Positive private Long id;
    }

    @Getter @Setter
    public static class CustomerDelete {
        @NotNull @Positive private Long id;
    }

    @Getter @Setter
    public static class CustomerQuery {
        @Size(max = 60) private String keyword;
        @Min(1) private int pageNum = 1;
        @Min(1) @Max(100) private int pageSize = 20;
    }
}
