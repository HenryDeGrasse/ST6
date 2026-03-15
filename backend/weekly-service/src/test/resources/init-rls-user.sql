-- Creates a non-superuser role for RLS enforcement in integration tests.
-- The superuser (weekly) runs Flyway migrations and creates tables/policies.
-- The app_user role is what the application connects as, so RLS policies
-- are enforced (superusers bypass RLS).

CREATE ROLE app_user WITH LOGIN PASSWORD 'app_pass';

-- Grant usage and create privileges on the database
GRANT CONNECT ON DATABASE weekly TO app_user;
GRANT USAGE ON SCHEMA public TO app_user;

-- Grant table-level privileges (applied after Flyway migration via
-- default privileges so future tables are also covered).
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;

-- Grant privileges on any tables that already exist (for safety)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_user;
