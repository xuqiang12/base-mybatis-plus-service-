package com.base.system.modules.ledger.dto;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 礼账返回模型。 */
public final class LedgerResponses {
    private LedgerResponses() {}

    @Data
    public static class Book {
        private Long id;
        private String name;
        private LocalDate date;
        private String note;
    }

    @Data
    public static class Gift {
        private Long projectId;
        private Long id;
        private String eventType;
        private String direction;
        private LocalDate date;
        private String name;
        private String appellation;
        private Long amountCents;
        private String note;
        private Long bookId;
        private Long friendId;
        private String createdBy;
        private String createdByName;
        private Instant createdAt;
    }

    @Data
    public static class Loan {
        private Long projectId;
        private Long id;
        private String direction;
        private Long friendId;
        private String name;
        private String appellation;
        private Long amountCents;
        private LocalDate date;
        private String note;
        private String createdBy;
        private String createdByName;
        private Instant createdAt;
    }

    @Data
    public static class Category {
        private Long id;
        private String type;
        private String name;
        private String icon;
        private Boolean active;
    }

    @Data
    public static class Household {
        private Long giftId;
        private Long loanId;
        private String savingEffect;
        private Long projectId;
        private String projectType;
        private Long id;
        private String type;
        private Long categoryId;
        private Long amountCents;
        private String date;
        private String note;
        private String createdBy;
        private String createdByName;
    }

    @Data
    public static class CreatedGift {
        private Gift record;
        private Household householdRecord;
        private Category householdCategory;
        private com.base.system.modules.friend.dto.FriendResponse friend;
    }

    @Data
    public static class CreatedLoan {
        private Loan record;
        private Household householdRecord;
        private com.base.system.modules.friend.dto.FriendResponse friend;
    }

    @Data
    public static class Month {
        private Long id;
        private String month;
        private List<Household> items;
    }

    @Data
    public static class DataSet {
        private List<Book> books;
        private List<com.base.system.modules.friend.dto.FriendResponse> friends;
        private List<Gift> records;
        private List<Loan> loanRecords = List.of();
        private List<Category> householdCategories = List.of();
        private List<Household> householdRecords = List.of();
    }

    @Data
    public static class MoneyProject {
        private Long id;
        private String type;
        private String name;
        private String note;
        private long balanceCents;
    }

    @Data
    public static class MoneyLog {
        private Long id;
        private String type;
        private String action;
        private long amountCents;
        private String title;
        private String note;
        private String flow;
        private long totalCents;
        private long savingsCents;
        private long accountCents;
        private Instant createdAt;
    }

    @Data
    public static class MoneyMonth {
        private String month;
        private long count;
    }

    @Data
    public static class MoneyTimeline {
        private List<MoneyLog> items;
        private Long nextCursor;
    }

    @Data
    public static class MoneySummary {
        private long cashCents;
        private long heldCents;
        private long lentCents;
        private long debtCents;
        private long totalCents;
        private long savingsCents;
        private List<MoneyProject> projects;
    }
}
