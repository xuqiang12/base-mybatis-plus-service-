package com.base.business;

import com.base.common.result.PageResult;
import com.base.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
public class BusinessSettlementController {
    private final BusinessSettlementService service;
    @GetMapping("/customer/bills")
    public BusinessSettlementResponses.BillPage bills(@AuthenticationPrincipal LoginUser user,@Valid @ModelAttribute BusinessSettlementRequests.BillQuery query) {
        return service.bills(user.id(),query);
    }
    @GetMapping("/fertilizer-sale/list")
    public PageResult<FertilizerResponses.Sale> sales(@AuthenticationPrincipal LoginUser user,@Valid @ModelAttribute GrainRequests.CustomerQuery query) {
        return service.sales(user.id(),query);
    }
    @GetMapping("/settlement/list")
    public PageResult<BusinessSettlementResponses.Settlement> history(@AuthenticationPrincipal LoginUser user,@Valid @ModelAttribute BusinessSettlementRequests.HistoryQuery query) {
        return service.history(user.id(),query);
    }
    @PostMapping("/settlement/create")
    public BusinessSettlementResponses.Settlement create(@AuthenticationPrincipal LoginUser user,@Valid @RequestBody BusinessSettlementRequests.Create request) {
        return service.create(user.id(),request);
    }
}
