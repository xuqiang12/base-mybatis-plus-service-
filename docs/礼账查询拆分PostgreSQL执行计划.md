# 礼账查询拆分 PostgreSQL 只读执行计划

验证时间：2026-10-29T22:28:03.533098300+08:00。数据库：PostgreSQL 18.6 (4e5fd08)。

连接开启 JDBC readOnly，并在只读事务内执行 SELECT 与 EXPLAIN (ANALYZE, BUFFERS)；最终回滚。未创建样本、索引或修改真实账目。选取现有记录最多的一个家庭，家庭 ID 已脱敏。第一次/第二次均为当前进程连续执行，未清理缓存，不能视为严格冷缓存。

- ledger_friend：5 条。
- ledger_book：1 条。
- ledger_gift：4 条。
- ledger_loan：3 条。
- ledger_household_item：17 条。
- ledger_money_project：3 条。

## household-page

```sql
SELECT h.*, COALESCE(h.record_date, CAST(m.month_value || '-01' AS DATE)) AS effective_date, c.name AS category_name,c.icon AS category_icon,c.active AS category_active,c.deleted_at AS category_deleted_at,p.name AS project_name,CASE WHEN p.cash_key='cash' THEN 'cash' ELSE p.type END AS project_type,g.name AS gift_name,g.appellation AS gift_appellation,g.event_type AS gift_event_type,b.name AS book_name FROM relations.ledger_household_item h JOIN relations.ledger_household_month m ON m.id=h.month_id AND m.family_id=h.family_id LEFT JOIN relations.ledger_category c ON c.id=h.category_id AND c.family_id=h.family_id LEFT JOIN relations.ledger_money_project p ON p.id=h.project_id AND p.family_id=h.family_id LEFT JOIN relations.ledger_gift g ON g.id=h.gift_id AND g.family_id=h.family_id LEFT JOIN relations.ledger_book b ON b.id=g.book_id AND b.family_id=h.family_id  WHERE h.family_id=? AND h.loan_id IS NULL AND COALESCE(h.record_date, CAST(m.month_value || '-01' AS DATE))>=? AND COALESCE(h.record_date, CAST(m.month_value || '-01' AS DATE))<? ORDER BY effective_date DESC,h.id DESC LIMIT 20 OFFSET 0
```

第一次：

