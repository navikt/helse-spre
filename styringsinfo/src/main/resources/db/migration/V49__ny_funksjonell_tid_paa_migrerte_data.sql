with migrerter_dingebomser as (
    select sekvensnummer from behandlingshendelse b
    left join hendelse h on h.id = b.hendelseid where h.type='pågående_behandlinger'
) update behandlingshendelse
set funksjonellTid = (data->>'registrertTid')::timestamptz
where sekvensnummer in (select sekvensnummer from migrerter_dingebomser)