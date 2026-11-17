package com.base.seed;

import com.base.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemSeedService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Validator validator;
    private final PasswordEncoder passwords;

    // 版本占位和数据同事务提交；唯一主键同时保证并发 Seed 不会重复执行。
    @Transactional
    public int initialize(SeedAdminProperties admin) {
        int applied = 0;
        if (claim("system-base")) {
            initializeSystem();
            applied++;
        }
        if (claim("admin-account")) {
            initializeAdmin(admin);
            applied++;
        }
        return applied;
    }

    private boolean claim(String module) {
        return jdbc.update("INSERT INTO relations.sys_seed_history(module,version) VALUES (?,1) ON CONFLICT DO NOTHING", module) == 1;
    }

    private void initializeSystem() {
        var permissions = read("permissions", Permission[].class);
        var menus = read("menus", Menu[].class);
        var buttons = read("buttons", Button[].class);
        var roles = read("roles", Role[].class);
        var permissionIds = new HashMap<String, Integer>();
        for (Permission item : permissions) {
            jdbc.update("INSERT INTO relations.sys_permission(code,name) SELECT ?,? WHERE NOT EXISTS "
                    + "(SELECT 1 FROM relations.sys_permission WHERE code=?)", item.code(), item.name(), item.code());
            permissionIds.put(item.code(), jdbc.queryForObject("SELECT id FROM relations.sys_permission WHERE code=?", Integer.class, item.code()));
        }
        var menuIds = new HashMap<String, Integer>();
        for (Menu item : menus) {
            var existing = jdbc.queryForList("SELECT id FROM relations.sys_menu WHERE path=?", Integer.class, item.path());
            if (existing.size() > 1) throw new BusinessException(409, "初始化菜单路径重复：" + item.path());
            Integer parent = item.parentPath() == null ? null : menuIds.get(item.parentPath());
            if (item.parentPath() != null && parent == null) throw new IllegalStateException("初始化菜单缺少父目录");
            if (existing.isEmpty()) {
                jdbc.update("INSERT INTO relations.sys_menu(parent_id,name,path,component,icon,sort,type,hidden) VALUES (?,?,?,?,?,?,?,FALSE)",
                        parent, item.name(), item.path(), item.component(), item.icon(), item.sort(), item.type());
            }
            menuIds.put(item.path(), jdbc.queryForObject("SELECT id FROM relations.sys_menu WHERE path=?", Integer.class, item.path()));
        }
        for (Button item : buttons) {
            int menuId = menuIds.get(item.menuPath());
            int permissionId = permissionIds.get(item.permissionCode());
            jdbc.update("INSERT INTO relations.sys_menu_button(menu_id,permission_id,name,sort) SELECT ?,?,?,? WHERE NOT EXISTS "
                    + "(SELECT 1 FROM relations.sys_menu_button WHERE menu_id=? AND permission_id=?)",
                    menuId, permissionId, item.name(), item.sort(), menuId, permissionId);
        }
        for (Role role : roles) {
            var builtIn = jdbc.queryForList("SELECT id FROM relations.sys_role WHERE seed_code=?", Integer.class, role.code());
            if (!builtIn.isEmpty()) continue;
            var existing = jdbc.queryForList("SELECT id FROM relations.sys_role WHERE name=?", Integer.class, role.name());
            if (!existing.isEmpty()) {
                jdbc.update("UPDATE relations.sys_role SET seed_code=? WHERE id=?", role.code(), existing.getFirst());
                continue; // 接管已有角色时保留原授权，避免自动扩权。
            }
            jdbc.update("INSERT INTO relations.sys_role(name,seed_code) VALUES (?,?)", role.name(), role.code());
            int roleId = jdbc.queryForObject("SELECT id FROM relations.sys_role WHERE seed_code=?", Integer.class, role.code());
            for (String path : role.menuPaths()) jdbc.update("INSERT INTO relations.sys_menu_role(role_id,menu_id) VALUES (?,?)", roleId, menuIds.get(path));
            for (String code : role.permissionCodes()) jdbc.update("INSERT INTO relations.sys_role_permission(role_id,permission_id) VALUES (?,?)", roleId, permissionIds.get(code));
        }
    }

    private <T> T read(String name, Class<T> type) {
        try (var stream = new ClassPathResource("seeds/system-service/" + name + ".json").getInputStream()) {
            return json.readValue(stream, type);
        } catch (IOException error) {
            throw new IllegalStateException("初始化数据读取失败：" + name, error);
        }
    }

    private void initializeAdmin(SeedAdminProperties properties) {
        if (!validator.validate(properties).isEmpty()) throw new BusinessException(400, "请配置有效的 app.seed-admin 用户名、邮箱及8～72位密码");
        String username = properties.username().trim();
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        var matching = jdbc.queryForList("SELECT id,username,email,is_super_admin,is_deleted FROM relations.sys_user "
                + "WHERE username=? OR LOWER(email)=?", username, email);
        if (!matching.isEmpty()) {
            if (matching.size() != 1 || !username.equals(matching.getFirst().get("username"))
                    || !email.equals(matching.getFirst().get("email").toString().toLowerCase(Locale.ROOT))
                    || !Boolean.TRUE.equals(matching.getFirst().get("is_super_admin"))
                    || Boolean.TRUE.equals(matching.getFirst().get("is_deleted"))) {
                throw new BusinessException(409, "初始化管理员与已有账号冲突，请核对用户名、邮箱及管理员状态；不会自动提权或恢复账号");
            }
            return; // 已有管理员保留密码、资料及角色关系。
        }
        jdbc.update("INSERT INTO relations.sys_user(username,nickname,email,email_verified,password,is_super_admin,is_deleted) "
                + "VALUES (?,'超级管理员',?,FALSE,?,TRUE,FALSE)", username, email, passwords.encode(properties.password()));
        int userId = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username=?", Integer.class, username);
        // 保持现有创建账号时的家庭、成员及现金账户关系，不加载业务服务和 JWT。
        String familyId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO relations.ledger_family(id,name) VALUES (?,'我的家庭')", familyId);
        jdbc.update("INSERT INTO relations.ledger_money_project(family_id,type,name,cash_key) VALUES (?,'other','现金','cash')", familyId);
        jdbc.update("INSERT INTO relations.ledger_member(user_id,family_id) VALUES (?,?)", userId, familyId);
        jdbc.update("INSERT INTO relations.sys_user_role(user_id,role_id) SELECT ?,id FROM relations.sys_role WHERE seed_code='administrator'", userId);
    }

    private record Role(String code, String name, List<String> menuPaths, List<String> permissionCodes) {}
    private record Permission(String code, String name) {}
    private record Menu(String name, String path, String parentPath, String component, String icon, int sort, String type) {}
    private record Button(String menuPath, String permissionCode, String name, int sort) {}
}