```text
Limit  (cost=26.37..26.37 rows=1 width=1136) (actual time=0.151..0.155 rows=14.00 loops=1)
  Buffers: shared hit=87
  ->  Sort  (cost=26.37..26.37 rows=1 width=1136) (actual time=0.150..0.153 rows=14.00 loops=1)
        Sort Key: (COALESCE(h.record_date, (((m.month_value)::text || '-01'::text))::date)) DESC, h.id DESC
        Sort Method: quicksort  Memory: 28kB
        Buffers: shared hit=87
        ->  Nested Loop Left Join  (cost=6.15..26.36 rows=1 width=1136) (actual time=0.063..0.133 rows=14.00 loops=1)
              Join Filter: (b.id = g.book_id)
              Rows Removed by Join Filter: 10
              Buffers: shared hit=87
              ->  Nested Loop Left Join  (cost=6.01..18.18 rows=1 width=980) (actual time=0.057..0.108 rows=14.00 loops=1)
                    Buffers: shared hit=59
                    ->  Nested Loop Left Join  (cost=5.87..15.07 rows=1 width=478) (actual time=0.053..0.095 rows=14.00 loops=1)
                          Buffers: shared hit=51
                          ->  Nested Loop Left Join  (cost=5.73..11.97 rows=1 width=202) (actual time=0.047..0.075 rows=14.00 loops=1)
                                Buffers: shared hit=23
                                ->  Hash Join  (cost=5.59..11.00 rows=1 width=178) (actual time=0.044..0.057 rows=14.00 loops=1)
                                      Hash Cond: (m.id = h.month_id)
                                      Join Filter: ((COALESCE(h.record_date, (((m.month_value)::text || '-01'::text))::date) >= '2026-01-01'::date) AND (COALESCE(h.record_date, (((m.month_value)::text || '-01'::text))::date) < '2027-01-01'::date))
                                      Buffers: shared hit=3
                                      ->  Bitmap Heap Scan on ledger_household_month m  (cost=4.16..9.50 rows=2 width=218) (actual time=0.012..0.012 rows=1.00 loops=1)
                                            Recheck Cond: ((family_id)::text = '[家庭ID]'::text)
                                            Heap Blocks: exact=1
                                            Buffers: shared hit=2
                                            ->  Bitmap Index Scan on ledger_household_month_family_id_month_value_key  (cost=0.00..4.16 rows=2 width=0) (actual time=0.006..0.006 rows=1.00 loops=1)
                                                  Index Cond: ((family_id)::text = '[家庭ID]'::text)
                                                  Index Searches: 1
                                                  Buffers: shared hit=1
                                      ->  Hash  (cost=1.24..1.24 rows=15 width=146) (actual time=0.025..0.025 rows=14.00 loops=1)
                                            Buckets: 1024  Batches: 1  Memory Usage: 11kB
                                            Buffers: shared hit=1
                                            ->  Seq Scan on ledger_household_item h  (cost=0.00..1.24 rows=15 width=146) (actual time=0.015..0.018 rows=14.00 loops=1)
                                                  Filter: ((loan_id IS NULL) AND ((family_id)::text = '[家庭ID]'::text))
                                                  Rows Removed by Filter: 4
                                                  Buffers: shared hit=1
                                ->  Index Scan using ledger_category_pkey on ledger_category c  (cost=0.14..0.96 rows=1 width=69) (actual time=0.001..0.001 rows=0.71 loops=14)
                                      Index Cond: (id = h.category_id)
                                      Filter: ((family_id)::text = '[家庭ID]'::text)
                                      Index Searches: 10
                                      Buffers: shared hit=20
                          ->  Index Scan using ledger_money_project_pkey on ledger_money_project p  (cost=0.14..3.10 rows=1 width=462) (actual time=0.001..0.001 rows=1.00 loops=14)
                                Index Cond: (id = h.project_id)
                                Filter: ((family_id)::text = '[家庭ID]'::text)
                                Index Searches: 14
                                Buffers: shared hit=28
                    ->  Index Scan using ledger_gift_pkey on ledger_gift g  (cost=0.14..3.09 rows=1 width=688) (actual time=0.001..0.001 rows=0.29 loops=14)
                          Index Cond: (id = h.gift_id)
                          Filter: ((family_id)::text = '[家庭ID]'::text)
                          Index Searches: 4
                          Buffers: shared hit=8
              ->  Index Scan using idx_ledger_book_family on ledger_book b  (cost=0.14..8.16 rows=1 width=404) (actual time=0.001..0.001 rows=1.00 loops=14)
                    Index Cond: ((family_id)::text = '[家庭ID]'::text)
                    Index Searches: 14
                    Buffers: shared hit=28
Planning:
  Buffers: shared hit=2
Planning Time: 0.670 ms
Execution Time: 0.225 ms
```

第二次：Planning Time: 0.720 ms；Execution Time: 0.236 ms；

## household-summary

```sql
SELECT COUNT(*) AS count,COALESCE(SUM(CASE WHEN h.type='income' THEN h.amount_cents ELSE 0 END),0) AS income_cents,COALESCE(SUM(CASE WHEN h.type='expense' THEN h.amount_cents ELSE 0 END),0) AS expense_cents,COALESCE(SUM(CASE WHEN h.type='saving' THEN CASE WHEN h.saving_effect='subtract' THEN -h.amount_cents ELSE h.amount_cents END WHEN h.type='income' AND p.cash_key IS NULL AND p.id IS NOT NULL THEN h.amount_cents WHEN h.type='expense' AND p.cash_key IS NULL AND p.id IS NOT NULL THEN -h.amount_cents ELSE 0 END),0) AS saving_cents FROM relations.ledger_household_item h JOIN relations.ledger_household_month m ON m.id=h.month_id AND m.family_id=h.family_id LEFT JOIN relations.ledger_category c ON c.id=h.category_id AND c.family_id=h.family_id LEFT JOIN relations.ledger_money_project p ON p.id=h.project_id AND p.family_id=h.family_id LEFT JOIN relations.ledger_gift g ON g.id=h.gift_id AND g.family_id=h.family_id LEFT JOIN relations.ledger_book b ON b.id=g.book_id AND b.family_id=h.family_id  WHERE h.family_id=? AND h.loan_id IS NULL
```

