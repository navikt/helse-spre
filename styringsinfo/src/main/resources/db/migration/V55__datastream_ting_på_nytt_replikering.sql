-- 1. Lag replication slot om den ikke allerede finnes
DO $$BEGIN
    IF
        NOT EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = 'spre_styringsinfo_replication') THEN
        PERFORM pg_create_logical_replication_slot('spre_styringsinfo_replication', 'pgoutput');
    END IF;
END$$;