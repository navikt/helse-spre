package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode

internal class V30__korrigerer_behandlingsresultat_ved_vedtaksperiode_avvist: BehandlingshendelseJsonMigrering() {
    override fun query() = """
        select b.sekvensnummer, b.data, b.er_korrigert from behandlingshendelse b
        left join hendelse h on h.id = b.hendelseid
        where b.data ->> 'behandlingsresultat' = 'VEDTATT'
        and h.type='vedtaksperiode_avvist';
    """

    override fun nyVersjon() = null

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.put("behandlingsresultat", "AVBRUTT")
        return gammelData
    }
}