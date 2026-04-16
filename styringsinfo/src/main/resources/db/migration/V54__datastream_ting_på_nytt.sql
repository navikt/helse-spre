-- 1. Grant datastream bruker rettigheter til å replikere og lese fra public schema, om den ikke alt har det
DO $$BEGIN
    IF
        EXISTS (SELECT 1 from pg_roles where rolname = 'spre-styringsinfo')
    THEN
        ALTER USER "spre-styringsinfo" WITH REPLICATION;
    END IF;
END$$;

DO $$BEGIN
    IF
        EXISTS (SELECT 1 from pg_roles where rolname = 'bigquery-datastream')
        THEN
            ALTER USER "bigquery-datastream" WITH REPLICATION;
            ALTER
                DEFAULT PRIVILEGES IN SCHEMA public GRANT
                SELECT
                ON TABLES TO "bigquery-datastream";
            GRANT
                USAGE
                ON
                SCHEMA
                public TO "bigquery-datastream";
            GRANT
                SELECT
                ON ALL TABLES IN SCHEMA public TO "bigquery-datastream";
    END IF;
END$$;

-- 2. Lag replication slot om den ikke allerede finnes
DO $$BEGIN
    IF
        NOT EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = 'spre_styringsinfo_replication') THEN
        PERFORM pg_create_logical_replication_slot('spre_styringsinfo_replication', 'pgoutput');
    END IF;
END$$;