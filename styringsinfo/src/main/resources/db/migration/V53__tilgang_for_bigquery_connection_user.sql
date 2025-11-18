DO $$BEGIN
    IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'bigquery_connection_user')
    THEN
        GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO "bigquery_connection_user";
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO "bigquery_connection_user";
    END IF;
END$$;
