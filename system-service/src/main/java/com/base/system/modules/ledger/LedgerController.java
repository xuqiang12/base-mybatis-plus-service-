package com.base.system.modules.ledger;

import com.base.security.LoginUser;
import com.base.system.modules.ledger.dto.LedgerRequests;
import com.base.system.modules.ledger.dto.LedgerQueries;
import com.base.system.modules.ledger.dto.LedgerReadResponses;
import com.base.common.result.PageResult;
import jakarta.validation.constraints.Positive;
import com.base.system.modules.ledger.dto.LedgerResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 礼账账簿与人情接口。 */
@RestController
@RequestMapping("/ledger")
@RequiredArgsConstructor
public class LedgerController {
    private final LedgerService service;
    private final HouseholdService householdService;
    private final MoneyService moneyService;

    @GetMapping("/household/detail")
    public LedgerReadResponses.Household householdDetail(@AuthenticationPrincipal LoginUser user, @RequestParam @Positive Long id) {
        return householdService.detail(user.id(), id);
    }

    @GetMapping("/gift/detail")
    public LedgerReadResponses.Gift giftDetail(@AuthenticationPrincipal LoginUser user, @RequestParam @Positive Long id) {
        return service.giftDetail(user.id(), id);
    }

    @GetMapping("/book/detail")
    public LedgerReadResponses.Book bookDetail(@AuthenticationPrincipal LoginUser user, @RequestParam @Positive Long id) {
        return service.bookDetail(user.id(), id);
    }

    @GetMapping("/money/project/detail")
    public LedgerResponses.MoneyProject moneyProjectDetail(@AuthenticationPrincipal LoginUser user, @RequestParam @Positive Long id) {
        return moneyService.project(user.id(), id);
    }

    @GetMapping("/household/list")
    public LedgerReadResponses.HouseholdPage householdList(@AuthenticationPrincipal LoginUser user, @Valid LedgerQueries.Records query) {
        return householdService.list(user.id(), query);
    }

    @GetMapping("/household/summary")
    public LedgerReadResponses.HouseholdSummary householdSummary(@AuthenticationPrincipal LoginUser user, @Valid LedgerQueries.Records query) {
        return householdService.summary(user.id(), query);
    }

    @GetMapping("/household/statistics")
    public LedgerReadResponses.Statistics householdStatistics(@AuthenticationPrincipal LoginUser user, @RequestParam String period) {
        return householdService.statistics(user.id(), period);
    }

    @GetMapping("/gift/list")
    public PageResult<LedgerReadResponses.Gift> giftList(@AuthenticationPrincipal LoginUser user, @Valid LedgerQueries.Records query) {
        return service.giftList(user.id(), query);
    }

    @GetMapping("/gift/summary")
    public LedgerReadResponses.GiftSummary giftSummary(@AuthenticationPrincipal LoginUser user, @Valid LedgerQueries.Records query) {
        return service.giftSummary(user.id(), query);
    }

    @GetMapping("/loan/list")
    public PageResult<LedgerReadResponses.Loan> loanList(@AuthenticationPrincipal LoginUser user, @Valid LedgerQueries.Records query) {
        return service.loanList(user.id(), query);
    }

    @GetMapping("/loan/summary")
    public LedgerReadResponses.LoanTotals loanSummary(@AuthenticationPrincipal LoginUser user, @RequestParam @Positive Long friendId) {
        return service.loanSummary(user.id(), friendId);
    }

    @GetMapping("/loan/summary/list")
    public PageResult<LedgerReadResponses.FriendSummary> loanSummaryList(@AuthenticationPrincipal LoginUser user, @Valid LedgerQueries.LoanSummary query) {
        return service.loanSummaryList(user.id(), query);
    }

    @GetMapping("/book/list")
    public PageResult<LedgerReadResponses.Book> bookList(@AuthenticationPrincipal LoginUser user, @Valid LedgerQueries.Page query) {
        return service.bookList(user.id(), query);
    }

    @GetMapping("/record/recent")
    public java.util.List<LedgerReadResponses.Recent> recordRecent(@AuthenticationPrincipal LoginUser user, @RequestParam(defaultValue = "5") int limit) {
        return service.recent(user.id(), limit);
    }

