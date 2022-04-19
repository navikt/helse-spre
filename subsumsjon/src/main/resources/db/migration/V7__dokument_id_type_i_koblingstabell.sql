ALTER TABLE hendelse_dokument_mapping ADD COLUMN dokument_id_type VARCHAR;

UPDATE hendelse_dokument_mapping SET dokument_id_type = 'Søknad'
    WHERE hendelse_type IN ('sendt_søknad_nav', 'sendt_søknad_arbeidsgiver');

UPDATE hendelse_dokument_mapping SET dokument_id_type = 'Sykmelding'
    WHERE hendelse_type IN ('ny_søknad');

UPDATE hendelse_dokument_mapping SET dokument_id_type = 'Inntektsmelding'
    WHERE hendelse_type IN ('inntektsmelding');

ALTER TABLE hendelse_dokument_mapping ALTER COLUMN dokument_id_type SET NOT NULL;
