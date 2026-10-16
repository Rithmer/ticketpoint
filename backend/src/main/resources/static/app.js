'use strict';
const $ = id => document.getElementById(id);
const statuses = {NEW: 'Новое', CONFIRMED: 'Подтверждено', PAID: 'Оплачено', CANCELLED: 'Отменено'};
let catalogCustomers = [], catalogEvents = [];
let bookings = [];
let busy = false;
let currentUser = null;
let csrfToken = null;
const roles = {ADMIN: "Администратор", MANAGER: "Менеджер", USER: "Пользователь"};
const date = value => new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'short',
    timeStyle: 'short'
}).format(new Date(value));

function message(text, error = false) {
    $('message').textContent = text;
    const dialogError = document.querySelector('dialog[open] .editor-error');
    if (dialogError) dialogError.textContent = error ? text : '';
    $('message').classList.toggle('error', error);
}

async function api(path, method = 'GET', body) {
    let response;
    try { response = await fetch('/api/' + path, {
        method,
        headers: {'Content-Type': 'application/json', ...(csrfToken ? {'X-CSRF-Token': csrfToken} : {})},
        body: body === undefined ? undefined : JSON.stringify(body)
    }); } catch (error) { throw new Error('Нет соединения с сервером. Проверьте подключение и повторите действие.'); }
    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        if (response.status === 401 && (!path.startsWith('auth/') || /^(Сеанс|Войдите)/.test(error.message || ''))) setSession({user: null, csrfToken});
        throw new Error(error.message || 'Не удалось выполнить запрос.');
    }
    return response.status === 204 ? null : response.json();
}

function options(id, values, label, empty) {
    const previous = $(id).value;
    $(id).replaceChildren(new Option(empty, ''), ...values.map(value => new Option(label(value), value.id)));
    if ([...$(id).options].some(option => option.value === previous)) $(id).value = previous;
}

function textNode(tag, text) {
    const node = document.createElement(tag);
    node.textContent = text;
    return node;
}

function render() {
    const query = $('search').value.trim().toLocaleLowerCase('ru');
    const visible = bookings.filter(b => (!query || [b.ticketNumber, b.customer.name, b.customer.customerCode, b.event.title, b.event.venue].some(v => v.toLocaleLowerCase('ru').includes(query))) && (!$('filter-status').value || b.status === $('filter-status').value) && (!$('filter-event').value || String(b.event.id) === $('filter-event').value));
    $('count').textContent = `Показано ${visible.length} из ${bookings.length}`;
    $('bookings').replaceChildren(...visible.map(b => {
        const row = document.createElement('tr');
        [b.ticketNumber, b.customer.name, b.event.title, date(b.event.startsAt), b.priceAtBooking, statuses[b.status]].forEach(value => row.append(textNode('td', value)));
        const actions = document.createElement('td');
        const personal = currentUser.role === 'USER';
        const future = new Date(b.event.startsAt) > new Date();
        const transitions = personal
            ? (future && ['NEW', 'CONFIRMED'].includes(b.status) ? ['CANCELLED'] : [])
            : ({NEW: ['CONFIRMED', 'CANCELLED'], CONFIRMED: ['PAID', 'NEW', 'CANCELLED'], PAID: ['CANCELLED']}[b.status] || []);
        for (const next of transitions) {
            const button = textNode('button', {CONFIRMED: 'Подтвердить', PAID: 'Отметить оплату', NEW: 'Вернуть на исправление', CANCELLED: 'Отменить'}[next]);
            button.className = 'secondary'; button.disabled = busy;
            button.onclick = () => {
                if (next === 'CANCELLED' && !confirm('Отменить этот билет? Если он оплачен, возврат оформляется отдельно.')) return;
                perform(() => api(`bookings/${b.id}/status`, 'PATCH', {status: next}));
            };
            actions.append(button);
        }
        if (b.status === 'NEW' && future) {
            const edit = textNode('button', 'Изменить заявку'); edit.disabled = busy;
            edit.onclick = () => showEditor('Изменить заявку', [
                ...(!personal ? [{key: 'customerId', label: 'Клиент', value: b.customer.id, options: catalogCustomers.map(c => [c.id, c.name])}] : []),
                {key: 'eventId', label: 'Мероприятие (при замене применяется его текущая цена)', value: b.event.id,
                    options: catalogEvents.filter(e => new Date(e.startsAt) > new Date()).map(e => [e.id, `${e.title} • ${e.price} ₽`])}
            ], values => api(`bookings/${b.id}`, 'PUT', {expected: b, customerId: personal ? currentUser.customerId : Number(values.customerId), eventId: Number(values.eventId), status: b.status}));
            actions.append(edit);
        }
        if (b.status === 'NEW' || !personal && b.status === 'CANCELLED') {
            const remove = textNode('button', 'Удалить'); remove.className = 'danger'; remove.disabled = busy;
            remove.onclick = () => { if (confirm(`Удалить билет ${b.ticketNumber}?`)) perform(() => api(`bookings/${b.id}`, 'DELETE')); };
            actions.append(remove);
        }
        row.append(actions);
        return row;
    }));
}

