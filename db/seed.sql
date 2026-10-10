BEGIN;

INSERT INTO customers(customer_code, name) VALUES
    ('CUS-5D844467-A4EA-4FF7-B3C9-00660BC6E293', 'Анна Смирнова'),
    ('CUS-99E74EF6-31DF-4EEC-A374-8E7A8BFA200F', 'Иван Петров'),
    ('CUS-07F68B98-3DFF-4FAB-B1E8-07F430BC5554', 'Мария Соколова'),
    ('CUS-84385E8F-C8AC-4CD1-B0D8-191EE2D3063D', 'Алексей Волков'),
    ('CUS-389F2341-84E1-4372-8E3D-69E1F6FEF030', 'Елена Кузнецова')
ON CONFLICT (customer_code) DO NOTHING;

INSERT INTO events(title, venue, starts_at, price)
SELECT v.title, v.venue, CURRENT_DATE + v.days * INTERVAL '1 day' + INTERVAL '19 hours', v.price
FROM (VALUES
    ('Джазовый концерт', 'Большой зал', 30, 2500.00),
    ('Театральная премьера', 'Городской театр', 45, 6000.00),
    ('Музыкальный фестиваль', 'Открытая сцена', 60, 8500.00)
) AS v(title, venue, days, price)
WHERE NOT EXISTS (SELECT 1 FROM events e WHERE e.title = v.title AND e.venue = v.venue);

INSERT INTO bookings(ticket_number, customer_id, event_id, price_at_booking, status, created_at)
SELECT v.ticket_number, c.id, e.id, e.price, v.status::booking_status,
       LOCALTIMESTAMP - v.days_ago * INTERVAL '1 day'
FROM (VALUES
    ('TP-B3D9B984-FCC3-4A40-9239-C764D976EC8E', 'CUS-5D844467-A4EA-4FF7-B3C9-00660BC6E293', 'Джазовый концерт', 'Большой зал', 'NEW', 10),
    ('TP-5AA382A1-05EE-4F20-8FB6-7AF029D8BF7E', 'CUS-99E74EF6-31DF-4EEC-A374-8E7A8BFA200F', 'Театральная премьера', 'Городской театр', 'PAID', 9),
    ('TP-F45BF66D-4428-481C-B0F9-D74B4827771B', 'CUS-07F68B98-3DFF-4FAB-B1E8-07F430BC5554', 'Музыкальный фестиваль', 'Открытая сцена', 'CANCELLED', 8),
    ('TP-B7A9A817-6C93-466E-AF3D-80AA6B15E1F6', 'CUS-84385E8F-C8AC-4CD1-B0D8-191EE2D3063D', 'Джазовый концерт', 'Большой зал', 'NEW', 7),
    ('TP-FA6162AE-3514-4C0E-8D0B-65C3CB1AC8D0', 'CUS-389F2341-84E1-4372-8E3D-69E1F6FEF030', 'Театральная премьера', 'Городской театр', 'PAID', 6),
    ('TP-557D67C0-F1C8-4EEB-8E3B-87AEDC7DA6F8', 'CUS-5D844467-A4EA-4FF7-B3C9-00660BC6E293', 'Музыкальный фестиваль', 'Открытая сцена', 'CANCELLED', 5),
    ('TP-4D1ED4DD-2F1A-41C9-B229-6ED28FB77B78', 'CUS-99E74EF6-31DF-4EEC-A374-8E7A8BFA200F', 'Джазовый концерт', 'Большой зал', 'NEW', 4),
    ('TP-94CC6A18-23B1-4297-B4DB-136EAD734666', 'CUS-07F68B98-3DFF-4FAB-B1E8-07F430BC5554', 'Театральная премьера', 'Городской театр', 'PAID', 3),
    ('TP-3BC09667-E3FA-4A18-A176-8C6AD95D9E78', 'CUS-84385E8F-C8AC-4CD1-B0D8-191EE2D3063D', 'Музыкальный фестиваль', 'Открытая сцена', 'CANCELLED', 2),
    ('TP-74B5C567-21F3-4CC8-AE4C-9968CAD65A40', 'CUS-389F2341-84E1-4372-8E3D-69E1F6FEF030', 'Джазовый концерт', 'Большой зал', 'NEW', 1)
) AS v(ticket_number, customer_code, title, venue, status, days_ago)
JOIN customers c ON c.customer_code = v.customer_code
JOIN events e ON e.title = v.title AND e.venue = v.venue
ON CONFLICT (ticket_number) DO NOTHING;

COMMIT;
