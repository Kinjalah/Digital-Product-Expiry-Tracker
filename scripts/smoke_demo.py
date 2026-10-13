"""Exercise the running packaged application. Exit nonzero on any regression."""
import json
import os
from datetime import date, timedelta
from pathlib import Path
from urllib.request import Request, build_opener, ProxyHandler
urlopen = build_opener(ProxyHandler({})).open
from urllib.error import HTTPError

base = os.environ.get('DEMO_URL', 'http://localhost:8081')
checks = []

def call(path, data=None, method=None):
    req = Request(base + path, data=json.dumps(data).encode() if data is not None else None,
                  headers={'Content-Type': 'application/json'}, method=method)
    with urlopen(req, timeout=20) as r:
        return json.load(r)

def check(name, condition):
    assert condition, name
    checks.append({'check': name, 'result': 'PASS'})

with urlopen(base, timeout=20) as r:
    check('Built React application served by packaged Spring Boot JAR', b'<div id="root">' in r.read())
user = call('/auth/register', {'name': 'FA2 Demo', 'email': 'fa2-demo@example.com', 'password': 'DemoPass123!'})
uid = user['id']
check('User registration', uid is not None)
login = call('/auth/login', {'email': 'fa2-demo@example.com', 'password': 'DemoPass123!'})
check('User login', login['id'] == uid)
for label, offset in [('Expired item', -1), ('Expires today', 0), ('Milk', 2), ('Seven-day boundary', 7), ('Future item', 8)]:
    result = call(f'/product?userId={uid}', {'productName': label, 'expiryDate': (date.today()+timedelta(days=offset)).strftime('%d/%m/%Y')})
    check(f'{label}: daysRemaining and warning', result['daysRemaining'] == offset and result['expiringSoon'] == (0 <= offset <= 7))
items = call(f'/product/expiring?userId={uid}&days=7')
check('Upcoming filter includes 0, 2, 7 days and excludes expired and 8 days', [p['daysRemaining'] for p in items] == [0,2,7])
try:
    call(f'/product?userId={uid}', {'productName': 'Invalid date', 'expiryDate': '31/02/2027'})
    raise AssertionError('Invalid date accepted')
except HTTPError as e:
    check('Impossible expiry date rejected with HTTP 400', e.code == 400)
items = call(f'/product?userId={uid}')
item_id = items[0]['id']
updated = call(f'/product/{item_id}?userId={uid}', {'productName':'Updated item'}, 'PUT')
check('Update item', updated['productName'] == 'Updated item')
call(f'/product/{item_id}?userId={uid}', method='DELETE')
check('Delete item', len(call(f'/product?userId={uid}')) == 4)
Path('reports').mkdir(exist_ok=True)
Path('reports/smoke-results.json').write_text(json.dumps({'environment':'temporary GitHub Actions runner, H2 demo database', 'checks': checks}, indent=2))
print(json.dumps(checks, indent=2))
