INSERT INTO roles(code)
VALUES ('VIEWER')
ON CONFLICT DO NOTHING;
ALTER TABLE app_users
    DROP CONSTRAINT app_users_role_check;
ALTER TABLE app_users
    ADD CONSTRAINT app_users_role_check
        CHECK (role IN ('ADMIN', 'MANAGER', 'USER', 'VIEWER'));