    @GetMapping("/money/project/list")
    public java.util.List<LedgerResponses.MoneyProject> moneyProjectList(@AuthenticationPrincipal LoginUser user) {
        return moneyService.projects(user.id());
    }

    @GetMapping("/category/list")
    public java.util.List<LedgerResponses.Category> categoryList(@AuthenticationPrincipal LoginUser user) {
        return householdService.categories(user.id());
    }

    @GetMapping("/history/friend/list")
    public PageResult<com.base.system.modules.friend.dto.FriendResponse> historyFriend(@AuthenticationPrincipal LoginUser user, @Valid LedgerQueries.History query) {
        return service.historyFriends(user.id(), query);
    }

    @GetMapping("/history/book/list")
    public PageResult<LedgerReadResponses.Book> historyBook(@AuthenticationPrincipal LoginUser user, @Valid LedgerQueries.History query) {
        return service.historyBooks(user.id(), query);
    }

    @GetMapping("/history/gift/list")
    public PageResult<LedgerReadResponses.Gift> historyGift(@AuthenticationPrincipal LoginUser user, @Valid LedgerQueries.History query) {
        return service.historyGifts(user.id(), query);
    }

    @GetMapping("/history/loan/list")
    public PageResult<LedgerReadResponses.Loan> historyLoan(@AuthenticationPrincipal LoginUser user, @Valid LedgerQueries.History query) {
        return service.historyLoans(user.id(), query);
    }

    @GetMapping("/history/household/list")
    public LedgerReadResponses.HouseholdPage historyHousehold(@AuthenticationPrincipal LoginUser user, @Valid LedgerQueries.History query) {
        return service.historyHousehold(user.id(), query);
    }

    @GetMapping("/money")
    public LedgerResponses.MoneySummary money(@AuthenticationPrincipal LoginUser user) {
        return moneyService.summary(user.id());
    }

    @PostMapping("/money/transfer")
    public LedgerResponses.MoneySummary transfer(@AuthenticationPrincipal LoginUser user,
                                                 @Valid @RequestBody LedgerRequests.MoneyTransfer request) {
        return moneyService.transfer(user.id(), request);
    }

    @GetMapping("/money/timeline")
    public LedgerResponses.MoneyTimeline moneyTimeline(@AuthenticationPrincipal LoginUser user,
                                                       @RequestParam(required = false) Long beforeId,
                                                       @RequestParam(required = false) String month,
                                                       @RequestParam(required = false) String keyword) {
        return moneyService.timeline(user.id(), beforeId, month, keyword);
    }

    @GetMapping("/money/timeline/months")
    public java.util.List<LedgerResponses.MoneyMonth> moneyTimelineMonths(@AuthenticationPrincipal LoginUser user,
                                                                         @RequestParam(required = false) String keyword) {
        return moneyService.timelineMonths(user.id(), keyword);
    }

    @PostMapping("/money/project/create")
    public LedgerResponses.MoneySummary createMoneyProject(@AuthenticationPrincipal LoginUser user,
                                                           @Valid @RequestBody LedgerRequests.MoneyProject request) {
        return moneyService.createProject(user.id(), request);
    }

    @PostMapping("/money/project/update")
    public LedgerResponses.MoneySummary updateMoneyProject(@AuthenticationPrincipal LoginUser user,
                                                           @Valid @RequestBody LedgerRequests.MoneyProjectUpdate request) {
        return moneyService.updateProject(user.id(), request);
    }

    @PostMapping("/money/project/delete")
    public LedgerResponses.MoneySummary deleteMoneyProject(@AuthenticationPrincipal LoginUser user,
                                                           @Valid @RequestBody LedgerRequests.Id request) {
        return moneyService.deleteProject(user.id(), request.getId());
    }

    /** 兼容旧客户端；新页面使用独立详情、分页与统计查询。 */
    @GetMapping("/data")
    public LedgerResponses.DataSet data(@AuthenticationPrincipal LoginUser user) {
        return service.data(user.id());
    }

    /** 兼容旧客户端；新历史页面按资源分页查询。 */
    @GetMapping("/history/data")
    public LedgerResponses.DataSet history(@AuthenticationPrincipal LoginUser user, @RequestParam String familyId) {
        return service.history(user.id(), familyId);
    }

