package com.base.system.modules.cms;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "运营配置")
@RestController
@RequestMapping("/cms")
@RequiredArgsConstructor
public class CmsController {
    private final CmsService service;

    @GetMapping("/home/list")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:gl:cms:home:list')")
    public List<CmsModels.Home> homes() { return service.homes(); }

    @GetMapping("/home/detail")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:gl:cms:home:list')")
    public CmsModels.Home home(@RequestParam @NotBlank @Size(max = 64) String id) { return service.home(id); }

    @PostMapping("/home/save")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority(#request.id() == null ? 'permission:gl:cms:home:add' : 'permission:gl:cms:home:edit')")
    public CmsModels.Home saveHome(@Valid @RequestBody CmsModels.Home request) { return service.saveHome(request); }

    @PostMapping("/home/delete")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:gl:cms:home:delete')")
    public String deleteHome(@Valid @RequestBody CmsModels.Id request) { return service.deleteHome(request.id()); }

    @GetMapping("/page/list")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:gl:cms:page:list')")
    public List<CmsModels.PageBinding> pages() { return service.pages(); }

    @PostMapping("/page/save")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority(#request.id() == null ? 'permission:gl:cms:page:add' : 'permission:gl:cms:page:edit')")
    public CmsModels.PageBinding savePage(@Valid @RequestBody CmsModels.PageBinding request) { return service.savePage(request); }

    @PostMapping("/page/delete")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:gl:cms:page:delete')")
    public String deletePage(@Valid @RequestBody CmsModels.Id request) { return service.deletePage(request.id()); }

    @GetMapping("/tabbar/list")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:gl:cms:tabbar:list')")
    public List<CmsModels.Tabbar> tabbars() { return service.tabbars(); }

    @GetMapping("/tabbar/detail")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:gl:cms:tabbar:list')")
    public CmsModels.Tabbar tabbar(@RequestParam @NotBlank @Size(max = 64) String id) { return service.tabbar(id); }

    @PostMapping("/tabbar/save")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority(#request.id() == null ? 'permission:gl:cms:tabbar:add' : 'permission:gl:cms:tabbar:edit')")
    public CmsModels.Tabbar saveTabbar(@Valid @RequestBody CmsModels.Tabbar request) { return service.saveTabbar(request); }

    @PostMapping("/tabbar/delete")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:gl:cms:tabbar:delete')")
    public String deleteTabbar(@Valid @RequestBody CmsModels.Id request) { return service.deleteTabbar(request.id()); }

    @GetMapping("/client/page")
    public CmsModels.Home clientPage(@RequestParam @NotBlank @Size(max = 64) String pageKey) { return service.clientPage(pageKey); }

    @GetMapping("/client/tabbar")
    public JsonNode clientTabbar() { return service.clientTabbar(); }
}
