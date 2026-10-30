package com.base.system.modules.ledger.dto;

import com.base.system.modules.friend.dto.FriendResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

/** 按使用处返回的查询模型，不附带无关集合。 */
public final class LedgerReadResponses {
    private LedgerReadResponses() {}
    @Data @EqualsAndHashCode(callSuper = true)
    public static class Household extends LedgerResponses.Household {
        private String categoryName;
        private String categoryIcon;
        private Boolean categoryActive;
        private Boolean categoryDeleted;
        private String projectName;
        private String giftName;
        private String giftAppellation;
        private String giftEventType;
        private String bookName;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class Gift extends LedgerResponses.Gift {
        private String bookName;
        private String projectName;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class Loan extends LedgerResponses.Loan {
        private String projectName;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class Book extends LedgerResponses.Book {
        private long recordCount;
        private long receivedCents;
    }
    @Data
    public static class HouseholdPage {
        private List<Household> list;
        private long total;
        private long pageNum;
        private long pageSize;
        private List<MonthSummary> monthSummaries;
    }
    @Data
    public static class HouseholdSummary {
        private long count;
        private long incomeCents;
        private long expenseCents;
        private long savingCents;
        private long balanceCents;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class MonthSummary extends HouseholdSummary { private String month; }
    @Data
    public static class CategorySummary {
        private String type;
        private Long categoryId;
        private String source;
        private String categoryName;
        private String categoryIcon;
        private long amountCents;
        private long count;
    }
    @Data
    public static class Statistics {
        private HouseholdSummary summary;
        private List<MonthSummary> months;
        private List<CategorySummary> categories;
    }
    @Data
    public static class GiftSummary {
        private long receivedCents;
        private long sentCents;
        private long receivedCount;
        private long sentCount;
        private long balanceCents;
        private LocalDate lastDate;
    }
    @Data
    public static class LoanTotals {
        private long receivableCents;
        private long payableCents;
        private long count;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class FriendSummary extends FriendResponse {
        private long receivedCents;
        private long sentCents;
        private long receivedCount;
        private long sentCount;
        private long balanceCents;
        private LocalDate lastDate;
        private long receivableCents;
        private long payableCents;
        private long count;
    }
    @Data
    public static class Recent {
        private Long projectId;
        private String kind;
        private Long id;
        private String title;
        private String date;
        private long amountCents;
        private String direction;
        private String type;
        private String eventType;
        private String name;
        private String appellation;
        private String categoryName;
        private String note;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
