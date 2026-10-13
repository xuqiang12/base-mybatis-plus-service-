package com.base.system.modules.permission;

import com.base.common.dto.IdRequest;
import com.base.system.modules.permission.dto.PermissionRequests;
import com.base.system.modules.permission.entity.PermissionEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

// 操作权限字典接口。
@Tag(name = "权限")
@RestController
@RequestMapping("/permission")
@RequiredArgsConstructor
public class PermissionController {
    private final PermissionService service;
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:permission:list')")
    public List<PermissionEntity> list() { return service.list(); }
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:permission:add')")
    public String create(@Valid @RequestBody PermissionRequests.Create request) { return service.create(request); }
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:permission:edit')")
    public String update(@Valid @RequestBody PermissionRequests.Update request) { return service.update(request); }
    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:permission:delete')")
    public String delete(@Valid @RequestBody IdRequest request) { return service.delete(request.id()); }
}
