create table vedtak_dokument_mapping(
  vedtak_hendelse_id UUID not null,
  dokument_hendelse_id UUID not null
);

create index idx_vedtak_dokument_mapping_hendelse_id ON vedtak_dokument_mapping(vedtak_hendelse_id)