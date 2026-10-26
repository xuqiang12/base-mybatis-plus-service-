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
        private Long projectId;
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
        private com.base.system.modules.friend.dto.FriendResponse friend;
    }

    @Data
    public static class CreatedLoan {
        private Loan record;
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
    public static class MoneySummary {
        private long cashCents;
        private long heldCents;
        private long lentCents;
        private long debtCents;
        private long totalCents;
        private List<MoneyProject> projects;
    }
}
