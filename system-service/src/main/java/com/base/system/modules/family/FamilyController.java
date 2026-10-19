package com.base.system.modules.family;

import com.base.security.LoginUser;
import com.base.system.modules.family.FamilyModels.*;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/family")
@RequiredArgsConstructor
public class FamilyController {
    private final FamilyService families;

    @GetMapping("/state")
    public Snapshot state(@AuthenticationPrincipal LoginUser u) {
        return families.state(u.id());
    }

    @PostMapping("/invitation/share")
    public Share share(
            @AuthenticationPrincipal LoginUser u,
            @RequestHeader(value = "X-Family-Id", required = false) String family) {
        return families.share(u.id(), family);
    }

    @GetMapping("/invitation")
    public InvitationInfo invitationInfo(@RequestParam String token) {
        return families.invitationInfo(token);
    }

    @PostMapping("/invitation/accept")
    public Snapshot accept(
            @AuthenticationPrincipal LoginUser u,
            @RequestHeader(value = "X-Family-Id", required = false) String family,
            @RequestBody Request r) {
        return families.accept(u.id(), family, r.getToken());
    }

    @PostMapping("/leave")
    public Snapshot leave(
            @AuthenticationPrincipal LoginUser u,
            @RequestHeader(value = "X-Family-Id", required = false) String family) {
        return families.leave(u.id(), family);
    }

}
