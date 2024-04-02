package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode

internal class V46__fjerne_behandlingstatus_vurderer_inngangsvilkår: BehandlingshendelseJsonMigrering() {
    override fun query() =
        "select sekvensnummer, data, er_korrigert from behandlingshendelse where er_korrigert=false and siste=true and data ->> 'behandlingstatus' = 'VURDERER_INNGANGSVILKÅR'"

    override fun nyVersjon() = null

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.put("behandlingstatus", "REGISTRERT")
        return gammelData
    }
}