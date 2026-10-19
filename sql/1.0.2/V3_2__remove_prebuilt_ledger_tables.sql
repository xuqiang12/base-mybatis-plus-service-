-- 保留账号、家庭及成员；移除预建的记账表，由用户后续逐表学习创建。
-- 先删除引用其他业务表的流水表，不使用 CASCADE。
DROP TABLE relations.ledger_gift;
DROP TABLE relations.ledger_loan;
DROP TABLE relations.ledger_household;
DROP TABLE relations.ledger_friend;
DROP TABLE relations.ledger_book;
DROP TABLE relations.ledger_category;
DROP TABLE relations.ledger_invitation;
