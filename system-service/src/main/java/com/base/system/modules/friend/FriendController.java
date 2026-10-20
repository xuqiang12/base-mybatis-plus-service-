package com.base.system.modules.friend;

import com.base.security.LoginUser;
import com.base.system.modules.friend.dto.FriendCreateRequest;
import com.base.system.modules.friend.dto.FriendResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
public class FriendController {
    private final FriendService friendService;

    /**
     * 新增亲友。
     */
    @PostMapping("/create")
    public FriendResponse create(@AuthenticationPrincipal LoginUser user, @Valid @RequestBody FriendCreateRequest request){
        return friendService.create(user.id(), request);
    }

}