第一次：

```text
Aggregate  (cost=13.64..13.65 rows=1 width=104) (actual time=0.094..0.095 rows=1.00 loops=1)
  Buffers: shared hit=31
  ->  Nested Loop Left Join  (cost=0.30..13.60 rows=1 width=47) (actual time=0.038..0.069 rows=14.00 loops=1)
        Buffers: shared hit=31
        ->  Nested Loop  (cost=0.16..10.50 rows=1 width=27) (actual time=0.034..0.047 rows=14.00 loops=1)
              Buffers: shared hit=3
              ->  Seq Scan on ledger_household_item h  (cost=0.00..1.24 rows=15 width=88) (actual time=0.016..0.019 rows=14.00 loops=1)
                    Filter: ((loan_id IS NULL) AND ((family_id)::text = '[家庭ID]'::text))
                    Rows Removed by Filter: 4
                    Buffers: shared hit=1
              ->  Memoize  (cost=0.16..3.11 rows=1 width=186) (actual time=0.002..0.002 rows=1.00 loops=14)
                    Cache Key: h.month_id
                    Cache Mode: logical
                    Hits: 13  Misses: 1  Evictions: 0  Overflows: 0  Memory Usage: 1kB
                    Buffers: shared hit=2
                    ->  Index Scan using ledger_household_month_pkey on ledger_household_month m  (cost=0.15..3.10 rows=1 width=186) (actual time=0.013..0.013 rows=1.00 loops=1)
                          Index Cond: (id = h.month_id)
                          Filter: ((family_id)::text = '[家庭ID]'::text)
                          Index Searches: 1
                          Buffers: shared hit=2
        ->  Index Scan using ledger_money_project_pkey on ledger_money_project p  (cost=0.14..3.10 rows=1 width=206) (actual time=0.001..0.001 rows=1.00 loops=14)
              Index Cond: (id = h.project_id)
              Filter: ((family_id)::text = '[家庭ID]'::text)
              Index Searches: 14
              Buffers: shared hit=28
Planning Time: 0.301 ms
Execution Time: 0.146 ms
```

第二次：Planning Time: 0.402 ms；Execution Time: 0.153 ms；

## gift-page

```sql
SELECT r.*,b.name AS book_name,p.name AS project_name FROM relations.ledger_gift r LEFT JOIN relations.ledger_book b ON b.id=r.book_id AND b.family_id=r.family_id LEFT JOIN relations.ledger_money_project p ON p.id=r.project_id AND p.family_id=r.family_id  WHERE r.family_id=? ORDER BY r.date DESC,r.id DESC LIMIT 20 OFFSET 0
```

第一次：

```text
Limit  (cost=24.51..24.56 rows=2 width=1944) (actual time=0.058..0.060 rows=4.00 loops=1)
  Buffers: shared hit=18
  ->  Incremental Sort  (cost=24.51..24.56 rows=2 width=1944) (actual time=0.057..0.058 rows=4.00 loops=1)
        Sort Key: r.date DESC, r.id DESC
        Presorted Key: r.date
        Full-sort Groups: 1  Sort Method: quicksort  Average Memory: 26kB  Peak Memory: 26kB
        Buffers: shared hit=18
        ->  Nested Loop Left Join  (cost=0.43..24.50 rows=1 width=1944) (actual time=0.037..0.048 rows=4.00 loops=1)
              Join Filter: (p.id = r.project_id)
              Rows Removed by Join Filter: 4
              Buffers: shared hit=18
              ->  Nested Loop Left Join  (cost=0.28..16.33 rows=1 width=1726) (actual time=0.029..0.034 rows=4.00 loops=1)
                    Join Filter: (b.id = r.book_id)
                    Buffers: shared hit=10
                    ->  Index Scan Backward using idx_ledger_gift_family_date on ledger_gift r  (cost=0.14..8.16 rows=1 width=1508) (actual time=0.021..0.022 rows=4.00 loops=1)
                          Index Cond: ((family_id)::text = '[家庭ID]'::text)
                          Index Searches: 1
                          Buffers: shared hit=2
                    ->  Index Scan using idx_ledger_book_family on ledger_book b  (cost=0.14..8.16 rows=1 width=404) (actual time=0.002..0.002 rows=1.00 loops=4)
                          Index Cond: ((family_id)::text = '[家庭ID]'::text)
                          Index Searches: 4
                          Buffers: shared hit=8
              ->  Index Scan using uq_ledger_money_family_cash on ledger_money_project p  (cost=0.14..8.16 rows=1 width=404) (actual time=0.002..0.003 rows=2.00 loops=4)
                    Index Cond: ((family_id)::text = '[家庭ID]'::text)
                    Index Searches: 4
                    Buffers: shared hit=8
Planning Time: 0.228 ms
Execution Time: 0.101 ms
```

