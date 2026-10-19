-- 家庭成员、邀请和账目；隐藏历史不占当前成员位置。
CREATE TABLE relations.ledger_family (
    id VARCHAR(80) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    hidden_owner INTEGER REFERENCES relations.sys_user(id)
);

CREATE TABLE relations.ledger_member (
    user_id INTEGER PRIMARY KEY REFERENCES relations.sys_user(id),
    family_id VARCHAR(80) NOT NULL REFERENCES relations.ledger_family(id)
);

CREATE INDEX idx_ledger_member_family ON relations.ledger_member(family_id);
CREATE TABLE relations.ledger_invitation (
    code VARCHAR(80) PRIMARY KEY,
    family_id VARCHAR(80) NOT NULL REFERENCES relations.ledger_family(id),
    inviter_id VARCHAR(80) NOT NULL,
    history_choice VARCHAR(10) NOT NULL CHECK(history_choice IN ('public','hide','clear')),
    expires_at BIGINT NOT NULL,
    status VARCHAR(10) NOT NULL CHECK(status IN ('pending','used','revoked'))
);

CREATE INDEX idx_ledger_invitation_family ON relations.ledger_invitation(family_id);
CREATE TABLE relations.ledger_friend (
    id VARCHAR(80) PRIMARY KEY,
    family_id VARCHAR(80) NOT NULL REFERENCES relations.ledger_family(id),
    origin_id VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    appellation VARCHAR(100) NOT NULL DEFAULT '',
    gender VARCHAR(10) NOT NULL DEFAULT '',
    note VARCHAR(1000) NOT NULL DEFAULT '',
    UNIQUE(family_id, origin_id),
    UNIQUE(family_id, id)
);

CREATE TABLE relations.ledger_book (
    id VARCHAR(80) PRIMARY KEY,
    family_id VARCHAR(80) NOT NULL REFERENCES relations.ledger_family(id),
    origin_id VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    date DATE NOT NULL,
    note VARCHAR(1000) NOT NULL DEFAULT '',
    UNIQUE(family_id, origin_id),
    UNIQUE(family_id, id)
);

CREATE TABLE relations.ledger_gift (
    id VARCHAR(80) PRIMARY KEY,
    family_id VARCHAR(80) NOT NULL REFERENCES relations.ledger_family(id),
    origin_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    direction VARCHAR(10) NOT NULL CHECK(direction IN ('received','sent')),
    date DATE NOT NULL,
    name VARCHAR(100) NOT NULL,
    appellation VARCHAR(100) NOT NULL DEFAULT '',
    amount_cents BIGINT NOT NULL CHECK(amount_cents BETWEEN 1 AND 9007199254740991),
    note VARCHAR(1000) NOT NULL DEFAULT '',
    book_id VARCHAR(80),
    friend_id VARCHAR(80),
    created_by VARCHAR(80) NOT NULL,
    created_by_name VARCHAR(100) NOT NULL,
    UNIQUE(family_id, origin_id),
    UNIQUE(family_id, id)
);

CREATE TABLE relations.ledger_loan (
    id VARCHAR(80) PRIMARY KEY,
    family_id VARCHAR(80) NOT NULL REFERENCES relations.ledger_family(id),
    origin_id VARCHAR(100) NOT NULL,
    direction VARCHAR(10) NOT NULL CHECK(direction IN ('lend','borrow','receive','pay')),
    friend_id VARCHAR(80),
    name VARCHAR(100) NOT NULL,
    appellation VARCHAR(100) NOT NULL DEFAULT '',
    amount_cents BIGINT NOT NULL CHECK(amount_cents BETWEEN 1 AND 9007199254740991),
    date DATE NOT NULL,
    note VARCHAR(1000) NOT NULL DEFAULT '',
    created_by VARCHAR(80) NOT NULL,
    created_by_name VARCHAR(100) NOT NULL,
    UNIQUE(family_id, origin_id),
    UNIQUE(family_id, id)
);

CREATE TABLE relations.ledger_category (
    id VARCHAR(80) PRIMARY KEY,
    family_id VARCHAR(80) NOT NULL REFERENCES relations.ledger_family(id),
    origin_id VARCHAR(100) NOT NULL,
    type VARCHAR(10) NOT NULL CHECK(type IN ('expense','income','saving')),
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    UNIQUE(family_id, origin_id),
    UNIQUE(family_id, id)
);

