package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode

internal class V42__fjerne_behandlingsresultat_vedtatt: BehandlingshendelseJsonMigrering() {
    override fun query() =
        "select sekvensnummer, data, er_korrigert from behandlingshendelse where er_korrigert=false and siste=true and data ->> 'behandlingsresultat' = 'VEDTATT' AND data ->> 'behandlingstatus' != 'AVSLUTTET'"

    override fun nyVersjon() = null

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.put("behandlingsresultat", "INNVILGET")
        return gammelData
    }
}