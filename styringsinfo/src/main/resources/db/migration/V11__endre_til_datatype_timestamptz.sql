TRUNCATE TABLE sendt_soknad;
TRUNCATE TABLE vedtak_dokument_mapping;
TRUNCATE TABLE vedtak_fattet;
TRUNCATE TABLE vedtak_forkastet;

ALTER TABLE sendt_soknad DROP COLUMN sendt;
ALTER TABLE sendt_soknad ADD COLUMN sendt TIMESTAMPTZ NOT NULL;
ALTER TABLE vedtak_fattet DROP COLUMN vedtak_fattet_tidspunkt;
ALTER TABLE vedtak_fattet ADD COLUMN vedtak_fattet_tidspunkt TIMESTAMPTZ NOT NULL;
ALTER TABLE vedtak_forkastet DROP COLUMN forkastet_tidspunkt;
ALTER TABLE vedtak_forkastet ADD COLUMN forkastet_tidspunkt TIMESTAMPTZ NOT NULL;
COMMENT ON COLUMN vedtak_forkastet.forkastet_tidspunkt IS 'Hentes fra feltet @opprettet i selve meldingen';



