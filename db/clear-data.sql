\set ON_ERROR_STOP on
BEGIN;
TRUNCATE TABLE booking_notes, booking_tags, refresh_sessions, audit_events,
               bookings, app_users, customers, events RESTART IDENTITY;
-- Repair only the known legacy demo migration after its conversion to a no-op.
UPDATE flyway_schema_history SET checksum = 1488892961
WHERE version = '3' AND script = 'V3__demo_data.sql' AND checksum = 1170148325;
COMMIT;
