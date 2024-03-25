package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode

internal class V43__rename_behandlingsresultat_henlagt: BehandlingshendelseJsonMigrering() {
    override fun query() =
        "select sekvensnummer, data, er_korrigert from behandlingshendelse where er_korrigert=false and siste=true and data ->> 'behandlingsresultat' = 'HENLAGT'"

    override fun nyVersjon() = null

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.put("behandlingsresultat", "IKKE_REALITETSBEHANDLET")
        return gammelData
    }
}