CREATE TABLE relations.ledger_household (
    id VARCHAR(80) PRIMARY KEY,
    family_id VARCHAR(80) NOT NULL REFERENCES relations.ledger_family(id),
    origin_id VARCHAR(100) NOT NULL,
    type VARCHAR(10) NOT NULL CHECK(type IN ('expense','income','saving')),
    category_id VARCHAR(80) NOT NULL,
    amount_cents BIGINT NOT NULL CHECK(amount_cents BETWEEN 1 AND 9007199254740991),
    date DATE NOT NULL,
    note VARCHAR(1000) NOT NULL DEFAULT '',
    created_by VARCHAR(80) NOT NULL,
    created_by_name VARCHAR(100) NOT NULL,
    UNIQUE(family_id, origin_id),
    UNIQUE(family_id, id)
);

ALTER TABLE relations.ledger_gift ADD CONSTRAINT fk_gift_friend_id FOREIGN KEY(family_id,friend_id) REFERENCES relations.ledger_friend(family_id,id);
ALTER TABLE relations.ledger_gift ADD CONSTRAINT fk_gift_book_id FOREIGN KEY(family_id,book_id) REFERENCES relations.ledger_book(family_id,id);
ALTER TABLE relations.ledger_loan ADD CONSTRAINT fk_loan_friend_id FOREIGN KEY(family_id,friend_id) REFERENCES relations.ledger_friend(family_id,id);
ALTER TABLE relations.ledger_household ADD CONSTRAINT fk_household_category_id FOREIGN KEY(family_id,category_id) REFERENCES relations.ledger_category(family_id,id);
INSERT INTO relations.ledger_family(id,name)
SELECT 'initial-' || CAST(id AS VARCHAR), '我的家庭'
FROM relations.sys_user WHERE is_deleted=FALSE;
INSERT INTO relations.ledger_member(user_id,family_id)
SELECT id,'initial-' || CAST(id AS VARCHAR)
FROM relations.sys_user WHERE is_deleted=FALSE;
INSERT INTO relations.ledger_category(id,family_id,origin_id,type,name,icon,active,sort_order)
SELECT 'initial-expense-food-' || CAST(id AS VARCHAR), 'initial-' || CAST(id AS VARCHAR), 'initial-expense-food-' || CAST(id AS VARCHAR), 'expense', '餐饮', 'fork', TRUE, 0
FROM relations.sys_user WHERE is_deleted=FALSE;
INSERT INTO relations.ledger_category(id,family_id,origin_id,type,name,icon,active,sort_order)
SELECT 'initial-expense-rent-' || CAST(id AS VARCHAR), 'initial-' || CAST(id AS VARCHAR), 'initial-expense-rent-' || CAST(id AS VARCHAR), 'expense', '房租', 'home', TRUE, 1
FROM relations.sys_user WHERE is_deleted=FALSE;
INSERT INTO relations.ledger_category(id,family_id,origin_id,type,name,icon,active,sort_order)
SELECT 'initial-expense-mortgage-' || CAST(id AS VARCHAR), 'initial-' || CAST(id AS VARCHAR), 'initial-expense-mortgage-' || CAST(id AS VARCHAR), 'expense', '房贷', 'home', TRUE, 2
FROM relations.sys_user WHERE is_deleted=FALSE;
INSERT INTO relations.ledger_category(id,family_id,origin_id,type,name,icon,active,sort_order)
SELECT 'initial-expense-credit-' || CAST(id AS VARCHAR), 'initial-' || CAST(id AS VARCHAR), 'initial-expense-credit-' || CAST(id AS VARCHAR), 'expense', '还花呗', 'creditcard', TRUE, 3
FROM relations.sys_user WHERE is_deleted=FALSE;
INSERT INTO relations.ledger_category(id,family_id,origin_id,type,name,icon,active,sort_order)
SELECT 'initial-expense-parent-' || CAST(id AS VARCHAR), 'initial-' || CAST(id AS VARCHAR), 'initial-expense-parent-' || CAST(id AS VARCHAR), 'expense', '父母养老', 'heart', TRUE, 4
FROM relations.sys_user WHERE is_deleted=FALSE;
INSERT INTO relations.ledger_category(id,family_id,origin_id,type,name,icon,active,sort_order)
SELECT 'initial-expense-daily-' || CAST(id AS VARCHAR), 'initial-' || CAST(id AS VARCHAR), 'initial-expense-daily-' || CAST(id AS VARCHAR), 'expense', '日用', 'shop', TRUE, 5
FROM relations.sys_user WHERE is_deleted=FALSE;
INSERT INTO relations.ledger_category(id,family_id,origin_id,type,name,icon,active,sort_order)
SELECT 'initial-expense-other-' || CAST(id AS VARCHAR), 'initial-' || CAST(id AS VARCHAR), 'initial-expense-other-' || CAST(id AS VARCHAR), 'expense', '其他', 'wallet', TRUE, 6
FROM relations.sys_user WHERE is_deleted=FALSE;
INSERT INTO relations.ledger_category(id,family_id,origin_id,type,name,icon,active,sort_order)
SELECT 'initial-income-salary-' || CAST(id AS VARCHAR), 'initial-' || CAST(id AS VARCHAR), 'initial-income-salary-' || CAST(id AS VARCHAR), 'income', '工资', 'money', TRUE, 7
FROM relations.sys_user WHERE is_deleted=FALSE;
INSERT INTO relations.ledger_category(id,family_id,origin_id,type,name,icon,active,sort_order)
SELECT 'initial-income-extra-' || CAST(id AS VARCHAR), 'initial-' || CAST(id AS VARCHAR), 'initial-income-extra-' || CAST(id AS VARCHAR), 'income', '额外收入', 'money', TRUE, 8
FROM relations.sys_user WHERE is_deleted=FALSE;
INSERT INTO relations.ledger_category(id,family_id,origin_id,type,name,icon,active,sort_order)
SELECT 'initial-income-other-' || CAST(id AS VARCHAR), 'initial-' || CAST(id AS VARCHAR), 'initial-income-other-' || CAST(id AS VARCHAR), 'income', '其他收入', 'wallet', TRUE, 9
FROM relations.sys_user WHERE is_deleted=FALSE;
INSERT INTO relations.ledger_category(id,family_id,origin_id,type,name,icon,active,sort_order)
SELECT 'initial-saving-deposit-' || CAST(id AS VARCHAR), 'initial-' || CAST(id AS VARCHAR), 'initial-saving-deposit-' || CAST(id AS VARCHAR), 'saving', '存款', 'wallet', TRUE, 10
FROM relations.sys_user WHERE is_deleted=FALSE;

