package com.base.system.modules.auth;

import com.base.common.result.ApiResponse;
import com.base.security.LoginUser;
import com.base.system.modules.auth.dto.EmailRegisterRequest;
import com.base.system.modules.auth.dto.LoginRequest;
import com.base.system.modules.auth.dto.LoginResponse;
import com.base.system.modules.auth.dto.SendEmailCodeRequest;
import com.base.system.modules.auth.dto.UserInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码发送、邮箱注册、密码登录和当前用户信息接口。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private EmailCodeService emailCodeService;

    @Operation(summary = "发送注册验证码")
    @PostMapping("/email/code")
    public ApiResponse<String> sendEmailCode(@RequestBody SendEmailCodeRequest request, HttpServletRequest httpRequest) {
        emailCodeService.sendEmailCode(request, httpRequest.getRemoteAddr());
        return ApiResponse.success("验证码已发送，请查收邮箱");
    }

    @Operation(summary = "邮箱验证码注册")
    @PostMapping("/email/register")
    public ApiResponse<String> register(@RequestBody EmailRegisterRequest request) {
        emailCodeService.register(request);
        return ApiResponse.success("注册成功");
    }

    @Operation(summary = "邮箱或手机号密码登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse result = authService.login(request);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取当前用户信息和权限菜单")
    @GetMapping("/userInfo")
    public ApiResponse<UserInfoResponse> getUserInfo(@AuthenticationPrincipal LoginUser user) {
        UserInfoResponse result = authService.getUserInfo(user);
        return ApiResponse.success(result);
    }
}