第二次：Planning Time: 0.199 ms；Execution Time: 0.090 ms；

## book-page

```sql
SELECT b.*,COALESCE(g.record_count,0) AS record_count,COALESCE(g.received_cents,0) AS received_cents FROM relations.ledger_book b LEFT JOIN (SELECT book_id,COUNT(*) AS record_count,SUM(amount_cents) AS received_cents FROM relations.ledger_gift WHERE family_id=? AND direction='received' GROUP BY book_id) g ON g.book_id=b.id WHERE b.family_id=? ORDER BY b.created_at DESC,b.id DESC LIMIT 20 OFFSET 0
```

第一次：

```text
Limit  (cost=16.37..16.38 rows=1 width=980) (actual time=0.041..0.042 rows=1.00 loops=1)
  Buffers: shared hit=4
  ->  Sort  (cost=16.37..16.38 rows=1 width=980) (actual time=0.040..0.041 rows=1.00 loops=1)
        Sort Key: b.created_at DESC, b.id DESC
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared hit=4
        ->  Nested Loop Left Join  (cost=0.28..16.36 rows=1 width=980) (actual time=0.034..0.035 rows=1.00 loops=1)
              Join Filter: (ledger_gift.book_id = b.id)
              Buffers: shared hit=4
              ->  Index Scan using idx_ledger_book_family on ledger_book b  (cost=0.14..8.16 rows=1 width=940) (actual time=0.016..0.017 rows=1.00 loops=1)
                    Index Cond: ((family_id)::text = '[家庭ID]'::text)
                    Index Searches: 1
                    Buffers: shared hit=2
              ->  GroupAggregate  (cost=0.14..8.18 rows=1 width=48) (actual time=0.015..0.015 rows=1.00 loops=1)
                    Group Key: ledger_gift.book_id
                    Buffers: shared hit=2
                    ->  Index Scan using idx_ledger_gift_book on ledger_gift  (cost=0.14..8.16 rows=1 width=16) (actual time=0.007..0.009 rows=4.00 loops=1)
                          Index Cond: ((family_id)::text = '[家庭ID]'::text)
                          Filter: ((direction)::text = 'received'::text)
                          Index Searches: 1
                          Buffers: shared hit=2
Planning Time: 0.171 ms
Execution Time: 0.083 ms
```

第二次：Planning Time: 0.174 ms；Execution Time: 0.083 ms；

## friend-gifts

```sql
SELECT friend_id,SUM(CASE WHEN direction='received' THEN amount_cents ELSE 0 END) AS received_cents,SUM(CASE WHEN direction='sent' THEN amount_cents ELSE 0 END) AS sent_cents,COUNT(CASE WHEN direction='received' THEN 1 END) AS received_count,COUNT(CASE WHEN direction='sent' THEN 1 END) AS sent_count,MAX(date) AS last_date FROM relations.ledger_gift WHERE family_id=? AND friend_id IN (SELECT id FROM relations.ledger_friend WHERE family_id=? ORDER BY created_at DESC,id DESC LIMIT 20) GROUP BY friend_id
```

第一次：

