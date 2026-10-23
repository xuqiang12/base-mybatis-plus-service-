"""对真实 Java/数据库执行隔离账号验收；注册地址须为仅本机开放的验收实例。"""
import argparse
import concurrent.futures
import json
import secrets
import time
import urllib.error
import urllib.request
import urllib.parse
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--base', default='http://127.0.0.1:8000')
parser.add_argument('--registration-base', default='http://127.0.0.1:18080')
parser.add_argument('--session-file', required=True)
parser.add_argument('--report', required=True)
args = parser.parse_args()
checks = []


def request(path, token=None, data=None, family=None, expected=200, base=None, status_only=False):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if family:
        headers['X-Family-Id'] = family
    req = urllib.request.Request((base or args.base) + urllib.parse.quote(path, safe='/?=&%'), headers=headers,
                                 data=None if data is None else json.dumps(data).encode())
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            status, result = response.status, json.load(response)
    except urllib.error.HTTPError as error:
        status, result = error.code, json.load(error)
    assert status in (expected if isinstance(expected, tuple) else (expected,)), (path, status, result.get('message'))
    if status == 200:
        assert result['code'] == 0, (path, result.get('message'))
    return status if status_only else result.get('data')


def mark(name):
    checks.append(name)
    print('PASS:', name, flush=True)


def family(token):
    return request('/family/state', token)['family']['id']


def dataset(token):
    return request('/ledger/data', token)


stamp = time.strftime('%Y%m%d%H%M%S')
password = 'Ledger-' + secrets.token_urlsafe(18)
accounts = []
for index in range(3):
    email = f'ledger-e2e-{stamp}-{index}@example.test'
    request('/auth/email/register', data={'email': email, 'password': password, 'code': '123456',
                                         'nickname': f'礼账验收{index + 1}'}, base=args.registration_base)
    token = request('/auth/login', data={'account': email, 'password': password})['token']
    accounts.append({'email': email, 'token': token})
a, b, c = [item['token'] for item in accounts]
Path(args.session_file).write_text(json.dumps({'password': password, 'accounts': accounts}, ensure_ascii=False), encoding='utf-8')
mark('独立验收账号注册并通过正式实例密码登录（验证码投递不在本脚本范围）')

friend = request('/friend/create', a, {'name': '验收亲友', 'appellation': '同事', 'gender': 1, 'remark': '验收'})
fid = friend['id']
assert request('/friend/list?pageNum=1&pageSize=1&name=验收', a)['total'] == 1
assert request('/friend/detail?id=' + str(fid), a)['gender'] == 1
request('/friend/update', a, {**friend, 'name': '验收亲友改名'})
for path, body in [('/friend/update', {**friend, 'name': '跨家庭'}), ('/friend/delete', {'id': fid})]:
    request(path, c, body, expected=404)
request('/friend/detail?id=' + str(fid), c, expected=404)
mark('亲友数据库恢复、分页、搜索、详情、修改及跨家庭拒绝')

book = request('/ledger/book/create', a, {'name': '验收账簿', 'date': '2026-10-01', 'note': ''})
request('/ledger/book/update', a, {**book, 'name': '验收账簿改名'})
gift = {'eventType': 'wedding', 'direction': 'received', 'date': '2026-10-01', 'name': '验收亲友',
        'appellation': '同事', 'amountCents': 10000, 'note': '', 'friendId': fid, 'bookId': book['id']}
request('/ledger/gift/create', a, gift)
before = request('/friend/list', a)['total']
request('/ledger/gift/create', a, {**gift, 'friendId': None, 'name': '不能留下的亲友', 'bookId': 9223372036854775806}, expected=404)
assert request('/friend/list', a)['total'] == before
request('/ledger/gift/create', a, {**gift, 'friendId': None, 'name': '事务新亲友'})
request('/ledger/book/delete', c, {'id': book['id']}, expected=404)
request('/ledger/book/delete', a, {'id': book['id']})
assert len(dataset(a)['records']) == 2
assert all(item.get('bookId') is None for item in dataset(a)['records'])
mark('账簿增改删、流水保留、新亲友和人情同事务及失败回滚')

loan = {'friendId': fid, 'name': '验收亲友', 'appellation': '同事', 'date': '2026-10-01', 'note': ''}
request('/ledger/loan/create', a, {**loan, 'direction': 'lend', 'amountCents': 10000})
request('/ledger/loan/create', a, {**loan, 'direction': 'receive', 'amountCents': 3000})
request('/ledger/loan/create', a, {**loan, 'direction': 'receive', 'amountCents': 8000}, expected=409)
assert sum(item['amountCents'] * (1 if item['direction'] == 'lend' else -1)
           for item in dataset(a)['loanRecords'] if item['direction'] in ('lend', 'receive')) == 7000
