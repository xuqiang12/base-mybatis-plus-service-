package com.base.system.modules.cms;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

// CMS 完整保存契约；组件内容仍使用小程序现有 info 对象。
public final class CmsModels {
    private CmsModels() {}

    public record Id(@NotBlank @Size(max = 64) String id) {}

    public record Home(@Size(min = 1, max = 64) String id,
            @NotBlank @Size(max = 100) String code, @NotBlank @Size(max = 100) String name,
            @Size(max = 32) String scene, @NotNull @Min(0) @Max(1) Integer status,
            @NotNull List<@NotNull @Valid Component> components) {}

    public record Component(@Size(min = 1, max = 64) String id,
            @NotBlank @Size(max = 32) String templateId, @Size(max = 100) String templateName,
            @NotNull JsonNode info, @NotNull @Min(0) Integer sortNo, @NotNull @Min(0) @Max(1) Integer status) {}

    public record PageBinding(@Size(min = 1, max = 64) String id,
            @NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 64) String pageKey,
            @NotBlank @Size(max = 64) String decorationId, @NotNull @Min(0) @Max(1) Integer status) {}

    public record Tabbar(@Size(min = 1, max = 64) String id,
            @NotBlank @Size(max = 100) String code, @NotBlank @Size(max = 100) String name,
            @NotNull @Min(0) @Max(1) Integer status, @NotNull JsonNode config) {}
}
