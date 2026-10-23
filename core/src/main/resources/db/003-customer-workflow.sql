ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'CONFIRMED' AFTER 'NEW';
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS customer_id BIGINT UNIQUE REFERENCES customers (id) ON DELETE RESTRICT;
ALTER TABLE app_users
    DROP CONSTRAINT IF EXISTS app_users_role_check;
ALTER TABLE app_users
    ADD CONSTRAINT app_users_role_check CHECK (role IN ('ADMIN', 'MANAGER', 'USER', 'VIEWER'));
DO
$$
    DECLARE
        account RECORD; profile_id BIGINT;
    BEGIN
        FOR account IN SELECT id, name FROM app_users WHERE role = 'USER' AND customer_id IS NULL
            LOOP
                INSERT INTO customers(customer_code, name)
                VALUES ('CUS-' || upper(gen_random_uuid()::text), account.name)
                RETURNING id INTO profile_id;
                UPDATE app_users SET customer_id = profile_id WHERE id = account.id;
            END LOOP;
    END
$$;

DO
$$
    BEGIN
        IF to_regclass('roles') IS NOT NULL THEN
            EXECUTE 'INSERT INTO roles(code) VALUES (''VIEWER'') ON CONFLICT DO NOTHING';
        END IF;
    END
$$;