ALTER TABLE relations.ledger_family ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE relations.ledger_family ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
COMMENT ON TABLE relations.ledger_family IS '家庭及隐藏历史归属';
COMMENT ON COLUMN relations.ledger_family.id IS '主键，字符串 UUID 或迁移来源标识';
COMMENT ON COLUMN relations.ledger_family.name IS '名称快照或档案名称';
COMMENT ON COLUMN relations.ledger_family.hidden_owner IS '隐藏历史所有者，为空时不作为隐藏历史';
COMMENT ON COLUMN relations.ledger_family.created_at IS '数据库创建时间';
COMMENT ON COLUMN relations.ledger_family.updated_at IS '数据库最后更新时间';

ALTER TABLE relations.ledger_member ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE relations.ledger_member ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
COMMENT ON TABLE relations.ledger_member IS '当前家庭成员，一个账号只属于一个当前家庭';
COMMENT ON COLUMN relations.ledger_member.user_id IS '当前成员账号 ID';
COMMENT ON COLUMN relations.ledger_member.family_id IS '所属家庭 ID';
COMMENT ON COLUMN relations.ledger_member.created_at IS '数据库创建时间';
COMMENT ON COLUMN relations.ledger_member.updated_at IS '数据库最后更新时间';

ALTER TABLE relations.ledger_invitation ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE relations.ledger_invitation ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
COMMENT ON TABLE relations.ledger_invitation IS '一次性家庭邀请';
COMMENT ON COLUMN relations.ledger_invitation.code IS '8位邀请码';
COMMENT ON COLUMN relations.ledger_invitation.family_id IS '所属家庭 ID';
COMMENT ON COLUMN relations.ledger_invitation.inviter_id IS '邀请者账号 ID';
COMMENT ON COLUMN relations.ledger_invitation.history_choice IS '加入成功时处理旧历史的选择';
COMMENT ON COLUMN relations.ledger_invitation.expires_at IS '失效时间，Unix 毫秒';
COMMENT ON COLUMN relations.ledger_invitation.status IS '邀请状态';
COMMENT ON COLUMN relations.ledger_invitation.created_at IS '数据库创建时间';
COMMENT ON COLUMN relations.ledger_invitation.updated_at IS '数据库最后更新时间';

