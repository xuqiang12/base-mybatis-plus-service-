package com.base.seed;

import com.base.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CmsSeedService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Transactional
    public int initialize() {
        int applied = 0;
        // H2 的 JSON 字符串参数需显式 FORMAT JSON；PostgreSQL 使用原生 JSONB 转换。
        String jsonValue = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                "H2".equals(connection.getMetaData().getDatabaseProductName()))) ? "? FORMAT JSON" : "CAST(? AS JSONB)";
        if (claim("cms-home")) {
            initializeHome(jsonValue);
            applied++;
        }
        if (claim("cms-tabbar")) {
            initializeTabbar(jsonValue);
            applied++;
        }
        if (claim("cms-management")) {
            initializeManagement();
            applied++;
        }
        return applied;
    }

    private boolean claim(String module) {
        return jdbc.update("INSERT INTO relations.sys_seed_history(module,version) VALUES (?,1) ON CONFLICT DO NOTHING", module) == 1;
    }

    private void initializeHome(String jsonValue) {
        for (JsonNode item : read("home")) {
            var existing = jdbc.queryForList("SELECT id FROM relations.cms_home_decoration WHERE code=?", String.class, item.path("code").asText());
            if (!existing.isEmpty()) continue; // 已有方案整份保留，包括组件删改及停用状态。
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO relations.cms_home_decoration(id,code,name,scene,status) VALUES (?,?,?,?,?)",
                    id, item.path("code").asText(), item.path("name").asText(), item.path("scene").asText(), item.path("status").asInt());
            for (JsonNode component : item.path("components")) {
                jdbc.update("INSERT INTO relations.cms_home_component(id,decoration_id,template_id,template_name,info,sort_no,status) VALUES (?,?,?,?," + jsonValue + ",?,?)",
                        UUID.randomUUID().toString(), id, component.path("templateId").asText(), component.path("templateName").asText(),
                        component.path("info").toString(), component.path("sortNo").asInt(), component.path("status").asInt());
            }
        }
        for (JsonNode item : read("page-bindings")) {
            var existing = jdbc.queryForList("SELECT id FROM relations.cms_page_binding WHERE page_key=?", String.class, item.path("pageKey").asText());
            if (!existing.isEmpty()) continue;
            String decorationId = jdbc.queryForObject("SELECT id FROM relations.cms_home_decoration WHERE code=?", String.class, item.path("decorationCode").asText());
            jdbc.update("INSERT INTO relations.cms_page_binding(id,name,page_key,decoration_id,status) VALUES (?,?,?,?,?)",
                    UUID.randomUUID().toString(), item.path("name").asText(), item.path("pageKey").asText(), decorationId, item.path("status").asInt());
        }
    }

    private void initializeTabbar(String jsonValue) {
        for (JsonNode item : read("tabbar")) {
            var existing = jdbc.queryForList("SELECT id FROM relations.cms_tabbar_config WHERE code=?", String.class, item.path("code").asText());
            if (!existing.isEmpty()) continue;
            // 已有启用方案时，不替换当前选择；默认方案只作为可选项补入。
            boolean active = item.path("status").asInt() == 1 && jdbc.queryForObject("SELECT COUNT(*) FROM relations.cms_tabbar_config WHERE status=1", Integer.class) == 0;
            jdbc.update("INSERT INTO relations.cms_tabbar_config(id,code,name,config,status,active_key) VALUES (?,?,?," + jsonValue + ",?,?)",
                    UUID.randomUUID().toString(), item.path("code").asText(), item.path("name").asText(), item.path("config").toString(), active ? 1 : 0, active ? "active" : null);
        }
    }

    private void initializeManagement() {
        var permissionIds = new HashMap<String, Integer>();
        for (JsonNode item : read("permissions")) {
            String code = item.path("code").asText();
            jdbc.update("INSERT INTO relations.sys_permission(code,name) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM relations.sys_permission WHERE code=?)",
                    code, item.path("name").asText(), code);
            permissionIds.put(code, jdbc.queryForObject("SELECT id FROM relations.sys_permission WHERE code=?", Integer.class, code));
        }
        var menuIds = new HashMap<String, Integer>();
        for (JsonNode item : read("menus")) {
            String path = item.path("path").asText();
            var existing = jdbc.queryForList("SELECT id FROM relations.sys_menu WHERE path=?", Integer.class, path);
            if (existing.size() > 1) throw new BusinessException(409, "初始化菜单路径重复：" + path);
            Integer parent = item.path("parentPath").isNull() ? null : menuIds.get(item.path("parentPath").asText());
            if (existing.isEmpty()) {
                jdbc.update("INSERT INTO relations.sys_menu(parent_id,name,path,component,icon,sort,type,hidden) VALUES (?,?,?,?,?,?,?,FALSE)",
                        parent, item.path("name").asText(), path, item.path("component").asText(), item.path("icon").asText(), item.path("sort").asInt(), item.path("type").asText());
            }
            menuIds.put(path, jdbc.queryForObject("SELECT id FROM relations.sys_menu WHERE path=?", Integer.class, path));
        }
        for (JsonNode item : read("buttons")) {
            int menuId = menuIds.get(item.path("menuPath").asText());
            int permissionId = permissionIds.get(item.path("permissionCode").asText());
            jdbc.update("INSERT INTO relations.sys_menu_button(menu_id,permission_id,name,sort) SELECT ?,?,?,? WHERE NOT EXISTS "
                    + "(SELECT 1 FROM relations.sys_menu_button WHERE menu_id=? AND permission_id=?)",
                    menuId, permissionId, item.path("name").asText(), item.path("sort").asInt(), menuId, permissionId);
        }
        // 运营菜单与权限可在后台分配，不给已有角色自动增加授权。
    }

    private JsonNode read(String name) {
        try (var stream = new ClassPathResource("seeds/system-service/cms/" + name + ".json").getInputStream()) {
            return json.readTree(stream);
        } catch (IOException error) {
            throw new IllegalStateException("运营初始化数据读取失败：" + name, error);
        }
    }
}
