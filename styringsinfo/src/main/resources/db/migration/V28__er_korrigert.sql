ALTER TABLE behandlingshendelse ADD COLUMN er_korrigert BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE behandlingshendelse ALTER COLUMN er_korrigert DROP DEFAULT;

