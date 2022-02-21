ALTER TABLE hendelse_id_mapping DROP CONSTRAINT hendelse_id_mapping_pkey;
ALTER TABLE hendelse_id_mapping ADD PRIMARY KEY (hendelse_id, dokument_id);