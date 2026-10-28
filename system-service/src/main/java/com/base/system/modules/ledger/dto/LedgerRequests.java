package com.base.system.modules.ledger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;

/** 礼账写请求。 */
public final class LedgerRequests {
    private LedgerRequests() {}

    @Data
    public static class Id {
        @NotNull(message = "请选择记录")
        @Positive(message = "记录编号无效")
        private Long id;
    }

    @Data
    public static class Book {
        @NotBlank(message = "账簿名称不能为空")
        private String name;
        private LocalDate date;
        private String note = "";
    }

    @Data
    public static class BookUpdate extends Book {
        @NotNull(message = "请选择账簿")
        @Positive(message = "账簿编号无效")
        private Long id;
    }

    @Data
    public static class BookDelete {
        @NotNull(message = "请选择账簿")
        @Positive(message = "账簿编号无效")
        private Long id;
    }

    @Data
    public static class Gift {
        private Long projectId;
        @NotBlank(message = "请选择事件类型")
        private String eventType;
        @NotBlank(message = "请选择收送方向")
        private String direction;
        @NotNull(message = "请选择日期")
        private LocalDate date;
        @NotBlank(message = "姓名不能为空")
        private String name;
        private String appellation = "";
        @NotNull(message = "金额不能为空")
        @Max(value = 9007199254740991L, message = "金额超出支持范围")
        @Positive(message = "金额必须大于0")
        private Long amountCents;
        private String note = "";
        private Long bookId;
        private Long friendId;
    }

    @Data
    public static class GiftUpdate extends Gift {
        @NotNull(message = "请选择人情记录")
        @Positive(message = "记录编号无效")
        private Long id;
    }

    @Data
    public static class Loan {
        private Long projectId;
        @NotBlank(message = "请选择借贷操作")
        private String direction;
        @NotBlank(message = "姓名不能为空")
        private String name;
        private String appellation = "";
        @NotNull(message = "金额不能为空")
        @Max(value = 9007199254740991L, message = "金额超出支持范围")
        @Positive(message = "金额必须大于0")
        private Long amountCents;
        @NotNull(message = "请选择日期")
        private LocalDate date;
        private String note = "";
        private Long friendId;
    }

    @Data
    public static class Category {
        @NotBlank(message = "请选择收支方向")
        private String type;
        @NotBlank(message = "分类名称不能为空")
        private String name;
        @NotBlank(message = "请选择分类图标")
        private String icon;
    }

    @Data
    public static class CategoryUpdate {
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 12, message = "分类名称最多12个字")
        private String name;
        @NotBlank(message = "请选择分类图标")
        private String icon;
        @NotNull(message = "请选择分类")
        @Positive(message = "分类编号无效")
        private Long id;
        @NotNull(message = "请选择启停状态")
        private Boolean active;
    }

    @Data
    public static class Household {
        private Long projectId;
        @NotBlank(message = "请选择收支方向")
        private String type;
        @NotNull(message = "请选择分类")
        @Positive(message = "分类编号无效")
        private Long categoryId;
        @NotNull(message = "金额不能为空")
        @Max(value = 9007199254740991L, message = "金额超出支持范围")
        @Positive(message = "金额必须大于0")
        private Long amountCents;
        @NotNull(message = "请选择日期")
        private LocalDate date;
        private String note = "";
    }

    @Data
    public static class Month {
        @NotBlank(message = "请选择月份")
        @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "月份格式无效")
        private String month;
        @NotNull(message = "请填写本次收支")
        @Size(min = 1, max = 200, message = "每次请填写1至200项收支")
        @Valid
        private List<MonthItem> items;
    }

    @Data
    public static class MonthItem {
        private Long projectId;
        @NotNull(message = "请选择分类")
        @Positive(message = "分类编号无效")
        private Long categoryId;
        @NotBlank(message = "请选择收支方向")
        private String type;
        @NotNull(message = "请填写金额")
        @Positive(message = "金额必须大于0")
        @Max(value = 9007199254740991L, message = "金额超出支持范围")
        private Long amountCents;
        @Size(max = 1000, message = "备注最多1000个字")
        private String note = "";
    }

    @Data
    public static class HouseholdUpdate extends Household {
        @NotNull(message = "请选择记录")
        @Positive(message = "记录编号无效")
        private Long id;
    }

    @Data
    public static class MoneyProject {
        @NotBlank(message = "请选择账户类型")
        @Pattern(regexp = "bank|stock|other", message = "请选择有效的账户类型")
        private String type;
        @NotBlank(message = "账户名称不能为空")
        @Size(max = 30, message = "账户名称最多30个字")
        private String name;
        @Size(max = 200, message = "备注最多200个字")
        private String note = "";
        @NotNull(message = "期初余额不能为空")
        @Min(value = 0, message = "期初余额不能为负数")
        @Max(value = 9007199254740991L, message = "期初余额超出支持范围")
        private Long initialBalanceCents = 0L;
    }

    @Data
    public static class MoneyProjectUpdate {
        @NotNull(message = "请选择账户")
        @Positive(message = "账户编号无效")
        private Long id;
        @NotBlank(message = "请选择账户类型")
        @Pattern(regexp = "bank|stock|other", message = "请选择有效的账户类型")
        private String type;
        @NotBlank(message = "账户名称不能为空")
        @Size(max = 30, message = "账户名称最多30个字")
        private String name;
        @Size(max = 200, message = "备注最多200个字")
        private String note = "";
        @Min(value = 0, message = "当前余额不能为负数")
        @Max(value = 9007199254740991L, message = "当前余额超出支持范围")
        private Long targetBalanceCents;
    }

    @Data
    public static class MoneyTransfer {
        @Positive(message = "请选择有效的来源项目")
        private Long fromProjectId;
        @Positive(message = "请选择有效的目标项目")
        private Long toProjectId;
        private String type;
        @Size(max = 100, message = "项目名称最多100个字")
        private String name;
        @NotNull(message = "请填写调拨金额")
        @Positive(message = "调拨金额必须大于0")
        @Max(value = 9007199254740991L, message = "金额超出支持范围")
        private Long amountCents;
        @Size(max = 1000, message = "备注最多1000个字")
        private String note = "";
    }
}