async function load() {
    const [rows, customers, events, stats] = await Promise.all(['bookings', 'customers', 'events', 'statistics'].map(path => api(path)));
    bookings = rows;
    catalogCustomers = customers; catalogEvents = events;
    const personal = currentUser.role === 'USER';
    options('customer', customers, c => `${c.name} • ${c.customerCode}`, 'Выберите клиента');
    if (personal) $('customer').value = String(currentUser.customerId);
    $('customer').disabled = personal; $('customer').parentElement.hidden = personal;
    options('event', events.filter(e => new Date(e.startsAt) > new Date()), e => `${e.title} • ${date(e.startsAt)}`, 'Выберите мероприятие');
    options('filter-event', events, e => e.title, 'Все мероприятия');
    $('customers').replaceChildren(...customers.map(c => {
        const row = textNode('li', `${c.name} • ${c.customerCode}`);
        if (currentUser.role === 'ADMIN') addCatalogActions(row, 'customers', c, [{key: 'name', label: 'Имя клиента', value: c.name, maxLength: 120}]);
        return row;
    }));
    $('events').replaceChildren(...events.map(e => {
        const row = textNode('li', `${e.title} • ${e.venue} • ${date(e.startsAt)} • ${e.price} ₽`);
        if (personal && new Date(e.startsAt) > new Date()) {
            const reserve = textNode('button', 'Забронировать');
            reserve.onclick = () => perform(() => api('bookings', 'POST', {customerId: currentUser.customerId, eventId: e.id}));
            row.append(reserve);
        }
        if (currentUser.role === 'ADMIN') addCatalogActions(row, 'events', e, [
            {key: 'title', label: 'Название', value: e.title, maxLength: 200}, {key: 'venue', label: 'Место', value: e.venue, maxLength: 200},
            {key: 'startsAt', label: 'Дата и время (Europe/Moscow)', type: 'datetime-local', value: e.startsAt.slice(0, 16)},
            {key: 'price', label: 'Цена', type: 'number', value: e.price, min: 0, max: 9999999999.99, step: '0.01'}]);
        return row;
    }));
    const values = [['Всего', stats.total], ['Новые', stats.newBookings], ['Подтверждённые', stats.confirmed], ['Оплаченные', stats.paid], ['Отменённые', stats.cancelled], ['От 5 000 ₽', stats.expensiveTickets], ['Доля оплаченных', (stats.total ? 100 * stats.paid / stats.total : 0).toFixed(1) + ' %']];
    $('statistics').replaceChildren(...values.map(([label, value]) => {
        const card = textNode('div', label);
        card.className = 'card';
        card.append(textNode('strong', value));
        return card;
    }));
    if (currentUser.role === 'ADMIN') await loadUsers();
    render();
}

