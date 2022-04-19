ALTER TABLE hendelse_dokument_mapping DROP CONSTRAINT hendelse_dokument_mapping_pkey;

ALTER TABLE hendelse_dokument_mapping ADD CONSTRAINT hendelse_dokument_mapping_pkey PRIMARY KEY (hendelse_id, dokument_id_type);