```text
GroupAggregate  (cost=8.31..16.40 rows=1 width=92) (actual time=0.047..0.052 rows=4.00 loops=1)
  Group Key: ledger_gift.friend_id
  Buffers: shared hit=4
  ->  Nested Loop Semi Join  (cost=8.31..16.36 rows=1 width=58) (actual time=0.038..0.042 rows=4.00 loops=1)
        Join Filter: (ledger_gift.friend_id = ledger_friend.id)
        Rows Removed by Join Filter: 8
        Buffers: shared hit=4
        ->  Index Scan using idx_ledger_gift_friend on ledger_gift  (cost=0.14..8.16 rows=1 width=58) (actual time=0.018..0.020 rows=4.00 loops=1)
              Index Cond: ((family_id)::text = '[家庭ID]'::text)
              Index Searches: 1
              Buffers: shared hit=2
        ->  Limit  (cost=8.17..8.18 rows=1 width=16) (actual time=0.004..0.004 rows=3.00 loops=4)
              Buffers: shared hit=2
              ->  Sort  (cost=8.17..8.18 rows=1 width=16) (actual time=0.004..0.004 rows=3.00 loops=4)
                    Sort Key: ledger_friend.created_at DESC, ledger_friend.id DESC
                    Sort Method: quicksort  Memory: 25kB
                    Buffers: shared hit=2
                    ->  Index Scan using idx_ledger_friend_family on ledger_friend  (cost=0.14..8.16 rows=1 width=16) (actual time=0.006..0.009 rows=5.00 loops=1)
                          Index Cond: ((family_id)::text = '[家庭ID]'::text)
                          Index Searches: 1
                          Buffers: shared hit=2
Planning Time: 0.181 ms
Execution Time: 0.094 ms
```

第二次：Planning Time: 0.186 ms；Execution Time: 0.095 ms；

## project-balances

```sql
WITH targets AS (SELECT * FROM relations.ledger_money_project WHERE family_id=?),cash AS (SELECT id FROM relations.ledger_money_project WHERE family_id=? AND cash_key='cash'), deltas AS (SELECT project_id,SUM(CASE WHEN type='expense' THEN -amount_cents ELSE amount_cents END) AS amount FROM relations.ledger_household_item WHERE family_id=? AND (type IN ('income','expense') OR (type='saving' AND saving_transfer=TRUE)) AND project_id IN (SELECT id FROM targets) GROUP BY project_id UNION ALL SELECT (SELECT id FROM cash),-SUM(amount_cents) FROM relations.ledger_household_item WHERE family_id=? AND type='saving' AND saving_transfer=TRUE AND (SELECT id FROM cash) IN (SELECT id FROM targets) UNION ALL SELECT from_project_id,-SUM(amount_cents) FROM relations.ledger_money_transfer WHERE family_id=? AND from_project_id IN (SELECT id FROM targets) GROUP BY from_project_id UNION ALL SELECT to_project_id,SUM(amount_cents) FROM relations.ledger_money_transfer WHERE family_id=? AND to_project_id IN (SELECT id FROM targets) GROUP BY to_project_id UNION ALL SELECT project_id,SUM(amount_cents) FROM relations.ledger_money_adjustment WHERE family_id=? AND project_id IN (SELECT id FROM targets) GROUP BY project_id UNION ALL SELECT COALESCE(project_id,(SELECT id FROM cash)),SUM(CASE WHEN direction IN ('lend','pay') THEN -amount_cents ELSE amount_cents END) FROM relations.ledger_loan WHERE family_id=? AND COALESCE(project_id,(SELECT id FROM cash)) IN (SELECT id FROM targets) GROUP BY project_id), totals AS (SELECT project_id,SUM(amount) AS amount FROM deltas GROUP BY project_id) SELECT p.id,p.type,p.name,p.note,p.cash_key,p.initial_balance_cents+COALESCE(t.amount,0) AS balance FROM targets p LEFT JOIN totals t ON t.project_id=p.id ORDER BY CASE WHEN p.cash_key='cash' THEN 0 ELSE 1 END,p.id
```

第一次：

