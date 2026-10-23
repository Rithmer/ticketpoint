\set ON_ERROR_STOP on
\ir seed.sql

BEGIN;
INSERT INTO app_users(login, name, password_hash, role, password_change_required, customer_id)
SELECT v.login, v.name, v.hash, v.role, false, c.id
FROM (VALUES
 ('user', 'Анна Смирнова', '$2a$12$NWVhHptcxjwXOI9ybcsuNOd6Yvln7lfKdnnfv.QzPYox.ny8ivucm', 'USER', 'CUS-5D844467-A4EA-4FF7-B3C9-00660BC6E293'),
 ('manager', 'Ольга Белова', '$2a$12$/EHELpH8Hmlhl3SLXMZ2suYt0oFrw/bEtNsj6ADTuyQAsn/GUKGJO', 'MANAGER', NULL),
 ('admin', 'Дмитрий Орлов', '$2a$12$90gv5HoLEfsmqhSm/B20dud3Ukwl0rLkOVG7XAswuYb9SJaXx538i', 'ADMIN', NULL),
 ('ivan.petrov', 'Иван Петров', '$2a$12$NWVhHptcxjwXOI9ybcsuNOd6Yvln7lfKdnnfv.QzPYox.ny8ivucm', 'USER', 'CUS-99E74EF6-31DF-4EEC-A374-8E7A8BFA200F'),
 ('maria.sokolova', 'Мария Соколова', '$2a$12$NWVhHptcxjwXOI9ybcsuNOd6Yvln7lfKdnnfv.QzPYox.ny8ivucm', 'USER', 'CUS-07F68B98-3DFF-4FAB-B1E8-07F430BC5554'),
 ('viewer', 'Сергей Морозов', '$2a$12$NWVhHptcxjwXOI9ybcsuNOd6Yvln7lfKdnnfv.QzPYox.ny8ivucm', 'VIEWER', NULL)
) v(login, name, hash, role, customer_code)
LEFT JOIN customers c ON c.customer_code = v.customer_code
ON CONFLICT (login) DO NOTHING;

INSERT INTO bookings(ticket_number, customer_id, event_id, price_at_booking, status,
                     vip, archived, description, created_at, updated_at)
SELECT 'TP-2026-' || lpad(n::text, 5, '0'), c.id, e.id, e.price,
       (ARRAY['NEW','CONFIRMED','PAID','CANCELLED'])[(n % 4) + 1]::booking_status,
       n % 5 = 0, n % 7 = 0,
       CASE WHEN n % 5 = 0 THEN 'Места в центральном секторе, сопровождение администратора'
            ELSE 'Электронный билет, уведомление о начале мероприятия' END,
       LOCALTIMESTAMP - n * INTERVAL '3 hours', LOCALTIMESTAMP
FROM generate_series(1, 36) n
JOIN customers c ON c.customer_code = (ARRAY[
 'CUS-5D844467-A4EA-4FF7-B3C9-00660BC6E293',
 'CUS-99E74EF6-31DF-4EEC-A374-8E7A8BFA200F',
 'CUS-07F68B98-3DFF-4FAB-B1E8-07F430BC5554'])[(n % 3) + 1]
JOIN events e ON e.title = (ARRAY['Джазовый концерт','Театральная премьера','Музыкальный фестиваль'])[((n / 3) % 3) + 1]
ON CONFLICT (ticket_number) DO NOTHING;

INSERT INTO booking_tags(booking_id, tag)
SELECT id, CASE WHEN vip THEN 'особое обслуживание' ELSE 'электронный билет' END
FROM bookings WHERE ticket_number LIKE 'TP-2026-%'
ON CONFLICT DO NOTHING;

INSERT INTO booking_notes(public_id, booking_id, author_id, body, pinned, created_at, updated_at)
SELECT md5('ticketpoint-note-' || b.ticket_number)::uuid, b.id, u.id,
       'Контактные данные проверены. Клиенту отправлена информация о площадке.',
       b.vip, LOCALTIMESTAMP, LOCALTIMESTAMP
FROM bookings b JOIN app_users u ON u.login = 'manager'
WHERE b.ticket_number LIKE 'TP-2026-%'
ON CONFLICT (public_id) DO NOTHING;
COMMIT;
