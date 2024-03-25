package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode

internal class V45__rename_behandlingstype_omgjøring: BehandlingshendelseJsonMigrering() {
    override fun query() =
        "select sekvensnummer, data, er_korrigert from behandlingshendelse where er_korrigert=false and siste=true and data ->> 'behandlingtype' = 'OMGJØRING'"

    override fun nyVersjon() = null

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.put("behandlingtype", "GJENÅPNING")
        return gammelData
    }
}