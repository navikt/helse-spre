with migrerter_dingebomser as (
    select sekvensnummer from behandlingshendelse b
    left join hendelse h on h.id = b.hendelseid where h.type='pågående_behandlinger'
) update behandlingshendelse
set funksjonellTid = to_timestamp(data->>'registrertTid', 'YYYY-MM-DD"T"HH24:MI:SS.USZ')
where sekvensnummer in (select sekvensnummer from migrerter_dingebomser)