```text
Sort  (cost=52.81..52.81 rows=1 width=836) (actual time=0.217..0.222 rows=3.00 loops=1)
  Sort Key: (CASE WHEN ((p.cash_key)::text = 'cash'::text) THEN 0 ELSE 1 END), p.id
  Sort Method: quicksort  Memory: 25kB
  Buffers: shared hit=14
  CTE targets
    ->  Index Scan using uq_ledger_money_family_cash on ledger_money_project  (cost=0.14..8.16 rows=1 width=994) (actual time=0.018..0.021 rows=3.00 loops=1)
          Index Cond: ((family_id)::text = '[家庭ID]'::text)
          Index Searches: 1
          Buffers: shared hit=2
  CTE cash
    ->  Index Scan using uq_ledger_money_family_cash on ledger_money_project ledger_money_project_1  (cost=0.14..8.16 rows=1 width=8) (actual time=0.008..0.009 rows=1.00 loops=1)
          Index Cond: (((family_id)::text = '[家庭ID]'::text) AND ((cash_key)::text = 'cash'::text))
          Index Searches: 1
          Buffers: shared hit=2
  ->  Merge Left Join  (cost=27.79..36.47 rows=1 width=836) (actual time=0.198..0.209 rows=3.00 loops=1)
        Merge Cond: (p.id = ledger_household_item.project_id)
        Buffers: shared hit=14
        ->  Sort  (cost=0.03..0.04 rows=1 width=808) (actual time=0.036..0.036 rows=3.00 loops=1)
              Sort Key: p.id
              Sort Method: quicksort  Memory: 25kB
              Buffers: shared hit=2
              ->  CTE Scan on targets p  (cost=0.00..0.02 rows=1 width=808) (actual time=0.022..0.025 rows=3.00 loops=1)
                    Storage: Memory  Maximum Storage: 17kB
                    Buffers: shared hit=2
        ->  GroupAggregate  (cost=27.76..36.31 rows=9 width=40) (actual time=0.159..0.167 rows=3.00 loops=1)
              Group Key: ledger_household_item.project_id
              Buffers: shared hit=12
              ->  Merge Append  (cost=27.76..36.15 rows=9 width=40) (actual time=0.153..0.161 rows=6.00 loops=1)
                    Sort Key: ledger_household_item.project_id
                    Buffers: shared hit=12
                    ->  GroupAggregate  (cost=1.49..1.59 rows=4 width=40) (actual time=0.054..0.058 rows=3.00 loops=1)
                          Group Key: ledger_household_item.project_id
                          Buffers: shared hit=1
                          ->  Sort  (cost=1.49..1.50 rows=4 width=23) (actual time=0.047..0.049 rows=14.00 loops=1)
                                Sort Key: ledger_household_item.project_id
                                Sort Method: quicksort  Memory: 25kB
                                Buffers: shared hit=1
                                ->  Hash Semi Join  (cost=0.03..1.45 rows=4 width=23) (actual time=0.033..0.040 rows=14.00 loops=1)
                                      Hash Cond: (ledger_household_item.project_id = targets.id)
                                      Buffers: shared hit=1
                                      ->  Seq Scan on ledger_household_item  (cost=0.00..1.33 rows=14 width=23) (actual time=0.015..0.018 rows=14.00 loops=1)
                                            Filter: (((family_id)::text = '[家庭ID]'::text) AND (((type)::text = ANY ('{income,expense}'::text[])) OR (((type)::text = 'saving'::text) AND saving_transfer)))
                                            Rows Removed by Filter: 4
                                            Buffers: shared hit=1
                                      ->  Hash  (cost=0.02..0.02 rows=1 width=8) (actual time=0.004..0.005 rows=3.00 loops=1)
                                            Buckets: 1024  Batches: 1  Memory Usage: 9kB
                                            ->  CTE Scan on targets  (cost=0.00..0.02 rows=1 width=8) (actual time=0.000..0.001 rows=3.00 loops=1)
                                                  Storage: Memory  Maximum Storage: 17kB
                    ->  Sort  (cost=1.38..1.38 rows=1 width=40) (actual time=0.038..0.039 rows=1.00 loops=1)
                          Sort Key: ((InitPlan 3).col1)
                          Sort Method: quicksort  Memory: 25kB
                          Buffers: shared hit=3
                          ->  Aggregate  (cost=1.35..1.37 rows=1 width=40) (actual time=0.030..0.030 rows=1.00 loops=1)
                                Buffers: shared hit=3
                                InitPlan 3
                                  ->  CTE Scan on cash  (cost=0.00..0.02 rows=1 width=8) (actual time=0.000..0.000 rows=1.00 loops=1)
                                        Storage: Memory  Maximum Storage: 17kB
                                InitPlan 4
                                  ->  CTE Scan on cash cash_1  (cost=0.00..0.02 rows=1 width=8) (actual time=0.009..0.009 rows=1.00 loops=1)
                                        Storage: Memory  Maximum Storage: 17kB
                                        Buffers: shared hit=2
                                ->  Result  (cost=0.02..1.31 rows=1 width=8) (actual time=0.026..0.027 rows=1.00 loops=1)
                                      One-Time Filter: (ANY ((InitPlan 4).col1 = (hashed SubPlan 5).col1))
                                      Buffers: shared hit=3
                                      ->  Seq Scan on ledger_household_item ledger_household_item_1  (cost=0.02..1.31 rows=1 width=8) (actual time=0.007..0.008 rows=1.00 loops=1)
                                            Filter: (saving_transfer AND ((family_id)::text = '[家庭ID]'::text) AND ((type)::text = 'saving'::text))
                                            Rows Removed by Filter: 17
                                            Buffers: shared hit=1
                                      SubPlan 5
                                        ->  CTE Scan on targets targets_5  (cost=0.00..0.02 rows=1 width=8) (actual time=0.000..0.001 rows=3.00 loops=1)
                                              Storage: Memory  Maximum Storage: 17kB
                    ->  GroupAggregate  (cost=8.20..8.23 rows=1 width=40) (actual time=0.012..0.012 rows=0.00 loops=1)
                          Group Key: ledger_money_transfer.from_project_id
                          Buffers: shared hit=2
                          ->  Sort  (cost=8.20..8.21 rows=1 width=16) (actual time=0.011..0.012 rows=0.00 loops=1)
                                Sort Key: ledger_money_transfer.from_project_id
                                Sort Method: quicksort  Memory: 25kB
                                Buffers: shared hit=2
                                ->  Nested Loop Semi Join  (cost=0.14..8.19 rows=1 width=16) (actual time=0.006..0.006 rows=0.00 loops=1)
                                      Join Filter: (ledger_money_transfer.from_project_id = targets_1.id)
                                      Buffers: shared hit=2
                                      ->  Index Scan using idx_ledger_money_transfer_family on ledger_money_transfer  (cost=0.14..8.16 rows=1 width=16) (actual time=0.005..0.005 rows=0.00 loops=1)
                                            Index Cond: ((family_id)::text = '[家庭ID]'::text)
                                            Index Searches: 1
                                            Buffers: shared hit=2
                                      ->  CTE Scan on targets targets_1  (cost=0.00..0.02 rows=1 width=8) (never executed)
                                            Storage: Memory  Maximum Storage: 17kB
                    ->  GroupAggregate  (cost=8.20..8.22 rows=1 width=40) (actual time=0.011..0.011 rows=0.00 loops=1)
                          Group Key: ledger_money_transfer_1.to_project_id
                          Buffers: shared hit=2
                          ->  Sort  (cost=8.20..8.21 rows=1 width=16) (actual time=0.011..0.011 rows=0.00 loops=1)
                                Sort Key: ledger_money_transfer_1.to_project_id
                                Sort Method: quicksort  Memory: 25kB
                                Buffers: shared hit=2
                                ->  Nested Loop Semi Join  (cost=0.14..8.19 rows=1 width=16) (actual time=0.004..0.005 rows=0.00 loops=1)
                                      Join Filter: (ledger_money_transfer_1.to_project_id = targets_2.id)
                                      Buffers: shared hit=2
                                      ->  Index Scan using idx_ledger_money_transfer_family on ledger_money_transfer ledger_money_transfer_1  (cost=0.14..8.16 rows=1 width=16) (actual time=0.004..0.004 rows=0.00 loops=1)
                                            Index Cond: ((family_id)::text = '[家庭ID]'::text)
                                            Index Searches: 1
                                            Buffers: shared hit=2
                                      ->  CTE Scan on targets targets_2  (cost=0.00..0.02 rows=1 width=8) (never executed)
                                            Storage: Memory  Maximum Storage: 17kB
                    ->  GroupAggregate  (cost=0.14..8.21 rows=1 width=40) (actual time=0.013..0.014 rows=1.00 loops=1)
                          Group Key: ledger_money_adjustment.project_id
                          Buffers: shared hit=2
                          ->  Nested Loop Semi Join  (cost=0.14..8.19 rows=1 width=16) (actual time=0.012..0.012 rows=1.00 loops=1)
                                Join Filter: (ledger_money_adjustment.project_id = targets_3.id)
                                Rows Removed by Join Filter: 2
                                Buffers: shared hit=2
                                ->  Index Scan using idx_ledger_money_adjustment_family_project on ledger_money_adjustment  (cost=0.14..8.16 rows=1 width=16) (actual time=0.010..0.010 rows=1.00 loops=1)
                                      Index Cond: ((family_id)::text = '[家庭ID]'::text)
                                      Index Searches: 1
                                      Buffers: shared hit=2
                                ->  CTE Scan on targets targets_3  (cost=0.00..0.02 rows=1 width=8) (actual time=0.000..0.000 rows=3.00 loops=1)
                                      Storage: Memory  Maximum Storage: 17kB
                    ->  Sort  (cost=8.27..8.28 rows=1 width=40) (actual time=0.024..0.025 rows=1.00 loops=1)
                          Sort Key: "*SELECT* 6"."coalesce"
                          Sort Method: quicksort  Memory: 25kB
                          Buffers: shared hit=2
                          ->  Subquery Scan on "*SELECT* 6"  (cost=0.18..8.26 rows=1 width=40) (actual time=0.016..0.017 rows=1.00 loops=1)
                                Buffers: shared hit=2
                                ->  GroupAggregate  (cost=0.18..8.25 rows=1 width=48) (actual time=0.015..0.016 rows=1.00 loops=1)
                                      Group Key: ledger_loan.project_id
                                      Buffers: shared hit=2
                                      InitPlan 6
                                        ->  CTE Scan on cash cash_2  (cost=0.00..0.02 rows=1 width=8) (never executed)
                                              Storage: Memory  Maximum Storage: 17kB
                                      InitPlan 7
                                        ->  CTE Scan on cash cash_3  (cost=0.00..0.02 rows=1 width=8) (never executed)
                                              Storage: Memory  Maximum Storage: 17kB
                                      ->  Nested Loop Semi Join  (cost=0.14..8.19 rows=1 width=54) (actual time=0.010..0.013 rows=3.00 loops=1)
                                            Join Filter: (COALESCE(ledger_loan.project_id, (InitPlan 7).col1) = targets_4.id)
                                            Rows Removed by Join Filter: 6
                                            Buffers: shared hit=2
                                            ->  Index Scan using idx_ledger_loan_family_project on ledger_loan  (cost=0.14..8.16 rows=1 width=54) (actual time=0.009..0.010 rows=3.00 loops=1)
                                                  Index Cond: ((family_id)::text = '[家庭ID]'::text)
                                                  Index Searches: 1
                                                  Buffers: shared hit=2
                                            ->  CTE Scan on targets targets_4  (cost=0.00..0.02 rows=1 width=8) (actual time=0.000..0.000 rows=3.00 loops=3)
                                                  Storage: Memory  Maximum Storage: 17kB
Planning:
  Buffers: shared hit=4
Planning Time: 0.643 ms
Execution Time: 0.389 ms
```

第二次：Planning Time: 0.718 ms；Execution Time: 0.289 ms；

## 判断边界

执行计划证明当前 PostgreSQL 可以执行这些真实 SQL，并记录当前样本上的行数、排序、扫描与缓冲区数据。生产样本量小于隔离规模样本时，顺序扫描本身不能证明缺少索引；本轮不凭猜测增加迁移。H2 100/1,000/10,000 条验证只用于分页、固定响应体积与隔离环境请求耗时，不代替 PostgreSQL 大规模性能验证。