request('/friend/delete', a, {'id': fid}, expected=409)
request('/ledger/loan/create', a, {**loan, 'direction': 'borrow', 'amountCents': 5000})
request('/ledger/loan/create', a, {**loan, 'direction': 'pay', 'amountCents': 2000})
request('/ledger/loan/create', a, {**loan, 'direction': 'pay', 'amountCents': 4000}, expected=409)
request('/ledger/loan/create', c, {**loan, 'direction': 'lend', 'amountCents': 1}, expected=404)
request('/ledger/loan/create', a, {**loan, 'direction': 'lend', 'amountCents': 0}, expected=400)
mark('借出100收回30剩70、超收80失败；借入归还、正数、归属、未结清删除限制')

categories = dataset(a)['householdCategories']
income = next(item for item in categories if item['name'] == '工资')
expense = next(item for item in categories if item['name'] == '餐饮')
saving = next(item for item in categories if item['type'] == 'saving')
items = [{'categoryId': cat['id'], 'type': cat['type'], 'amountCents': amount} for cat, amount in [(income, 500000), (expense, 12000), (saving, 100000)]]
first = request('/ledger/household/month/save', a, {'month': '2026-10', 'items': items})
items[1]['amountCents'] = 15000
second = request('/ledger/household/month/save', a, {'month': '2026-10', 'items': items})
assert first['id'] == second['id'] and len(second['items']) == 3
bad = [{**items[0], 'amountCents': 900000}, {**items[1], 'type': 'income'}]
request('/ledger/household/month/save', a, {'month': '2026-10', 'items': bad}, expected=400)
assert '900000' not in json.dumps(request('/ledger/household/month?month=2026-10', a))
request('/ledger/category/delete', a, {'id': expense['id']}, expected=409)
request('/ledger/household/month/save', c, {'month': '2026-10', 'items': items}, expected=404)
extra = request('/ledger/category/create', a, {'type': 'expense', 'name': '验收分类', 'icon': 'wallet'})
request('/ledger/category/update', a, {'id': extra['id'], 'name': '验收改名', 'icon': 'wallet', 'active': False})
request('/ledger/category/move', a, {'id': extra['id']})
request('/ledger/category/delete', a, {'id': extra['id']})
mark('同月同表多分类回填、原子事务、分类维护与删除限制')

old_b = family(b)
old_book = request('/ledger/book/create', b, {'name': '验收加入前历史'})
invitation = request('/family/invitation/share', a, {}, family(a))['token']
request('/family/invitation/accept', b, {'token': invitation}, old_b)
assert family(b) == family(a)
assert len(dataset(b)['records']) == 2
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    futures = [pool.submit(request, '/ledger/loan/create', token,
                           {**loan, 'direction': 'receive', 'amountCents': 5000},
                           expected=(200, 409), status_only=True) for token in (a, b)]
    assert sorted(future.result() for future in futures) == [200, 409]
mark('同家庭两成员并发收款仅一笔成功，未发生超额收款')
histories = request('/family/history/list', b)
assert any(item['id'] == old_b for item in histories)
assert request('/ledger/history/data?familyId=' + old_b, b)['books'][0]['id'] == old_book['id']
request('/ledger/history/data?familyId=' + old_b, a, expected=404)
request('/ledger/book/update', b, {**old_book, 'name': '不允许修改历史'}, expected=404)
request('/family/invitation/accept', c, {'token': invitation}, family(c), expected=400)
request('/ledger/book/create', b, {'name': '验收共享更新'})
assert any(item['name'] == '验收共享更新' for item in dataset(a)['books'])
request('/family/leave', b, {}, family(b))
assert not dataset(b)['books'] and not dataset(b)['records']
assert any(item['name'] == '验收共享更新' for item in dataset(a)['books'])
assert request('/ledger/history/data?familyId=' + old_b, b)['books']
mark('家庭共享刷新、邀请两人上限、历史仅原账号只读、退出新空家庭')

fresh = request('/auth/login', data={'account': accounts[0]['email'], 'password': password})['token']
assert len(dataset(fresh)['records']) == 2
assert len(dataset(fresh)['householdRecords']) == 3
mark('重新登录后从 PostgreSQL 恢复全部业务数据')
report = {'time': time.strftime('%Y-%m-%d %H:%M:%S'), 'service': args.base, 'accounts': [item['email'] for item in accounts],
          'passed': checks, 'unverified': ['真实邮件投递', '微信真机', '两台物理设备刷新']}
Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print('Report:', args.report)
