#!/usr/bin/env python3
import http.cookiejar
import json
import pathlib
import secrets
import sys
import urllib.error
import urllib.request

base = sys.argv[1].rstrip('/')
settings = dict(line.split('=', 1) for line in pathlib.Path(sys.argv[2]).read_text().splitlines()
                if line and not line.startswith('#'))

class Client:
    def __init__(self):
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.token = None
    def request(self, path, method='GET', body=None, expected=200, csrf=True):
        headers = {'Content-Type': 'application/json'}
        if self.token and csrf:
            headers['X-CSRF-Token'] = self.token
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(base + path, data=data, headers=headers, method=method)
        try:
            response = self.http.open(request, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        assert response.status == expected, f'{method} {path}: expected {expected}, received {response.status}'
        payload = response.read()
        result = json.loads(payload) if payload else None
        if isinstance(result, dict) and 'csrfToken' in result:
            self.token = result['csrfToken']
        return result
    def login(self, login, password):
        self.request('/api/auth/session')
        return self.request('/api/auth/login', 'POST', {'login': login, 'password': password})

suffix = secrets.token_hex(5)
password = 'Smoke-test-' + secrets.token_urlsafe(20)
admin = Client()
assert admin.request('/actuator/health')['status'] == 'UP'
admin.request('/api/bookings', expected=401)
session = admin.login(settings.get('BOOTSTRAP_ADMIN_LOGIN', 'admin'), settings['BOOTSTRAP_ADMIN_PASSWORD'])
if session['user']['passwordChangeRequired']:
    admin.request('/api/bookings', expected=403)
    admin.request('/api/auth/password', 'POST', {'currentPassword': settings['BOOTSTRAP_ADMIN_PASSWORD'], 'newPassword': password})
    settings['BOOTSTRAP_ADMIN_PASSWORD'] = password
    pathlib.Path(sys.argv[2]).write_text(''.join(f'{key}={value}\n' for key, value in settings.items()))
assert len(admin.request('/api/bookings')) >= 10
admin.request('/api/customers', 'POST', {'name': 'CSRF must reject this'}, expected=403, csrf=False)
admin.request('/api/events', 'POST', {'title': 'Invalid', 'venue': 'Hall', 'startsAt': None, 'price': 100}, expected=400)
buyer_login = 'buyer-' + suffix
permanent = password + '-changed'
client = Client()
client.request('/api/auth/session')
registration = {'login': buyer_login, 'name': 'Smoke buyer', 'password': permanent, 'confirmation': permanent, 'role': 'ADMIN'}
client.request('/api/auth/register', 'POST', registration, expected=403, csrf=False)
client.request('/api/auth/register', 'POST', {**registration, 'confirmation': 'different'}, expected=400)
registered = client.request('/api/auth/register', 'POST', registration, expected=201)
buyer = registered['user']
assert buyer['role'] == 'USER' and buyer['customerId'] is not None
assert not buyer['passwordChangeRequired']
assert client.request('/api/auth/session')['user']['id'] == buyer['id']
client.request('/api/auth/register', 'POST', registration, expected=400)
assert client.request('/api/bookings') == []
assert client.request('/api/statistics')['total'] == 0
assert len(client.request('/api/customers')) == 1
client.request('/api/customers', 'POST', {'name': 'Forbidden'}, expected=403)
client.request('/api/auth/users', expected=403)
manager_login = 'manager-' + suffix
admin.request('/api/auth/users', 'POST', {'login': manager_login, 'name': 'Smoke manager', 'role': 'MANAGER', 'password': password}, expected=201)
manager = Client()
manager.login(manager_login, password)
manager.request('/api/auth/password', 'POST', {'currentPassword': password, 'newPassword': permanent})
manager.request('/api/customers', 'POST', {'name': 'Forbidden'}, expected=403)
customer = admin.request('/api/customers', 'POST', {'name': 'Smoke customer'}, expected=201)
event = admin.request('/api/events', 'POST', {'title': 'Smoke event', 'venue': 'Test hall', 'startsAt': '2035-01-01T19:30:00', 'price': 1250}, expected=201)
admin.request(f"/api/events/{event['id']}", 'PUT', {'title': 'Updated event', 'venue': 'Updated hall', 'startsAt': '2035-01-01T19:30:00', 'price': 1300})
client.request('/api/bookings', 'POST', {'customerId': customer['id'], 'eventId': event['id']}, expected=403)
foreign = manager.request('/api/bookings', 'POST', {'customerId': customer['id'], 'eventId': event['id']}, expected=201)
client.request(f"/api/bookings/{foreign['id']}", expected=403)
client.request(f"/api/bookings/{foreign['id']}", 'DELETE', expected=403)
booking = client.request('/api/bookings', 'POST', {'customerId': buyer['customerId'], 'eventId': event['id']}, expected=201)
assert [b['id'] for b in client.request('/api/bookings')] == [booking['id']]
client.request(f"/api/bookings/{booking['id']}/status", 'PATCH', {'status': 'CONFIRMED'}, expected=403)
client.request(f"/api/bookings/{booking['id']}/status", 'PATCH', {'status': 'PAID'}, expected=403)
replacement = admin.request('/api/events', 'POST', {'title': 'Replacement', 'venue': 'Second hall', 'startsAt': '2035-02-01T19:30:00', 'price': 2400}, expected=201)
edited = client.request(f"/api/bookings/{booking['id']}", 'PUT', {'expected': booking, 'customerId': buyer['customerId'], 'eventId': replacement['id'], 'status': 'NEW'})
assert edited['priceAtBooking'] == 2400
client.request(f"/api/bookings/{booking['id']}", 'PUT', {'expected': booking, 'customerId': buyer['customerId'], 'eventId': event['id'], 'status': 'NEW'}, expected=400)
manager.request(f"/api/bookings/{booking['id']}/status", 'PATCH', {'status': 'PAID'}, expected=400)
assert manager.request(f"/api/bookings/{booking['id']}/status", 'PATCH', {'status': 'CONFIRMED'})['status'] == 'CONFIRMED'
assert client.request('/api/statistics')['confirmed'] == 1
client.request(f"/api/bookings/{booking['id']}", 'DELETE', expected=400)
assert manager.request(f"/api/bookings/{booking['id']}/status", 'PATCH', {'status': 'PAID'})['status'] == 'PAID'
manager.request(f"/api/bookings/{booking['id']}", 'DELETE', expected=400)
client.request(f"/api/bookings/{booking['id']}/status", 'PATCH', {'status': 'CANCELLED'}, expected=403)
manager.request(f"/api/bookings/{booking['id']}/status", 'PATCH', {'status': 'CANCELLED'})
manager.request(f"/api/bookings/{booking['id']}", 'DELETE', expected=204)
manager.request(f"/api/bookings/{foreign['id']}", 'DELETE', expected=204)
admin.request(f"/api/events/{replacement['id']}", 'DELETE', expected=204)
admin.request(f"/api/events/{event['id']}", 'DELETE', expected=204)
admin.request(f"/api/customers/{customer['id']}", 'DELETE', expected=204)
fresh = next(u for u in admin.request('/api/auth/users') if u['id'] == buyer['id'])
promoted = admin.request(f"/api/auth/users/{buyer['id']}", 'PUT', {'expected': fresh, 'name': 'Updated buyer', 'role': 'MANAGER'})
client.request('/api/bookings', expected=401)
returned = admin.request(f"/api/auth/users/{buyer['id']}", 'PUT', {'expected': promoted, 'name': 'Buyer restored', 'role': 'USER'})
assert returned['customerId'] == buyer['customerId']
assert client.login(buyer_login, permanent)['user']['role'] == 'USER'
admin.request(f"/api/auth/users/{buyer['id']}/access", 'PATCH', {'enabled': False}, expected=204)
client.request('/api/bookings', expected=401)
manager.request('/api/auth/logout', 'POST', expected=204)
manager.request('/api/bookings', expected=401)
assert 'password_hash' not in json.dumps(admin.request('/api/auth/users'))
print('PASS: public registration, permanent buyer password, forced USER role, buyer ownership, manager confirmation, payment recording, admin catalog/account CRUD, readiness, login, mandatory password change, CSRF, roles, CRUD, validation, access revocation and logout')
