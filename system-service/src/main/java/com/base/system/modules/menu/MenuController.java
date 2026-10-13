package com.base.system.modules.menu;

import com.base.common.dto.IdRequest;
import com.base.system.modules.menu.dto.ButtonResponse;
import com.base.system.modules.menu.dto.MenuNode;
import com.base.system.modules.menu.dto.MenuRequests;
import com.base.system.modules.menu.entity.MenuEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

// 菜单树、菜单维护和菜单按钮接口。
@Tag(name = "菜单")
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuController {
    private final MenuService service;
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:menu:list')")
    public List<MenuEntity> list() { return service.list(); }
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:menu:list')")
    public List<MenuNode> tree() { return service.tree(); }
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:menu:add')")
    public String create(@Valid @RequestBody MenuRequests.Create request) { return service.create(request); }
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:menu:edit')")
    public String update(@Valid @RequestBody MenuRequests.Update request) { return service.update(request); }
    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:menu:delete')")
    public String delete(@Valid @RequestBody IdRequest request) { return service.delete(request.id()); }
    @GetMapping("/button/list")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:menu:list')")
    public List<ButtonResponse> buttons(@RequestParam @Positive Integer menuId) { return service.buttons(menuId); }
    @PostMapping("/button/create")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:menu:add')")
    public String createButton(@Valid @RequestBody MenuRequests.CreateButton request) { return service.createButton(request); }
    @PostMapping("/button/update")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:menu:edit')")
    public String updateButton(@Valid @RequestBody MenuRequests.UpdateButton request) { return service.updateButton(request); }
    @PostMapping("/button/delete")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:system:menu:delete')")
    public String deleteButton(@Valid @RequestBody IdRequest request) { return service.deleteButton(request.id()); }
}
