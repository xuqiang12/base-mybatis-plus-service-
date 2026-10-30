package com.base.system.modules.ledger.dto;

import com.base.common.exception.BusinessException;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.YearMonth;

/** 独立查询的筛选与分页；所有值仍由 SQL 参数绑定。 */
public final class LedgerQueries {
    private LedgerQueries() {}
    @Data
    public static class Page {
        @Min(1) private int pageNum = 1;
        @Min(1) @Max(100) private int pageSize = 20;
        public long offset() { return ((long) pageNum - 1) * pageSize; }
    }
    @Data
    public static class Records extends Page {
        private String period;
        @Positive private Long friendId;
        @Positive private Long bookId;
        @Positive private Long categoryId;
        @Pattern(regexp = "expense|income|saving", message = "请选择有效收支类型") private String type;
        private String direction;
        @Pattern(regexp = "gift", message = "请选择有效来源") private String source;
    }
    @Data
    public static class Friends extends Page { private String name; }
    @Data
    public static class History extends Page {
        @NotBlank private String familyId;
    }
    @Data
    public static class LoanSummary extends Page {
        @NotNull @Pattern(regexp = "receivable|payable", message = "请选择有效借贷方向") private String side;
    }
    /** 不修改客户端日期边界；期间转换仅供数据库范围筛选。 */
    public static LocalDate start(String period) {
        if (period == null) return null;
        try {
            if (period.matches("[0-9]{4}")) return LocalDate.of(Integer.parseInt(period), 1, 1);
            if (period.matches("[0-9]{4}-[0-9]{2}")) return YearMonth.parse(period).atDay(1);
        } catch (java.time.DateTimeException ignored) { }
        throw new BusinessException(400, "请选择有效期间");
    }
    public static LocalDate end(String period) {
        LocalDate first = start(period);
        return first == null ? null : period.length() == 4 ? first.plusYears(1) : first.plusMonths(1);
    }
}
