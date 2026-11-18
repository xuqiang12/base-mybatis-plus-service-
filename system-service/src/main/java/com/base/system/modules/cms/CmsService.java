package com.base.system.modules.cms;

import com.base.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 装修及其组件在同一事务完整替换，页面绑定决定客户端使用哪个方案。
@Service
public class CmsService {
    private static final Set<String> TEMPLATES = Set.of("homePageTitle", "navSingle", "advert", "notice", "cube",
            "typeList", "goodsWaterfall", "divider", "search", "navLinkage");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String jsonParameter;

    public CmsService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.jsonParameter = jdbc.execute((java.sql.Connection connection) ->
                "H2".equals(connection.getMetaData().getDatabaseProductName()) ? "? FORMAT JSON" : "CAST(? AS JSONB)");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<CmsModels.Home> homes() {
        return jdbc.query("SELECT * FROM relations.cms_home_decoration ORDER BY created_at,id", this::homeRow);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CmsModels.Home home(String id) {
        var rows = jdbc.query("SELECT * FROM relations.cms_home_decoration WHERE id=?", this::homeRow, id);
        if (rows.isEmpty()) throw new BusinessException(404, "装修方案不存在");
        return rows.getFirst();
    }

    @Transactional
    public CmsModels.Home saveHome(CmsModels.Home request) {
        String id = request.id() == null ? UUID.randomUUID().toString() : request.id();
        String code = request.code().trim();
        if (request.id() != null) {
            lockHome(id);
            if (!home(id).code().equals(code)) throw new BusinessException(400, "装修编码创建后不能修改");
        }
        String scene = request.scene() == null || request.scene().isBlank() ? "homePage" : request.scene().trim();
        try {
            if (request.id() == null) {
                jdbc.update("INSERT INTO relations.cms_home_decoration(id,code,name,scene,status) VALUES (?,?,?,?,?)",
                        id, code, request.name().trim(), scene, request.status());
            } else {
                jdbc.update("UPDATE relations.cms_home_decoration SET name=?,scene=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                        request.name().trim(), scene, request.status(), id);
            }
            // 先检查已有组件归属，避免提交另一方案的组件编号。
            var ids = new HashSet<String>();
            for (var component : request.components()) {
                if (component.id() != null && (!ids.add(component.id()) || jdbc.queryForObject(
                        "SELECT COUNT(*) FROM relations.cms_home_component WHERE id=? AND decoration_id<>?",
                        Integer.class, component.id(), id) > 0)) throw new BusinessException(400, "组件编号重复或属于其他方案");
            }
            jdbc.update("DELETE FROM relations.cms_home_component WHERE decoration_id=?", id);
            for (var component : request.components()) {
                if (!TEMPLATES.contains(component.templateId()) || !component.info().isObject()) {
                    throw new BusinessException(400, "组件模板不支持或内容不是对象");
                }
                jdbc.update("INSERT INTO relations.cms_home_component(id,decoration_id,template_id,template_name,info,sort_no,status) "
                                + "VALUES (?,?,?,?," + jsonParameter + ",?,?)",
                        component.id() == null ? UUID.randomUUID().toString() : component.id(), id, component.templateId(),
                        component.templateName() == null ? "" : component.templateName().trim(), component.info().toString(), component.sortNo(), component.status());
            }
        } catch (DataIntegrityViolationException error) {
            throw new BusinessException(409, "装修编码或组件编号已存在");
        }
        return home(id);
    }

    @Transactional
    public String deleteHome(String id) {
        lockHome(id);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM relations.cms_page_binding WHERE decoration_id=?", Integer.class, id) > 0) {
            throw new BusinessException(409, "装修方案已被页面绑定，不能删除");
        }
        try {
            jdbc.update("DELETE FROM relations.cms_home_component WHERE decoration_id=?", id);
            jdbc.update("DELETE FROM relations.cms_home_decoration WHERE id=?", id);
        } catch (DataIntegrityViolationException error) {
            throw new BusinessException(409, "装修方案已被页面绑定，不能删除");
        }
        return "装修方案删除成功";
    }

    public List<CmsModels.PageBinding> pages() {
        return jdbc.query("SELECT * FROM relations.cms_page_binding ORDER BY created_at,id", this::pageRow);
    }

    @Transactional
    public CmsModels.PageBinding savePage(CmsModels.PageBinding request) {
        String id = request.id() == null ? UUID.randomUUID().toString() : request.id();
        String pageKey = request.pageKey().trim();
        if (request.id() != null) {
            var existing = jdbc.query("SELECT * FROM relations.cms_page_binding WHERE id=? FOR UPDATE", this::pageRow, id);
            if (existing.isEmpty()) throw new BusinessException(404, "页面绑定不存在");
            if (!existing.getFirst().pageKey().equals(pageKey)) throw new BusinessException(400, "页面标识创建后不能修改");
        }
        lockHome(request.decorationId());
        try {
            if (request.id() == null) {
                jdbc.update("INSERT INTO relations.cms_page_binding(id,name,page_key,decoration_id,status) VALUES (?,?,?,?,?)",
                        id, request.name().trim(), pageKey, request.decorationId(), request.status());
            } else {
                jdbc.update("UPDATE relations.cms_page_binding SET name=?,decoration_id=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                        request.name().trim(), request.decorationId(), request.status(), id);
            }
        } catch (DataIntegrityViolationException error) {
            throw new BusinessException(409, "页面标识已存在或装修方案已变化");
        }
        return new CmsModels.PageBinding(id, request.name().trim(), pageKey, request.decorationId(), request.status());
    }

    public String deletePage(String id) {
        if (jdbc.update("DELETE FROM relations.cms_page_binding WHERE id=?", id) == 0) throw new BusinessException(404, "页面绑定不存在");
        return "页面绑定删除成功";
    }

    public List<CmsModels.Tabbar> tabbars() {
        return jdbc.query("SELECT * FROM relations.cms_tabbar_config ORDER BY created_at,id", this::tabbarRow);
    }

    public CmsModels.Tabbar tabbar(String id) {
        var rows = jdbc.query("SELECT * FROM relations.cms_tabbar_config WHERE id=?", this::tabbarRow, id);
        if (rows.isEmpty()) throw new BusinessException(404, "导航方案不存在");
        return rows.getFirst();
    }

    @Transactional
    public CmsModels.Tabbar saveTabbar(CmsModels.Tabbar request) {
        validateTabbar(request.config());
        String id = request.id() == null ? UUID.randomUUID().toString() : request.id();
        String code = request.code().trim();
        // 有现存方案时按固定顺序持锁；空表并发首次启用由 active_key 唯一约束兜底。
        jdbc.queryForList("SELECT id FROM relations.cms_tabbar_config ORDER BY id FOR UPDATE", String.class);
        if (request.id() != null && !tabbar(id).code().equals(code)) throw new BusinessException(400, "导航编码创建后不能修改");
        try {
            if (request.status() == 1) {
                jdbc.update("UPDATE relations.cms_tabbar_config SET status=0,active_key=NULL,updated_at=CURRENT_TIMESTAMP WHERE status=1 AND id<>?", id);
            }
            String active = request.status() == 1 ? "active" : null;
            if (request.id() == null) {
                jdbc.update("INSERT INTO relations.cms_tabbar_config(id,code,name,status,config,active_key) VALUES (?,?,?,?," + jsonParameter + ",?)",
                        id, code, request.name().trim(), request.status(), request.config().toString(), active);
            } else {
                jdbc.update("UPDATE relations.cms_tabbar_config SET name=?,status=?,config=" + jsonParameter + ",active_key=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                        request.name().trim(), request.status(), request.config().toString(), active, id);
            }
        } catch (DataIntegrityViolationException error) {
            throw new BusinessException(409, "导航编码已存在或启用状态已变化，请刷新后重试");
        }
        return tabbar(id);
    }

    public String deleteTabbar(String id) {
        if (jdbc.update("DELETE FROM relations.cms_tabbar_config WHERE id=?", id) == 0) throw new BusinessException(404, "导航方案不存在");
        return "导航方案删除成功";
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CmsModels.Home clientPage(String pageKey) {
        var ids = jdbc.queryForList("SELECT h.id FROM relations.cms_page_binding p JOIN relations.cms_home_decoration h ON h.id=p.decoration_id "
                + "WHERE p.page_key=? AND p.status=1 AND h.status=1", String.class, pageKey);
        if (ids.isEmpty()) throw new BusinessException(404, "页面暂无启用的装修方案");
        var home = home(ids.getFirst());
        return new CmsModels.Home(home.id(), home.code(), home.name(), home.scene(), home.status(),
                home.components().stream().filter(component -> component.status() == 1).toList());
    }

    public JsonNode clientTabbar() {
        var rows = jdbc.query("SELECT * FROM relations.cms_tabbar_config WHERE status=1", this::tabbarRow);
        if (rows.isEmpty()) throw new BusinessException(404, "暂无启用的导航方案");
        var tabbar = rows.getFirst();
        ObjectNode result = tabbar.config().deepCopy();
        result.put("id", tabbar.id()); result.put("name", tabbar.name());
        return result;
    }

    private void lockHome(String id) {
        if (jdbc.queryForList("SELECT id FROM relations.cms_home_decoration WHERE id=? FOR UPDATE", String.class, id).isEmpty()) {
            throw new BusinessException(404, "装修方案不存在");
        }
    }

    private CmsModels.Home homeRow(ResultSet row, int index) throws SQLException {
        String id = row.getString("id");
        var components = jdbc.query("SELECT * FROM relations.cms_home_component WHERE decoration_id=? ORDER BY sort_no,id", (component, i) ->
                new CmsModels.Component(component.getString("id"), component.getString("template_id"), component.getString("template_name"),
                        parse(component.getString("info")), component.getInt("sort_no"), component.getInt("status")), id);
        return new CmsModels.Home(id, row.getString("code"), row.getString("name"), row.getString("scene"), row.getInt("status"), components);
    }

    private CmsModels.PageBinding pageRow(ResultSet row, int index) throws SQLException {
        return new CmsModels.PageBinding(row.getString("id"), row.getString("name"), row.getString("page_key"), row.getString("decoration_id"), row.getInt("status"));
    }

    private CmsModels.Tabbar tabbarRow(ResultSet row, int index) throws SQLException {
        return new CmsModels.Tabbar(row.getString("id"), row.getString("code"), row.getString("name"), row.getInt("status"), parse(row.getString("config")));
    }

    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("CMS JSON 数据无效", error); }
    }

    private void validateTabbar(JsonNode config) {
        if (!config.isObject()) throw new BusinessException(400, "导航配置必须是对象");
        choice(config, "tabBarMode", Set.of("custom", "native", "hidden"));
        choice(config, "bgColorMode", Set.of("system", "custom"));
        choice(config, "textColorMode", Set.of("system", "custom"));
        choice(config, "radiusMode", Set.of("square", "round", "largeRound"));
        for (String key : List.of("bgColor", "textColor", "activeColor")) {
            if (!text(config, key).matches("#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) throw new BusinessException(400, "导航颜色格式无效");
        }
        pagePath(text(config, "rootPagePath"));
        JsonNode items = config.path("items");
        if (!items.isArray() || ("custom".equals(config.path("tabBarMode").asText()) && items.isEmpty())) throw new BusinessException(400, "导航项目列表无效");
        var ids = new HashSet<String>();
        for (JsonNode item : items) {
            if (!item.isObject() || !ids.add(text(item, "id"))) throw new BusinessException(400, "导航项目编号重复或无效");
            text(item, "name");
            choice(item, "linkType", Set.of("page"));
            pagePath(text(item, "pagePath"));
            icon(text(item, "icon")); icon(text(item, "activeIcon"));
            if (!item.path("sortNo").isIntegralNumber() || !item.path("sortNo").canConvertToInt() || item.path("sortNo").asInt() < 0) {
                throw new BusinessException(400, "导航排序必须是非负整数");
            }
        }
    }

    private String text(JsonNode object, String key) {
        JsonNode value = object.path(key);
        if (!value.isTextual() || value.asText().isBlank()) throw new BusinessException(400, "导航字段 " + key + " 不能为空");
        return value.asText();
    }

    private void choice(JsonNode object, String key, Set<String> choices) {
        if (!choices.contains(text(object, key))) throw new BusinessException(400, "导航字段 " + key + " 无效");
    }

    private void pagePath(String path) {
        if (!path.matches("/(?!/)[^\\s?#:\\\\]+") || path.contains("..")) throw new BusinessException(400, "导航必须使用合法内部页面路径");
    }

    private void icon(String path) {
        try {
            URI uri = URI.create(path);
            if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null) return;
        } catch (IllegalArgumentException error) {
            throw new BusinessException(400, "导航图标地址无效");
        }
        if (!path.matches("/?static/[^\\s?#:\\\\]+") || path.contains("..")) throw new BusinessException(400, "导航图标必须使用 static 路径或 HTTP 图片地址");
    }
}
