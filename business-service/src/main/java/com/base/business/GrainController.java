package com.base.business;

import com.base.security.LoginUser;
import com.base.common.result.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
public class GrainController {
    private final GrainService service;

    @PostMapping("/grain-weighing/create")
    public GrainResponses.Weighing weigh(@AuthenticationPrincipal LoginUser user,@Valid @RequestBody GrainRequests.WeighingCreate request) {
        return service.createWeighing(user.id(),request);
    }

    @GetMapping("/grain-weighing/list")
    public GrainResponses.WeighingPage weighings(@AuthenticationPrincipal LoginUser user,@Valid @ModelAttribute GrainRequests.CustomerQuery query) {
        return service.pendingWeighings(user.id(),query);
    }

    @GetMapping("/grain-weighing/tare-history")
    public List<GrainResponses.TareHistory> tareHistory(@AuthenticationPrincipal LoginUser user,@RequestParam @Positive long customerId) {
        return service.tareHistory(user.id(),customerId);
    }

    @PostMapping("/grain-purchase/create")
    public GrainResponses.Purchase create(@AuthenticationPrincipal LoginUser user,@Valid @RequestBody GrainRequests.PurchaseCreate request) {
        return service.createPurchase(user.id(),request);
    }

    @GetMapping("/grain-purchase/list")
    public PageResult<GrainResponses.Purchase> list(@AuthenticationPrincipal LoginUser user,@Valid @ModelAttribute GrainRequests.CustomerQuery query) {
        return service.purchases(user.id(),query);
    }

    @PostMapping("/grain-purchase/reprice")
    public GrainResponses.Purchase reprice(@AuthenticationPrincipal LoginUser user,@Valid @RequestBody GrainRequests.PurchaseReprice request) {
        return service.repricePurchase(user.id(),request);
    }

    @GetMapping("/grain-purchase/detail")
    public GrainResponses.Purchase detail(@AuthenticationPrincipal LoginUser user,@RequestParam @Positive long id) {
        return service.purchase(user.id(),id);
    }
}
