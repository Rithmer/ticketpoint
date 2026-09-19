BEGIN;

INSERT INTO customers(customer_code, name) VALUES
    ('CUS-00000000-0000-0000-0000-000000000001', 'Анна Смирнова'),
    ('CUS-00000000-0000-0000-0000-000000000002', 'Иван Петров'),
    ('CUS-00000000-0000-0000-0000-000000000003', 'Мария Соколова'),
    ('CUS-00000000-0000-0000-0000-000000000004', 'Алексей Волков'),
    ('CUS-00000000-0000-0000-0000-000000000005', 'Елена Кузнецова')
ON CONFLICT (customer_code) DO NOTHING;

INSERT INTO events(title, venue, starts_at, price)
SELECT v.title, v.venue, CURRENT_DATE + v.days * INTERVAL '1 day' + INTERVAL '19 hours', v.price
FROM (VALUES
    ('Демо: Джазовый концерт', 'Большой зал', 30, 2500.00),
    ('Демо: Театральная премьера', 'Городской театр', 45, 6000.00),
    ('Демо: Музыкальный фестиваль', 'Открытая сцена', 60, 8500.00)
) AS v(title, venue, days, price)
WHERE NOT EXISTS (SELECT 1 FROM events e WHERE e.title = v.title AND e.venue = v.venue);

INSERT INTO bookings(ticket_number, customer_id, event_id, price_at_booking, status, created_at)
SELECT 'TP-DEMO-' || lpad(n::text, 3, '0'), c.id,
    (SELECT e.id FROM events e
     WHERE e.title = (ARRAY['Демо: Джазовый концерт', 'Демо: Театральная премьера', 'Демо: Музыкальный фестиваль'])[(n - 1) % 3 + 1]
     ORDER BY e.id LIMIT 1),
    (SELECT e.price FROM events e
     WHERE e.title = (ARRAY['Демо: Джазовый концерт', 'Демо: Театральная премьера', 'Демо: Музыкальный фестиваль'])[(n - 1) % 3 + 1]
     ORDER BY e.id LIMIT 1),
    (ARRAY['NEW', 'PAID', 'CANCELLED'])[(n - 1) % 3 + 1]::booking_status,
    LOCALTIMESTAMP - (11 - n) * INTERVAL '1 day'
FROM generate_series(1, 10) AS numbers(n)
JOIN customers c ON c.customer_code = 'CUS-00000000-0000-0000-0000-00000000000' || ((n - 1) % 5 + 1)::text
ON CONFLICT (ticket_number) DO NOTHING;

COMMIT;