ALTER TABLE relations.ledger_friend ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE relations.ledger_friend ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
COMMENT ON TABLE relations.ledger_friend IS '亲友档案';
COMMENT ON COLUMN relations.ledger_friend.id IS '主键，字符串 UUID 或迁移来源标识';
COMMENT ON COLUMN relations.ledger_friend.family_id IS '所属家庭 ID';
COMMENT ON COLUMN relations.ledger_friend.origin_id IS '复制来源版本，用于相同历史去重';
COMMENT ON COLUMN relations.ledger_friend.name IS '名称快照或档案名称';
COMMENT ON COLUMN relations.ledger_friend.appellation IS '称谓快照';
COMMENT ON COLUMN relations.ledger_friend.gender IS '性别';
COMMENT ON COLUMN relations.ledger_friend.note IS '备注';
COMMENT ON COLUMN relations.ledger_friend.created_at IS '数据库创建时间';
COMMENT ON COLUMN relations.ledger_friend.updated_at IS '数据库最后更新时间';

ALTER TABLE relations.ledger_book ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE relations.ledger_book ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
COMMENT ON TABLE relations.ledger_book IS '礼簿档案';
COMMENT ON COLUMN relations.ledger_book.id IS '主键，字符串 UUID 或迁移来源标识';
COMMENT ON COLUMN relations.ledger_book.family_id IS '所属家庭 ID';
COMMENT ON COLUMN relations.ledger_book.origin_id IS '复制来源版本，用于相同历史去重';
COMMENT ON COLUMN relations.ledger_book.name IS '名称快照或档案名称';
COMMENT ON COLUMN relations.ledger_book.date IS '业务日期';
COMMENT ON COLUMN relations.ledger_book.note IS '备注';
COMMENT ON COLUMN relations.ledger_book.created_at IS '数据库创建时间';
COMMENT ON COLUMN relations.ledger_book.updated_at IS '数据库最后更新时间';

ALTER TABLE relations.ledger_gift ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE relations.ledger_gift ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
COMMENT ON TABLE relations.ledger_gift IS '人情流水';
COMMENT ON COLUMN relations.ledger_gift.id IS '主键，字符串 UUID 或迁移来源标识';
COMMENT ON COLUMN relations.ledger_gift.family_id IS '所属家庭 ID';
COMMENT ON COLUMN relations.ledger_gift.origin_id IS '复制来源版本，用于相同历史去重';
COMMENT ON COLUMN relations.ledger_gift.event_type IS '人情事件类型';
COMMENT ON COLUMN relations.ledger_gift.direction IS '收送或借贷方向';
COMMENT ON COLUMN relations.ledger_gift.date IS '业务日期';
COMMENT ON COLUMN relations.ledger_gift.name IS '名称快照或档案名称';
COMMENT ON COLUMN relations.ledger_gift.appellation IS '称谓快照';
COMMENT ON COLUMN relations.ledger_gift.amount_cents IS '金额，整数分且不超过 JS 安全整数';
COMMENT ON COLUMN relations.ledger_gift.note IS '备注';
COMMENT ON COLUMN relations.ledger_gift.book_id IS '当前家庭礼簿关联，可空';
COMMENT ON COLUMN relations.ledger_gift.friend_id IS '当前家庭亲友关联，可空';
COMMENT ON COLUMN relations.ledger_gift.created_by IS '最初记录作者账号 ID，分开时保留';
COMMENT ON COLUMN relations.ledger_gift.created_by_name IS '最初记录作者昵称快照';
COMMENT ON COLUMN relations.ledger_gift.created_at IS '数据库创建时间';
COMMENT ON COLUMN relations.ledger_gift.updated_at IS '数据库最后更新时间';

