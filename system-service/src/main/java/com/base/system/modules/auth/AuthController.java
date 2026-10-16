package com.base.system.modules.auth;

import com.base.security.LoginUser;
import com.base.system.modules.auth.dto.LoginRequest;
import com.base.system.modules.auth.dto.EmailRegisterRequests;
import jakarta.servlet.http.HttpServletRequest;
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
    private final EmailCodeService emailCodes;
    @PostMapping("/email/code")
    public String emailCode(@Valid @RequestBody EmailRegisterRequests.Send request, HttpServletRequest http) {
        return emailCodes.send(request.email(), http.getRemoteAddr());
    }
    @PostMapping("/email/register")
    public String emailRegister(@Valid @RequestBody EmailRegisterRequests.Register request) {
        return emailCodes.register(request);
    }
    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest request) { return service.login(request); }
    @GetMapping("/userInfo")
    public Map<String, Object> userInfo(@AuthenticationPrincipal LoginUser user) { return service.userInfo(user); }
}
