package com.base.system.modules.role;

import com.base.common.dto.IdRequest;
import com.base.system.modules.role.dto.RoleRequests;
import com.base.system.modules.role.dto.RoleResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

// 角色及角色授权接口。
@Tag(name = "角色")
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService service;
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:role:list')")
    public List<RoleResponses.Summary> list() { return service.list(); }
    @GetMapping("/detail")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:role:list')")
    public RoleResponses.Detail detail(@RequestParam @Positive Integer id) { return service.detail(id); }
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:role:add')")
    public String create(@Valid @RequestBody RoleRequests.Create request) { return service.create(request); }
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:role:edit')")
    public String update(@Valid @RequestBody RoleRequests.Update request) { return service.update(request); }
    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:role:delete')")
    public String delete(@Valid @RequestBody IdRequest request) { return service.delete(request.id()); }
    @PostMapping("/permission")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:role:permission')")
    public String grant(@Valid @RequestBody RoleRequests.Grant request) { return service.grant(request); }
}
