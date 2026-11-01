package com.base.business;

import com.base.common.result.PageResult;
import com.base.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
public class BusinessController {
    private final BusinessCustomerService customers;

    @PostMapping("/customer/create")
    public BusinessResponses.Customer create(@AuthenticationPrincipal LoginUser user,
                                             @Valid @RequestBody BusinessRequests.CustomerCreate request) {
        return customers.create(user.id(), request);
    }

    @PostMapping("/customer/update")
    public BusinessResponses.Customer update(@AuthenticationPrincipal LoginUser user,
                                             @Valid @RequestBody BusinessRequests.CustomerUpdate request) {
        return customers.update(user.id(), request);
    }

    @GetMapping("/customer/list")
    public PageResult<BusinessResponses.Customer> list(@AuthenticationPrincipal LoginUser user,
                                                       @Valid BusinessRequests.CustomerQuery query) {
        return customers.list(user.id(), query);
    }

    @PostMapping("/customer/delete")
    public void delete(@AuthenticationPrincipal LoginUser user,
                       @Valid @RequestBody BusinessRequests.CustomerDelete request) {
        customers.delete(user.id(), request.getId());
    }

    @GetMapping("/customer/detail")
    public BusinessResponses.Customer detail(@AuthenticationPrincipal LoginUser user,
                                             @RequestParam @Positive long id) {
        return customers.detail(user.id(), id);
    }
}
