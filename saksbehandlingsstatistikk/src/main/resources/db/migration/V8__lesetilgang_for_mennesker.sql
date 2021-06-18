
DO
$do$
    BEGIN
        IF NOT EXISTS (
            SELECT
            FROM   pg_catalog.pg_roles
            WHERE  rolname = 'my_user'
        )
        THEN
            CREATE ROLE cloudsqliamuser;
        END IF;
    END
$do$;

alter default privileges in schema public grant all on tables to cloudsqliamuser;
