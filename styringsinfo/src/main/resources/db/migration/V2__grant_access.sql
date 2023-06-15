DO $$
    BEGIN
        IF EXISTS
            ( SELECT 1 FROM pg_roles WHERE rolname='cloudsqliamuser')
        THEN
            GRANT ALL PRIVILEGES ON TABLE public.flyway_schema_history TO cloudsqliamuser;
            GRANT ALL PRIVILEGES ON TABLE public.sendt_soknad TO cloudsqliamuser;
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO cloudsqliamuser;
        END IF ;
    END
$$ ;
