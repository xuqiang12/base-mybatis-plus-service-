package com.base.system.modules.auth;

import com.base.security.LoginUser;
import com.base.system.modules.auth.dto.LoginRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

// 登录和当前用户信息接口。
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService service;
    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest request) { return service.login(request); }
    @GetMapping("/userInfo")
    public Map<String, Object> userInfo(@AuthenticationPrincipal LoginUser user) { return service.userInfo(user); }
    // 业务服务携带原 Token 查询当前身份；不能通过匿名 userId 查询授权。
    @GetMapping("/access")
    public LoginUser access(@AuthenticationPrincipal LoginUser user) { return user; }
}
