CREATE TABLE relations.cms_home_decoration (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    scene VARCHAR(32) NOT NULL DEFAULT 'homePage',
    status INTEGER NOT NULL DEFAULT 1 CHECK (status IN (0,1)),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE relations.cms_home_decoration IS '运营首页装修方案';
COMMENT ON COLUMN relations.cms_home_decoration.id IS '装修方案标识';
COMMENT ON COLUMN relations.cms_home_decoration.code IS '方案稳定编码，创建后不修改';
COMMENT ON COLUMN relations.cms_home_decoration.name IS '方案名称';
COMMENT ON COLUMN relations.cms_home_decoration.scene IS '适用场景，homePage 为首页';
COMMENT ON COLUMN relations.cms_home_decoration.status IS '状态，1启用0停用';
COMMENT ON COLUMN relations.cms_home_decoration.created_at IS '创建时间';
COMMENT ON COLUMN relations.cms_home_decoration.updated_at IS '更新时间';

CREATE TABLE relations.cms_home_component (
    id VARCHAR(64) PRIMARY KEY,
    decoration_id VARCHAR(64) NOT NULL REFERENCES relations.cms_home_decoration(id) ON DELETE CASCADE,
    template_id VARCHAR(32) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    info JSONB NOT NULL,
    sort_no INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 1 CHECK (status IN (0,1))
);
CREATE INDEX idx_cms_component_decoration ON relations.cms_home_component(decoration_id,sort_no);
COMMENT ON TABLE relations.cms_home_component IS '首页装修方案中的组件配置';
COMMENT ON COLUMN relations.cms_home_component.id IS '组件标识';
COMMENT ON COLUMN relations.cms_home_component.decoration_id IS '所属装修方案';
COMMENT ON COLUMN relations.cms_home_component.template_id IS '小程序组件模板编码';
COMMENT ON COLUMN relations.cms_home_component.template_name IS '组件名称';
COMMENT ON COLUMN relations.cms_home_component.info IS '组件属性对象';
COMMENT ON COLUMN relations.cms_home_component.sort_no IS '展示排序，数字越小越靠前';
COMMENT ON COLUMN relations.cms_home_component.status IS '状态，1启用0停用';

CREATE TABLE relations.cms_page_binding (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    page_key VARCHAR(64) NOT NULL UNIQUE,
    decoration_id VARCHAR(64) NOT NULL REFERENCES relations.cms_home_decoration(id),
    status INTEGER NOT NULL DEFAULT 1 CHECK (status IN (0,1)),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE relations.cms_page_binding IS '页面与装修方案的绑定';
COMMENT ON COLUMN relations.cms_page_binding.id IS '页面绑定标识';
COMMENT ON COLUMN relations.cms_page_binding.name IS '页面配置名称';
COMMENT ON COLUMN relations.cms_page_binding.page_key IS '页面稳定编码，home 为默认首页';
COMMENT ON COLUMN relations.cms_page_binding.decoration_id IS '绑定装修方案，存在绑定时禁止删除方案';
COMMENT ON COLUMN relations.cms_page_binding.status IS '状态，1启用0停用';
COMMENT ON COLUMN relations.cms_page_binding.created_at IS '创建时间';
COMMENT ON COLUMN relations.cms_page_binding.updated_at IS '更新时间';

CREATE TABLE relations.cms_tabbar_config (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    config JSONB NOT NULL,
    status INTEGER NOT NULL DEFAULT 0 CHECK (status IN (0,1)),
    active_key VARCHAR(20) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cms_tabbar_active CHECK ((status=1 AND active_key IS NOT NULL AND active_key='active') OR (status=0 AND active_key IS NULL))
);
COMMENT ON TABLE relations.cms_tabbar_config IS '小程序底部导航运营配置';
COMMENT ON COLUMN relations.cms_tabbar_config.id IS '导航方案标识';
COMMENT ON COLUMN relations.cms_tabbar_config.code IS '方案稳定编码，创建后不修改';
COMMENT ON COLUMN relations.cms_tabbar_config.name IS '方案名称';
COMMENT ON COLUMN relations.cms_tabbar_config.config IS '导航样式与入口列表';
COMMENT ON COLUMN relations.cms_tabbar_config.status IS '状态，1启用0停用，同时最多启用一个方案';
COMMENT ON COLUMN relations.cms_tabbar_config.active_key IS '启用槽位，固定active或空，唯一约束防止并发多启用';
COMMENT ON COLUMN relations.cms_tabbar_config.created_at IS '创建时间';
COMMENT ON COLUMN relations.cms_tabbar_config.updated_at IS '更新时间';