    @PostMapping("/book/create")
    public LedgerResponses.Book createBook(@AuthenticationPrincipal LoginUser user,
                                            @Valid @RequestBody LedgerRequests.Book request) {
        return service.createBook(user.id(), request);
    }

    @PostMapping("/book/update")
    public LedgerResponses.Book updateBook(@AuthenticationPrincipal LoginUser user,
                                           @Valid @RequestBody LedgerRequests.BookUpdate request) {
        return service.updateBook(user.id(), request);
    }

    @PostMapping("/book/delete")
    public void deleteBook(@AuthenticationPrincipal LoginUser user,
                           @Valid @RequestBody LedgerRequests.BookDelete request) {
        service.deleteBook(user.id(), request);
    }

    @PostMapping("/gift/create")
    public LedgerResponses.CreatedGift createGift(@AuthenticationPrincipal LoginUser user,
                                                  @Valid @RequestBody LedgerRequests.Gift request) {
        return service.createGift(user.id(), request);
    }

    @PostMapping("/gift/update")
    public LedgerResponses.CreatedGift updateGift(@AuthenticationPrincipal LoginUser user,
                                                  @Valid @RequestBody LedgerRequests.GiftUpdate request) {
        return service.updateGift(user.id(), request);
    }

    @PostMapping("/gift/delete")
    public void deleteGift(@AuthenticationPrincipal LoginUser user,
                           @Valid @RequestBody LedgerRequests.Id request) {
        service.deleteGift(user.id(), request.getId());
    }

    @PostMapping("/loan/create")
    public LedgerResponses.CreatedLoan createLoan(@AuthenticationPrincipal LoginUser user,
                                                  @Valid @RequestBody LedgerRequests.Loan request) {
        return service.createLoan(user.id(), request);
    }

    @PostMapping("/category/create")
    public LedgerResponses.Category createCategory(@AuthenticationPrincipal LoginUser user,
                                                   @Valid @RequestBody LedgerRequests.Category request) {
        return householdService.createCategory(user.id(), request);
    }

    @PostMapping("/category/update")
    public LedgerResponses.Category updateCategory(@AuthenticationPrincipal LoginUser user,
                                                   @Valid @RequestBody LedgerRequests.CategoryUpdate request) {
        return householdService.updateCategory(user.id(), request);
    }

    @PostMapping("/category/delete")
    public void deleteCategory(@AuthenticationPrincipal LoginUser user,
                               @Valid @RequestBody LedgerRequests.Id request) {
        householdService.deleteCategory(user.id(), request.getId());
    }

    @PostMapping("/category/move")
    public java.util.List<LedgerResponses.Category> moveCategory(@AuthenticationPrincipal LoginUser user,
                                                                  @Valid @RequestBody LedgerRequests.Id request) {
        return householdService.moveCategory(user.id(), request.getId());
    }

    @GetMapping("/household/month")
    public LedgerResponses.Month month(@AuthenticationPrincipal LoginUser user, @RequestParam String month) {
        return householdService.readMonth(user.id(), month);
    }

    @PostMapping("/household/month/save")
    public LedgerResponses.Month saveMonth(@AuthenticationPrincipal LoginUser user,
                                          @Valid @RequestBody LedgerRequests.Month request) {
        return householdService.saveMonth(user.id(), request);
    }

    @PostMapping("/household/save")
    public LedgerResponses.Household saveHousehold(@AuthenticationPrincipal LoginUser user,
                                                   @Valid @RequestBody LedgerRequests.Household request) {
        return householdService.saveRecord(user.id(), request);
    }

    @PostMapping("/household/update")
    public LedgerResponses.Household updateHousehold(@AuthenticationPrincipal LoginUser user,
                                                     @Valid @RequestBody LedgerRequests.HouseholdUpdate request) {
        return householdService.updateRecord(user.id(), request);
    }

    @PostMapping("/household/delete")
    public void deleteHousehold(@AuthenticationPrincipal LoginUser user,
                                @Valid @RequestBody LedgerRequests.Id request) {
        householdService.deleteRecord(user.id(), request.getId());
    }
}
