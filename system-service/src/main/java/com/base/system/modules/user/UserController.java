package com.base.system.modules.user;

import com.base.common.result.PageResult;
import com.base.common.dto.IdRequest;
import com.base.security.LoginUser;
import com.base.system.modules.user.dto.UserRequests;
import com.base.system.modules.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 用户查询、维护和公开注册接口。
@Tag(name = "用户")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:user:list')")
    public PageResult<UserResponse> list(@Valid @ModelAttribute UserRequests.ListQuery query) { return service.list(query); }
    @GetMapping("/detail")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:user:detail')")
    public UserResponse detail(@RequestParam @Positive Integer id) { return service.detail(id); }
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:user:add')")
    public String create(@Valid @RequestBody UserRequests.Create request, @AuthenticationPrincipal LoginUser user) { return service.create(request, user); }
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:user:edit')")
    public String update(@Valid @RequestBody UserRequests.Update request, @AuthenticationPrincipal LoginUser user) { return service.update(request, user); }
    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:user:delete')")
    public String delete(@Valid @RequestBody IdRequest request, @AuthenticationPrincipal LoginUser user) { return service.delete(request.id(), user); }
    @PostMapping("/register")
    public String register(@Valid @RequestBody UserRequests.Register request) { return service.register(request); }
}
