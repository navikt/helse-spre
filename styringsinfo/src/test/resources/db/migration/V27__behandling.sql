create table behandling(
    sakId           UUID NOT NULL,
    behandlingId    UUID NOT NULL,
    funksjonellTid  TIMESTAMP NOT NULL,
    tekniskTid      TIMESTAMP NOT NULL,
    versjon         VARCHAR NOT NULL,
    data            JSONB NOT NULL
)