async function perform(action, form) {
    if (busy) return;
    busy = true;
    document.querySelectorAll('button').forEach(button => button.disabled = true);
    message('Выполнение…');
    let saved = false;
    try {
        await action();
        saved = true;
        if (form) form.reset();
        if (currentUser && !currentUser.passwordChangeRequired) await load();
        message(currentUser ? (currentUser.passwordChangeRequired ? 'Вход выполнен. Смените временный пароль.' : 'Данные обновлены.') : '');
    } catch (error) {
        message((saved ? 'Операция выполнена, но обновить список не удалось. ' : '') + error.message, true);
    } finally {
        busy = false;
        document.querySelectorAll('button').forEach(button => button.disabled = false);
        ['customer-form', 'event-form'].forEach(id => $(id).querySelectorAll('button').forEach(button => button.disabled = !currentUser || currentUser.role !== 'ADMIN'));
        render();
    }
}

$('search').oninput = render;
$('filter-status').onchange = render;
$('filter-event').onchange = render;
$('refresh').onclick = () => perform(() => Promise.resolve());
$('booking-form').onsubmit = e => {
    e.preventDefault();
    perform(() => api('bookings', 'POST', {
        customerId: currentUser.role === 'USER' ? currentUser.customerId : Number($('customer').value),
        eventId: Number($('event').value)
    }), e.target);
};
$('customer-form').onsubmit = e => {
    e.preventDefault();
    perform(() => api('customers', 'POST', {name: $('customer-name').value}), e.target);
};
$('event-form').onsubmit = e => {
    e.preventDefault();
    perform(() => api('events', 'POST', {
        title: $('event-title').value,
        venue: $('event-venue').value,
        startsAt: $('event-date').value,
        price: $('event-price').value
    }), e.target);
};
function setSession(session) {
    currentUser = session.user;
    csrfToken = session.csrfToken;
    $('auth-panel').hidden = Boolean(currentUser);
    $('account-panel').hidden = !currentUser;
    $('workspace').hidden = !currentUser || currentUser.passwordChangeRequired;
    $('users-panel').hidden = !currentUser || currentUser.role !== 'ADMIN';
    $('customer-section').hidden = Boolean(currentUser && currentUser.role === 'USER');
    ['customer-form', 'event-form'].forEach(id => $(id).hidden = !currentUser || currentUser.role !== 'ADMIN');
    $('booking-heading').textContent = currentUser && currentUser.role === 'USER' ? 'Мои заявки и билеты' : 'Заявки и билеты клиентов';
    $('statistics-scope').textContent = currentUser && currentUser.role === 'USER' ? 'Только ваши заявки и билеты' : 'По всей базе данных';
    $('account-name').textContent = currentUser ? `${currentUser.name} • ${roles[currentUser.role]}` : '';
    $('password-notice').textContent = currentUser && currentUser.passwordChangeRequired ? 'Замените временный пароль для продолжения работы.' : '';
    $('password-details').open = Boolean(currentUser && currentUser.passwordChangeRequired);
    if (!currentUser) {
        bookings = []; catalogCustomers = []; catalogEvents = [];
        ['bookings', 'customers', 'events', 'users', 'statistics'].forEach(id => $(id).replaceChildren());
    }
}

async function loadUsers() {
    const users = await api('auth/users');
    $('users').replaceChildren(...users.map(user => {
        const row = textNode('li', `${user.login} • ${user.name} • ${roles[user.role]} • ${user.enabled ? 'Активен' : 'Отключён'}`);
        const edit = textNode('button', 'Изменить имя и роль');
        edit.onclick = () => showEditor('Изменить пользователя', [
            {key: 'name', label: 'Имя', value: user.name, maxLength: 120},
            {key: 'role', label: 'Роль', value: user.role, options: user.role === 'ADMIN' ? [['ADMIN', roles.ADMIN]] : Object.entries(roles)}
        ], async values => { await api(`auth/users/${user.id}`, 'PUT', {expected: user, name: values.name, role: values.role}); setSession(await api('auth/session')); });
        row.append(edit);
        if (user.role !== 'ADMIN') {
            const button = textNode('button', user.enabled ? 'Отключить доступ' : 'Включить доступ');
            button.onclick = () => {
                if (confirm(`Изменить доступ для «${user.login}»?`)) perform(() => api(`auth/users/${user.id}/access`, 'PATCH', {enabled: !user.enabled}));
            };
            row.append(button);
        }
        return row;
    }));
}

