package com.base.business;

import com.base.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
public class FertilizerController {
    private final FertilizerService service;
    @GetMapping("/product/list")
    public List<FertilizerResponses.Product> products(@AuthenticationPrincipal LoginUser user) { return service.products(user.id()); }
    @PostMapping("/product/create")
    public FertilizerResponses.Product createProduct(@AuthenticationPrincipal LoginUser user,@Valid @RequestBody FertilizerRequests.ProductCreate request) { return service.createProduct(user.id(),request); }
    @PostMapping("/product/update")
    public FertilizerResponses.Product updateProduct(@AuthenticationPrincipal LoginUser user,@Valid @RequestBody FertilizerRequests.ProductUpdate request) { return service.updateProduct(user.id(),request); }
    @PostMapping("/product/delete")
    public void deleteProduct(@AuthenticationPrincipal LoginUser user,@Valid @RequestBody FertilizerRequests.Id request) { service.deleteProduct(user.id(),request.id()); }
    @GetMapping("/account/list")
    public List<FertilizerResponses.Account> accounts(@AuthenticationPrincipal LoginUser user) { return service.accounts(user.id()); }
    @PostMapping("/account/create")
    public FertilizerResponses.Account createAccount(@AuthenticationPrincipal LoginUser user,@Valid @RequestBody FertilizerRequests.AccountCreate request) { return service.createAccount(user.id(),request.name()); }
    @PostMapping("/fertilizer-sale/create")
    public FertilizerResponses.Sale createSale(@AuthenticationPrincipal LoginUser user,@Valid @RequestBody FertilizerRequests.SaleCreate request) { return service.createSale(user.id(),request); }
    @GetMapping("/fertilizer-sale/detail")
    public FertilizerResponses.Sale sale(@AuthenticationPrincipal LoginUser user,@RequestParam @Positive long id) { return service.sale(user.id(),id); }
}
