"use strict";
const byId = (id) => document.getElementById(id),
    esc = (s) =>
        String(s ?? "").replace(
            /[&<>"']/g,
            (c) =>
                ({
                    "&": "&amp;",
                    "<": "&lt;",
                    ">": "&gt;",
                    '"': "&quot;",
                    "'": "&#39;",
                })[c],
        );
/** @type {{tokens: any, page: number, userPage: number, auditPage: number, events: any[], customers: any[], bookings: any[], preview: any, tab: string}} */
const state = {
    tokens: null,
    page: 0,
    userPage: 0,
    auditPage: 0,
    events: [],
    customers: [],
    bookings: [],
    preview: null,
    tab: "bookings",
};
const statuses = {NEW: "Новое", CONFIRMED: "Подтверждено", PAID: "Оплачено", CANCELLED: "Отменено"};
const priorities = {LOW: "Обычный", MEDIUM: "Средний", HIGH: "Высокий"};
const roles = {USER: "Покупатель", VIEWER: "Наблюдатель", MANAGER: "Менеджер", ADMIN: "Администратор"};
const buyer = () => state.tokens?.user.role === "USER";
const availableEvents = () => state.events.filter(e => e.available);
const textValue = value => statuses[value] || priorities[value] || roles[value] || value;
const formatDate = value => value ? new Intl.DateTimeFormat("ru-RU", /** @type {Intl.DateTimeFormatOptions} */ ({
    dateStyle: "medium",
    timeStyle: "short"
})).format(new Date(value)) : "—";
const fieldNames = {
    login: "Логин",
    name: "Имя",
    password: "Пароль",
    confirmation: "Повтор пароля",
    currentPassword: "Текущий пароль",
    newPassword: "Новый пароль",
    temporaryPassword: "Временный пароль",
    customerId: "Покупатель",
    eventId: "Мероприятие",
    status: "Статус",
    title: "Название",
    venue: "Место проведения",
    startsAt: "Начало",
    price: "Цена",
    description: "Описание",
    tags: "Метки",
    body: "Заметка"
};
const auditNames = {
    LOGIN_SUCCESS: "Вход в аккаунт",
    LOGIN_FAILED: "Неудачная попытка входа",
    LOGOUT: "Выход из аккаунта",
    USER_REGISTERED: "Регистрация покупателя",
    USER_CREATED: "Создание пользователя",
    USER_UPDATED: "Изменение пользователя",
    PASSWORD_CHANGED: "Смена пароля",
    PASSWORD_RESET: "Сброс пароля",
    REFRESH_REUSE: "Повторное использование сеанса",
    BOOKING_CREATED: "Создание бронирования",
    BOOKING_UPDATED: "Изменение бронирования",
    BOOKING_ARCHIVED: "Архивирование",
    BOOKING_RESTORED: "Восстановление",
    BOOKING_DELETED: "Окончательное удаление",
    EVENT_CREATED: "Создание мероприятия",
    EVENT_UPDATED: "Изменение мероприятия",
    CUSTOMER_CREATED: "Создание профиля покупателя",
    NOTE_SAVED: "Сохранение заметки",
    NOTE_DELETED: "Удаление заметки",
    BOOKING_BULK_ARCHIVE: "Массовое архивирование",
    BOOKING_BULK_RESTORE: "Массовое восстановление",
    BOOKING_BULK_SET_STATUS: "Массовое изменение статуса",
    BOOKING_BULK_ADD_TAG: "Добавление метки",
    BOOKING_BULK_REMOVE_TAG: "Удаление метки"
};
const entityNames = {BOOKING: "Бронирование", USER: "Пользователь", EVENT: "Мероприятие"};
const write = () => ["ADMIN", "MANAGER"].includes(state.tokens?.user.role),
    admin = () => state.tokens?.user.role === "ADMIN";
byId("apiDocs").href = "/swagger-ui.html";
function message(text) {
    byId("message").textContent = text;
}

/** @returns {Promise<any>} */
async function api(
    path,
    {method = "GET", body, headers = {}, raw = false, retry = true} = {},
) {
    const h = {...headers};
    if (state.tokens && !["/auth/login", "/auth/refresh"].includes(path))
        h.Authorization = "Bearer " + state.tokens.accessToken;
    if (body !== undefined && !(body instanceof FormData))
        h["Content-Type"] = "application/json";
    let r;
    try {
        r = await fetch("/api" + path, {
            method,
            headers: h,
            body:
                body === undefined
                    ? undefined
                    : body instanceof FormData
                        ? body
                        : JSON.stringify(body),
        });
    } catch {
        throw new Error("Нет соединения с сервером. Проверьте интернет и попробуйте ещё раз.");
    }
    if (r.status === 401 && state.tokens && retry && !path.startsWith("/auth/")) {
        try {
            state.tokens = await api("/auth/refresh", {
                method: "POST",
                body: {refreshToken: state.tokens.refreshToken},
                retry: false,
            });
            return api(path, {method, body, headers, raw, retry: false});
        } catch (e) {
            signedOut();
            throw e;
        }
    }
    if (!r.ok) {
        const data = await r
            .json()
            .catch(() => ({message: "Не удалось выполнить действие. Попробуйте ещё раз."}));
        throw new Error(
            data.message +
            (Object.keys(data.fields || {}).length
                ? "\n" +
                Object.entries(data.fields)
                    .map(([k, v]) => (fieldNames[k.split(".").pop()] || "Поле формы") + ": " + v)
                    .join("\n")
                : ""),
        );
    }
    return r.status === 204 ? null : raw ? r.text() : r.json();
}

function visibility() {
    document.querySelectorAll(".admin").forEach((e) => (e.hidden = !admin()));
    document.querySelectorAll(".writer").forEach((e) => (e.hidden = !write()));
}

function signedOut() {
    state.tokens = null;
    state.preview = null;
    byId("workspace").hidden = true;
    byId("passwordPanel").hidden = true;
    byId("login").hidden = false;
    byId("registration").hidden = true;
    byId("identity").textContent = "";
    byId("loginForm").reset();
}

async function signedIn() {
    /** @type {{name: string, role: string, passwordChangeRequired: boolean}} */
    const u = state.tokens.user;
    byId("identity").textContent = u.name + " · " + roles[u.role];
    byId("bookingsHeading").textContent = buyer() ? "Мои билеты" : "Бронирования";
    byId("newBooking").textContent = buyer() ? "Забронировать билет" : "Создать бронирование";
    byId("newBooking").hidden = !write() && !buyer();
    byId("registration").hidden = true;
    byId("login").hidden = true;
    byId("passwordPanel").hidden = !u.passwordChangeRequired;
    byId("workspace").hidden = u.passwordChangeRequired;
    if (!u.passwordChangeRequired) {
        await loadReferences();
        visibility();
        await tab("bookings");
    } else message("Для продолжения замените временный пароль.");
}

byId("loginForm").onsubmit = async (e) => {
    e.preventDefault();
    try {
        state.tokens = await api("/auth/login", {
            method: "POST",
            body: Object.fromEntries(new FormData(e.target)),
        });
        e.target.reset();
        message("");
        await signedIn();
    } catch (ex) {
        message(ex.message);
    }
};
byId("showRegistration").onclick = () => {
    byId("login").hidden = true;
    byId("registration").hidden = false;
    message("");
};
byId("showLogin").onclick = () => {
    byId("registration").hidden = true;
    byId("login").hidden = false;
    message("");
};
byId("registrationForm").onsubmit = async e => {
    e.preventDefault();
    const body = Object.fromEntries(new FormData(e.target));
    if (body.password !== body.confirmation) {
        message("Пароли не совпадают. Повторите пароль ещё раз.");
        return;
    }
    const submit = e.target.querySelector('[type="submit"]');
    submit.disabled = true;
    try {
        state.tokens = await api("/auth/register", {method: "POST", body});
        e.target.reset();
        message("");
        await signedIn();
    } catch (ex) {
        message(ex.message);
    } finally {
        submit.disabled = false;
    }
};
byId("passwordForm").onsubmit = async (e) => {
    e.preventDefault();
    try {
        await api("/auth/password", {
            method: "POST",
            body: Object.fromEntries(new FormData(e.target)),
        });
        e.target.reset();
        signedOut();
        message("Пароль изменён. Войдите с новым паролем.");
    } catch (ex) {
        message(ex.message);
    }
};
byId("passwordButton").onclick = () => {
    byId("passwordPanel").hidden = !byId("passwordPanel").hidden;
};
byId("logoutButton").onclick = async () => {
    try {
        await api("/auth/logout", {
            method: "POST",
            body: {refreshToken: state.tokens.refreshToken},
        });
    } catch (ex) {
        message(ex.message);
    } finally {
        signedOut();
    }
};

async function loadReferences() {
    [state.events, state.customers] = await Promise.all([
        api("/events"),
        api("/customers"),
    ]);
    byId("eventFilter").innerHTML =
        '<option value="">Все</option>' +
        state.events
            .map(
                (e) =>
                    `<option value="${e.id}">${esc(e.title)}${e.active ? "" : " (отключено)"}</option>`,
            )
            .join("");
}

async function tab(name) {
    state.tab = name;
    for (const id of ["bookings", "events", "analytics", "users", "audit"])
        byId(id).hidden = id !== name;
    try {
        if (name === "bookings") await loadBookings();
        if (name === "events") await loadEvents();
        if (name === "analytics") await loadAnalytics();
        if (name === "users") await loadUsers();
        if (name === "audit") await loadAudit();
    } catch (ex) {
        message(ex.message);
    }
}

document
    .querySelectorAll("[data-tab]")
    .forEach((b) => (b.onclick = () => tab(b.dataset.tab)));

function filters() {
    const params = new URLSearchParams();
    for (const [k, v] of new FormData(byId("filters")))
        if (v !== "") params.set(k, v);
    return params;
}

byId("filters").onsubmit = (e) => {
    e.preventDefault();
    state.page = 0;
    void tab("bookings");
};
byId("filters").onreset = () => {
    state.page = 0;
    setTimeout(() => tab("bookings"), 0);
};

async function loadBookings() {
    const params = filters();
    params.set("page", String(state.page));
    const data = await api("/bookings?" + params);
    state.bookings = data.content;
    byId("selectAll").checked = false;
    byId("bookingRows").innerHTML =
        data.content
            .map(
                (b) =>
                    `<tr><td><input type="checkbox" data-select="${b.id}" aria-label="Выбрать ${esc(b.ticketNumber)}"></td><td>${esc(b.ticketNumber)}<small>${esc(b.customerName)}</small><small>${b.tags.map(esc).join(" · ")}${b.vip ? " · VIP" : ""}</small></td><td>${esc(b.eventTitle)}</td><td>${esc(statuses[b.status])}</td><td>${esc(b.price)} ₽</td><td>${priorities[b.priority]}</td><td><button data-action="notes" data-id="${b.id}" class="secondary">Заметки</button>${(write() || buyer() && !b.archived && ["NEW", "CONFIRMED"].includes(b.status)) ? `<button data-action="edit" data-id="${b.id}">${buyer() && b.status === "CONFIRMED" ? "Отменить" : "Изменить"}</button>${write() || b.status === "NEW" ? `<button data-action="${b.archived ? "restore" : "archive"}" data-id="${b.id}" class="secondary">${b.archived ? "Восстановить" : "В архив"}</button>` : ""}` : ""}${admin() && b.archived ? `<button data-action="delete" data-id="${b.id}" class="secondary">Удалить навсегда</button>` : ""}</td></tr>`,
            )
            .join("") || '<tr><td colspan="7">Билетов пока нет. Откройте «Мероприятия» и выберите, куда хотите пойти, или измените фильтры.</td></tr>';
    byId("pageInfo").textContent =
        `Страница ${data.number + 1} из ${Math.max(1, data.totalPages)} · Всего ${data.totalElements}`;
    byId("prevPage").disabled = data.first;
    byId("nextPage").disabled = data.last;
}

byId("prevPage").onclick = () => {
    state.page--;
    void tab("bookings");
};
byId("nextPage").onclick = () => {
    state.page++;
    void tab("bookings");
};
byId("selectAll").onchange = (e) =>
    document
        .querySelectorAll("[data-select]")
        .forEach((c) => (c.checked = e.target.checked));
byId("bookingRows").onclick = async (e) => {
    const b = e.target.closest("[data-action]");
    if (!b) return;
    const id = b.dataset.id;
    try {
        if (b.dataset.action === "notes") return await openNotes(id);
        if (b.dataset.action === "edit")
            return bookingEditor(state.bookings.find((x) => x.id === id));
        if (
            !confirm(
                b.dataset.action === "delete"
                    ? "Удалить билет навсегда вместе с заметками?"
                    : b.dataset.action === "archive"
                        ? "Переместить билет в архив?"
                        : "Восстановить билет?",
            )
        )
            return;
        if (b.dataset.action === "archive")
            await api("/bookings/" + id, {method: "DELETE"});
        if (b.dataset.action === "restore")
            await api("/bookings/" + id + "/restore", {method: "POST"});
        if (b.dataset.action === "delete")
            await api("/bookings/" + id + "/permanent", {
                method: "DELETE",
                headers: {"X-Confirm-Delete": "PERMANENTLY_DELETE"},
            });
        await loadBookings();
        message("Операция выполнена.");
    } catch (ex) {
        message(ex.message);
    }
};

function field(name, label, type = "text", value = "", options = null) {
    const wrapper = document.createElement("label");
    wrapper.append(document.createTextNode(label));
    const input = document.createElement(options ? "select" : type === "textarea" ? "textarea" : "input");
    input.setAttribute("name", name);
    if (options) {
        for (const option of options) {
            const node = document.createElement("option");
            node.value = String(option.value);
            node.textContent = option.label;
            node.selected = String(value) === String(option.value);
            input.append(node);
        }
    } else if (type === "textarea") {
        input.setAttribute("maxlength", "4000");
        input.textContent = value;
    } else {
        input.setAttribute("type", type);
        if (type === "checkbox") {
            if (value) input.setAttribute("checked", "");
        } else input.setAttribute("value", value);
        if (type === "password") input.setAttribute("autocomplete", "new-password");
        if (type === "number") {
            input.setAttribute("step", "0.01");
            input.setAttribute("min", "0");
        }
    }
    wrapper.append(input);
    return wrapper.outerHTML;
}

function editor(title, fields, save) {
    byId("editorTitle").textContent = title;
    byId("editorFields").innerHTML = fields;
    byId("editorError").textContent = "";
    byId("editor").showModal();
    byId("editorForm").onsubmit = async (e) => {
        e.preventDefault();
        const submit = e.target.querySelector('[type="submit"]');
        if (submit.disabled) return;
        submit.disabled = true;
        try {
            await save(new FormData(e.target));
            byId("editor").close();
            message("Сохранено.");
        } catch (ex) {
            byId("editorError").textContent = ex.message;
        } finally {
            submit.disabled = false;
        }
    };
}

byId("closeEditor").onclick = () => byId("editor").close();

function bookingEditor(b = null, selectedEvent = null) {
    if (b?.archived) {
        message("Сначала восстановите билет из архива.");
        return;
    }
    const choices = availableEvents();
    if (!b && !choices.length) {
        message("Сейчас нет мероприятий, доступных для бронирования. Загляните позже.");
        return;
    }
    const original = b && state.events.find(e => e.id === b.eventId);
    if (original && !choices.some(e => e.id === original.id)) choices.unshift(original);
    const customerOptions = state.customers.map(c => ({value: c.id, label: c.name}));
    const allowed = b ? (b.status === "NEW" ? ["NEW", "CONFIRMED", "CANCELLED"] : b.status === "CONFIRMED" ? ["CONFIRMED", "NEW", "PAID", "CANCELLED"] : b.status === "PAID" ? ["PAID", "CANCELLED"] : ["CANCELLED"]) : ["NEW"];
    const statusOptions = (buyer() ? (b?.status === "CONFIRMED" ? ["CANCELLED"] : ["NEW", "CANCELLED"].filter(v => b || v === "NEW")) : allowed).map(value => ({
        value,
        label: statuses[value]
    }));
    editor(b ? (buyer() && b.status === "CONFIRMED" ? "Отменить бронирование" : "Изменить бронирование") : "Забронировать билет",
        (buyer() ? `<p>Покупатель: <strong>${esc(state.tokens.user.name)}</strong></p>` : field("customerId", "Клиент", "", b?.customerId ?? state.customers[0]?.id, customerOptions)) +
        field("eventId", "Мероприятие", "", b?.eventId ?? selectedEvent?.id ?? choices[0]?.id,
            choices.map(e => ({
                value: e.id,
                label: e.title + " · " + formatDate(e.startsAt) + " · " + e.price + " ₽"
            }))) +
        field("status", "Статус", "", b?.status ?? "NEW", statusOptions) +
        (buyer() ? "<p>Цена определяется выбранным мероприятием. Заявку подтвердит менеджер.</p>" : field("vip", "VIP", "checkbox", b?.vip)) +
        field("description", "Комментарий к бронированию", "textarea", b?.description ?? "") +
        (buyer() ? "" : field("tags", "Метки через запятую", "text", b?.tags.join(", ") ?? "")),
        async f => {
            const body = {
                customerId: buyer() ? state.customers[0]?.id : Number(f.get("customerId")),
                eventId: f.get("eventId") || b?.eventId,
                status: f.get("status"),
                vip: buyer() ? (b?.vip ?? false) : f.has("vip"),
                description: f.get("description"),
                tags: buyer() ? (b?.tags ?? []) : String(f.get("tags") || "").split(",").map(t => t.trim()).filter(Boolean),
                version: b?.version
            };
            await api("/bookings" + (b ? "/" + b.id : ""), {method: b ? "PUT" : "POST", body});
            await tab("bookings");
        });
    if (b && b.status !== "NEW") byId("editorFields").querySelector('[name="eventId"]').disabled = true;
}

byId("newBooking").onclick = async () => {
    try {
        await loadReferences();
        bookingEditor();
    } catch (ex) {
        message(ex.message);
    }
};
byId("bulkApply").onclick = async () => {
    const ids = [...document.querySelectorAll("[data-select]:checked")].map(
        (c) => c.dataset.select,
    );
    if (!ids.length) {
        message("Выберите бронирования.");
        return;
    }
    if (!confirm("Применить операцию к " + ids.length + " записям?")) return;
    try {
        const r = await api("/bookings/bulk", {
            method: "POST",
            body: {
                ids,
                action: byId("bulkAction").value,
                status: byId("bulkStatus").value,
                tag: byId("bulkTag").value,
            },
        });
        message(
            `Успешно: ${r.succeeded} из ${r.requested}` +
            (r.failures.length
                ? "\n" + r.failures.map((f) => f.id + ": " + f.message).join("\n")
                : ""),
        );
        await loadBookings();
    } catch (ex) {
        message(ex.message);
    }
};
byId("exportCsv").onclick = async () => {
    try {
        const text = await api("/bookings/export?" + filters(), {raw: true});
        const url = URL.createObjectURL(
            new Blob([text], {type: "text/csv;charset=utf-8"}),
        );
        const a = document.createElement("a");
        a.href = url;
        a.download = "бронирования.csv";
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (ex) {
        message(ex.message);
    }
};
byId("previewImport").onclick = async () => {
    try {
        state.preview = null;
        byId("applyImport").disabled = true;
        if (!byId("csvFile").files[0]) {
            message("Выберите файл таблицы в формате .csv.");
            return;
        }
        const body = new FormData();
        body.set("file", byId("csvFile").files[0]);
        body.set("mode", byId("importMode").value);
        state.preview = await api("/bookings/import/preview", {
            method: "POST",
            body,
        });
        byId("importResult").textContent = `Готово к импорту: ${state.preview.validRows}. Строк с ошибками: ${state.preview.invalidRows}.` + importErrors(state.preview.errors);
        byId("applyImport").disabled = state.preview.validRows === 0;
    } catch (ex) {
        message(ex.message);
    }
};
byId("csvFile").onchange = byId("importMode").onchange = () => {
    state.preview = null;
    byId("applyImport").disabled = true;
};
byId("applyImport").onclick = async () => {
    try {
        if (!state.preview) return;
        const result = await api("/bookings/import/apply", {
            method: "POST",
            body: {previewId: state.preview.previewId, mode: state.preview.mode},
        });
        byId("importResult").textContent = `Сохранено записей: ${result.succeeded}.` + importErrors(result.errors);
        state.preview = null;
        byId("applyImport").disabled = true;
        await loadBookings();
    } catch (ex) {
        message(ex.message);
    }
};

function importErrors(errors) {
    return errors.length ? "\n" + errors.map(e => `Строка ${e.row}: ${e.message}`).join("\n") : "\nОшибок нет.";
}

async function loadEvents() {
    await loadReferences();
    byId("eventCards").innerHTML = (buyer() ? availableEvents() : state.events)
        .map(
            (e) =>
                `<article class="card"><h2>${esc(e.title)}</h2><p>${esc(e.venue)}<br>${esc(formatDate(e.startsAt))}<br>${e.price} ₽ · ${e.active ? "Активно" : "Отключено"}</p>${e.available && (write() || buyer()) ? `<button data-book-event="${e.id}">Забронировать</button>` : ""}${admin() ? `<button data-event="${e.id}">Изменить</button>` : ""}</article>`,
        )
        .join("") || "<p>Сейчас нет мероприятий, доступных для бронирования. Загляните позже.</p>";
    visibility();
}

function eventEditor(e = null) {
    editor(
        e ? "Изменить мероприятие" : "Новое мероприятие",
        field("title", "Название", "text", e?.title ?? "") +
        field("venue", "Место", "text", e?.venue ?? "") +
        field("startsAt", "Начало", "datetime-local", e?.startsAt ?? "") +
        field("price", "Цена", "number", e?.price ?? 0) +
        field("active", "Активно", "checkbox", e?.active ?? true),
        async (f) => {
            await api("/events" + (e ? "/" + e.id : ""), {
                method: e ? "PUT" : "POST",
                body: {
                    title: f.get("title"),
                    venue: f.get("venue"),
                    startsAt: f.get("startsAt"),
                    price: Number(f.get("price")),
                    active: f.has("active"),
                    version: e?.version,
                },
            });
            await loadEvents();
        },
    );
}

byId("newEvent").onclick = () => eventEditor();
byId("eventCards").onclick = (e) => {
    const book = e.target.closest("[data-book-event]");
    if (book) return bookingEditor(null, state.events.find(x => x.id === book.dataset.bookEvent));
    const b = e.target.closest("[data-event]");
    if (b) eventEditor(state.events.find((x) => x.id === b.dataset.event));
};
byId("newCustomer").onclick = () =>
    editor("Новый клиент", field("name", "Имя"), async (f) => {
        await api("/customers", {method: "POST", body: {name: f.get("name")}});
        await loadReferences();
    });

async function loadAnalytics() {
    const d = await api("/analytics/summary");
    const names = {
        total: "Всего бронирований",
        active: "Активные",
        archived: "Архивные",
        vip: "Бронирования с VIP-отметкой",
        averagePrice: "Средняя цена",
    };
    byId("metrics").innerHTML = Object.entries(names)
        .map(
            ([k, n]) =>
                `<article class="card"><div>${n}</div><strong>${esc(d[k])}</strong></article>`,
        )
        .join("");
    byId("breakdowns").innerHTML = Object.entries({
        byEvent: "По мероприятиям",
        byStatus: "По статусам",
        byPriority: "По приоритету",
    })
        .map(
            ([k, n]) =>
                `<article class="card"><h2>${n}</h2>${Object.entries(d[k])
                    .map(
                        ([a, b]) =>
                            `<p>${esc(a.includes(" | ") ? a.split(" | ").slice(1).join(" | ") : textValue(a))}: <b>${b}</b></p>`,
                    )
                    .join("")}</article>`,
        )
        .join("");
}

let noteBooking = null,
    noteRows = [];

async function openNotes(id) {
    noteBooking = id;
    await loadNotes();
    if (!byId("notesDialog").open) byId("notesDialog").showModal();
    visibility();
}

async function loadNotes() {
    noteRows = await api("/bookings/" + noteBooking + "/notes");
    byId("notesList").innerHTML =
        noteRows
            .map(
                (n) =>
                    `<article class="row"><b>${n.pinned ? "📌 " : ""}${esc(n["authorName"])}</b><small> · ${esc(formatDate(n["updatedAt"]))}</small><p>${esc(n.body)}</p>${write() ? `<button data-note="${n.id}" data-kind="edit">Изменить</button><button data-note="${n.id}" data-kind="delete" class="secondary">Удалить</button>` : ""}</article>`,
            )
            .join("") || "<p>Заметок пока нет</p>";
}

byId("noteForm").onsubmit = async (e) => {
    e.preventDefault();
    try {
        const f = new FormData(e.target);
        await api("/bookings/" + noteBooking + "/notes", {
            method: "POST",
            body: {body: f.get("body"), pinned: f.has("pinned")},
        });
        e.target.reset();
        await loadNotes();
    } catch (ex) {
        message(ex.message);
    }
};
byId("notesList").onclick = async (e) => {
    const b = e.target.closest("[data-note]");
    if (!b) return;
    const n = noteRows.find((x) => x.id === b.dataset.note);
    try {
        if (b.dataset.kind === "delete") {
            if (!confirm("Удалить заметку?")) return;
            await api("/bookings/" + noteBooking + "/notes/" + n.id, {
                method: "DELETE",
            });
            await loadNotes();
        } else {
            const text = prompt("Текст заметки", n.body);
            if (text === null) return;
            await api("/bookings/" + noteBooking + "/notes/" + n.id, {
                method: "PUT",
                body: {
                    body: text,
                    pinned: confirm("Закрепить заметку?"),
                    version: n.version,
                },
            });
            await loadNotes();
        }
    } catch (ex) {
        message(ex.message);
    }
};
byId("closeNotes").onclick = () => byId("notesDialog").close();
let users = [];

async function loadUsers() {
    const data = await api("/admin/users?page=" + state.userPage);
    users = data.content;
    byId("userRows").innerHTML = users
        .map(
            (u) =>
                `<article class="row"><b>${esc(u.name)}</b> · ${esc(u.login)} · ${esc(roles[u.role] || "Пользователь")} · ${u.enabled ? "Активен" : "Отключён"}<button data-user="${u.id}">Изменить</button><button data-reset="${u.id}" class="secondary">Сбросить пароль</button></article>`,
        )
        .join("");
    byId("usersInfo").textContent =
        `Страница ${data.number + 1} · Всего ${data.totalElements}`;
    byId("prevUsers").disabled = data.first;
    byId("nextUsers").disabled = data.last;
}

byId("prevUsers").onclick = () => {
    state.userPage--;
    void tab("users");
};
byId("nextUsers").onclick = () => {
    state.userPage++;
    void tab("users");
};

function userEditor(u = null) {
    editor(
        u ? "Изменить пользователя" : "Новая учётная запись",
        field("login", "Логин", "text", u?.login ?? "") +
        field("name", "Имя", "text", u?.name ?? "") +
        field(
            "role",
            "Роль",
            "",
            u?.role ?? "USER",
            ["USER", "VIEWER", "MANAGER", "ADMIN"].map((v) => ({value: v, label: textValue(v)})),
        ) +
        field("enabled", "Активен", "checkbox", u?.enabled ?? true) +
        (u ? "" : field("temporaryPassword", "Временный пароль", "password")),
        async (f) => {
            const user = {
                login: f.get("login"),
                name: f.get("name"),
                role: f.get("role"),
                enabled: f.has("enabled"),
                version: u?.version,
            };
            await api("/admin/users" + (u ? "/" + u.id : ""), {
                method: u ? "PUT" : "POST",
                body: u
                    ? user
                    : {user, temporaryPassword: f.get("temporaryPassword")},
            });
            await loadUsers();
        },
    );
}

byId("newUser").onclick = () => userEditor();
byId("userRows").onclick = (e) => {
    const b = e.target.closest("[data-user],[data-reset]");
    if (!b) return;
    if (b.dataset.user) userEditor(users.find((u) => u.id === b.dataset.user));
    else
        editor(
            "Сброс пароля",
            field("temporaryPassword", "Новый временный пароль", "password"),
            async (f) => {
                await api("/admin/users/" + b.dataset.reset + "/reset-password", {
                    method: "POST",
                    body: {temporaryPassword: f.get("temporaryPassword")},
                });
                await loadUsers();
            },
        );
};

async function loadAudit() {
    const q = new FormData(byId("auditSearch")).get("q");
    const data = await api(
        "/admin/audit?page=" + state.auditPage + "&q=" + encodeURIComponent(String(q ?? "")),
    );
    byId("auditRows").innerHTML = data.content
        .map(
            (/** @type {{eventType:string, actor:string, occurredAt:string, summary:string, entityType:string, entityId:string, correlationId:string}} */ a) =>
                `<article class="row"><b>${esc(auditNames[a.eventType] || "Изменение данных")}</b> · ${esc(a["actor"] === "anonymous" ? "Посетитель" : a["actor"])} · ${esc(formatDate(a.occurredAt))}<p>${esc(a.summary)}</p><small>${esc(entityNames[a.entityType] || "Запись")} · ${esc(a.entityId)} · Запрос ${esc(a.correlationId)}</small></article>`,
        )
        .join("");
    byId("auditInfo").textContent =
        `Страница ${data.number + 1} · Всего ${data.totalElements}`;
    byId("prevAudit").disabled = data.first;
    byId("nextAudit").disabled = data.last;
}

byId("auditSearch").onsubmit = (e) => {
    e.preventDefault();
    state.auditPage = 0;
    void tab("audit");
};
byId("prevAudit").onclick = () => {
    state.auditPage--;
    void tab("audit");
};
byId("nextAudit").onclick = () => {
    state.auditPage++;
    void tab("audit");
};

byId("copyToken").onclick = async () => {
    try {
        await navigator.clipboard.writeText(state.tokens.accessToken);
        message("Ключ доступа скопирован.");
    } catch (ex) {
        message("Не удалось скопировать ключ доступа. Попробуйте ещё раз.");
    }
};