$('login-form').onsubmit = e => {
    e.preventDefault();
    perform(async () => {
        setSession(await api('auth/session'));
        setSession(await api('auth/login', 'POST', {login: $('login').value, password: $('login-password').value}));
        $('login-password').value = '';
    });
};
function showRegistration(registering) {
    $('login-form').hidden = registering;
    $('registration-form').hidden = !registering;
    $('login-password').value = '';
    $('registration-password').value = '';
    $('registration-confirmation').value = '';
    message('');
    $(registering ? 'registration-name' : 'login').focus();
}
$('show-registration').onclick = () => showRegistration(true);
$('show-login').onclick = () => showRegistration(false);
$('registration-form').onsubmit = async e => {
    e.preventDefault();
    if ($('registration-password').value !== $('registration-confirmation').value)
        return message('Подтверждение пароля не совпадает.', true);
    await perform(async () => {
        setSession(await api('auth/session'));
        const session = await api('auth/register', 'POST', {
            login: $('registration-login').value,
            name: $('registration-name').value,
            password: $('registration-password').value,
            confirmation: $('registration-confirmation').value
        });
        $('registration-form').reset();
        showRegistration(false);
        setSession(session);
    });
    $('registration-password').value = '';
    $('registration-confirmation').value = '';
};
$('logout').onclick = () => perform(async () => {
    await api('auth/logout', 'POST');
    setSession(await api('auth/session'));
    $('password-form').reset();
    $('login-form').reset();
    $('registration-form').reset();
    showRegistration(false);
});
$('password-form').onsubmit = e => {
    e.preventDefault();
    if ($('new-password').value !== $('confirm-password').value) return message('Подтверждение пароля не совпадает.', true);
    perform(async () => {
        setSession(await api('auth/password', 'POST', {currentPassword: $('old-password').value, newPassword: $('new-password').value}));
        $('password-form').reset();
        $('password-details').open = false;
    });
};
$('user-form').onsubmit = e => {
    e.preventDefault();
    if ($('user-password').value !== $('user-confirmation').value) return message('Подтверждение пароля не совпадает.', true);
    perform(() => api('auth/users', 'POST', {login: $('user-login').value, name: $('user-name').value, role: $('user-role').value, password: $('user-password').value}), e.target);
};
perform(async () => { setSession(await api('auth/session')); });

function addCatalogActions(row, path, entity, fields) {
    const edit = textNode('button', 'Изменить');
    edit.onclick = () => showEditor('Изменить запись', fields, values => api(`${path}/${entity.id}`, 'PUT', values));
    const remove = textNode('button', 'Удалить'); remove.className = 'danger';
    remove.onclick = () => { if (confirm('Удалить запись? Записи, связанные с билетами или учётными записями, удалить нельзя.')) perform(() => api(`${path}/${entity.id}`, 'DELETE')); };
    row.append(edit, remove);
}

function showEditor(title, fields, save) {
    const dialog = document.createElement('dialog');
    const form = document.createElement('form');
    form.append(textNode('h2', title));
    const controls = {};
    fields.forEach(field => {
        const label = textNode('label', field.label);
        const input = document.createElement(field.options ? 'select' : 'input');
        if (field.options) field.options.forEach(([value, text]) => input.append(new Option(text, value)));
        else { input.type = field.type || 'text'; ['min', 'max', 'step', 'maxLength'].forEach(key => { if (field[key] !== undefined) input[key] = field[key]; }); }
        input.value = field.value; input.required = true;
        label.append(input); form.append(label); controls[field.key] = input;
    });
    const error = textNode('p', ''); error.className = 'editor-error error'; form.append(error);
    const submit = textNode('button', 'Сохранить'); submit.type = 'submit';
    const cancel = textNode('button', 'Отмена'); cancel.type = 'button'; cancel.onclick = () => dialog.close();
    form.append(submit, cancel); dialog.append(form); document.body.append(dialog);
    dialog.onclose = () => dialog.remove();
    form.onsubmit = event => { event.preventDefault(); perform(async () => {
        await save(Object.fromEntries(Object.entries(controls).map(([key, input]) => [key, input.value]))); dialog.close();
    }); };
    dialog.showModal();
}