ALTER TABLE relations.ledger_loan ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE relations.ledger_loan ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
COMMENT ON TABLE relations.ledger_loan IS '借贷流水';
COMMENT ON COLUMN relations.ledger_loan.id IS '主键，字符串 UUID 或迁移来源标识';
COMMENT ON COLUMN relations.ledger_loan.family_id IS '所属家庭 ID';
COMMENT ON COLUMN relations.ledger_loan.origin_id IS '复制来源版本，用于相同历史去重';
COMMENT ON COLUMN relations.ledger_loan.direction IS '收送或借贷方向';
COMMENT ON COLUMN relations.ledger_loan.friend_id IS '当前家庭亲友关联，可空';
COMMENT ON COLUMN relations.ledger_loan.name IS '名称快照或档案名称';
COMMENT ON COLUMN relations.ledger_loan.appellation IS '称谓快照';
COMMENT ON COLUMN relations.ledger_loan.amount_cents IS '金额，整数分且不超过 JS 安全整数';
COMMENT ON COLUMN relations.ledger_loan.date IS '业务日期';
COMMENT ON COLUMN relations.ledger_loan.note IS '备注';
COMMENT ON COLUMN relations.ledger_loan.created_by IS '最初记录作者账号 ID，分开时保留';
COMMENT ON COLUMN relations.ledger_loan.created_by_name IS '最初记录作者昵称快照';
COMMENT ON COLUMN relations.ledger_loan.created_at IS '数据库创建时间';
COMMENT ON COLUMN relations.ledger_loan.updated_at IS '数据库最后更新时间';

ALTER TABLE relations.ledger_category ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE relations.ledger_category ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
COMMENT ON TABLE relations.ledger_category IS '家庭收支分类';
COMMENT ON COLUMN relations.ledger_category.id IS '主键，字符串 UUID 或迁移来源标识';
COMMENT ON COLUMN relations.ledger_category.family_id IS '所属家庭 ID';
COMMENT ON COLUMN relations.ledger_category.origin_id IS '复制来源版本，用于相同历史去重';
COMMENT ON COLUMN relations.ledger_category.type IS '收入、支出或存款类型';
COMMENT ON COLUMN relations.ledger_category.name IS '名称快照或档案名称';
COMMENT ON COLUMN relations.ledger_category.icon IS '分类图标';
COMMENT ON COLUMN relations.ledger_category.active IS '是否启用';
COMMENT ON COLUMN relations.ledger_category.sort_order IS '分类顺序';
COMMENT ON COLUMN relations.ledger_category.created_at IS '数据库创建时间';
COMMENT ON COLUMN relations.ledger_category.updated_at IS '数据库最后更新时间';

ALTER TABLE relations.ledger_household ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE relations.ledger_household ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
COMMENT ON TABLE relations.ledger_household IS '家庭收支流水';
COMMENT ON COLUMN relations.ledger_household.id IS '主键，字符串 UUID 或迁移来源标识';
COMMENT ON COLUMN relations.ledger_household.family_id IS '所属家庭 ID';
COMMENT ON COLUMN relations.ledger_household.origin_id IS '复制来源版本，用于相同历史去重';
COMMENT ON COLUMN relations.ledger_household.type IS '收入、支出或存款类型';
COMMENT ON COLUMN relations.ledger_household.category_id IS '当前家庭分类关联';
COMMENT ON COLUMN relations.ledger_household.amount_cents IS '金额，整数分且不超过 JS 安全整数';
COMMENT ON COLUMN relations.ledger_household.date IS '业务日期';
COMMENT ON COLUMN relations.ledger_household.note IS '备注';
COMMENT ON COLUMN relations.ledger_household.created_by IS '最初记录作者账号 ID，分开时保留';
COMMENT ON COLUMN relations.ledger_household.created_by_name IS '最初记录作者昵称快照';
COMMENT ON COLUMN relations.ledger_household.created_at IS '数据库创建时间';
COMMENT ON COLUMN relations.ledger_household.updated_at IS '数据库最后更新时间';
