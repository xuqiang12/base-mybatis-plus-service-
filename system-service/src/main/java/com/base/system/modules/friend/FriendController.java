package com.base.system.modules.friend;

import com.base.common.dto.IdRequest;
import com.base.common.result.PageResult;
import com.base.security.LoginUser;
import com.base.system.modules.friend.dto.FriendCreateRequest;
import com.base.system.modules.friend.dto.FriendQueryRequest;
import com.base.system.modules.friend.dto.FriendResponse;
import com.base.system.modules.friend.dto.FriendUpdateRequest;
import jakarta.validation.Valid;
import com.base.system.modules.ledger.dto.LedgerQueries;
import com.base.system.modules.ledger.dto.LedgerReadResponses;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 分页查询当前家庭的亲友。
     */
    @GetMapping("/list")
    public PageResult<FriendResponse> list(@AuthenticationPrincipal LoginUser user,
                                           @Valid FriendQueryRequest query) {
        return friendService.list(user.id(), query);
    }

    @GetMapping("/summary/list")
    public PageResult<LedgerReadResponses.FriendSummary> summaryList(@AuthenticationPrincipal LoginUser user,
                                                                    @Valid LedgerQueries.Friends query) {
        return friendService.summaryList(user.id(), query);
    }

    /**
     * 查询当前家庭中的单个亲友。
     */
    @GetMapping("/detail")
    public FriendResponse detail(@AuthenticationPrincipal LoginUser user,
                                 @RequestParam @Positive(message = "亲友编号无效") Long id) {
        return friendService.detail(user.id(), id);
    }

    /**
     * 修改当前家庭中的亲友。
     */
    @PostMapping("/update")
    public FriendResponse update(@AuthenticationPrincipal LoginUser user,
                                 @Valid @RequestBody FriendUpdateRequest request) {
        return friendService.update(user.id(), request);
    }

    /**
     * 删除当前家庭中的亲友。
     */
    @PostMapping("/delete")
    public String delete(@AuthenticationPrincipal LoginUser user,
                         @Valid @RequestBody IdRequest request) {
        return friendService.delete(user.id(), request.id().longValue());
    }

}
