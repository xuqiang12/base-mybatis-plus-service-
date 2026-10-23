package com.base.system.modules.ledger;

import com.base.security.LoginUser;
import com.base.system.modules.ledger.dto.LedgerRequests;
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

    @GetMapping("/data")
    public LedgerResponses.DataSet data(@AuthenticationPrincipal LoginUser user) {
        return service.data(user.id());
    }

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
