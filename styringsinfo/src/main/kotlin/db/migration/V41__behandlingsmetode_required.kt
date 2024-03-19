package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon

internal class V41__behandlingsmetode_required: BehandlingshendelseJsonMigrering() {
    override fun query() =
        "select sekvensnummer, data, er_korrigert from behandlingshendelse where er_korrigert=false and siste=true and data ->> 'behandlingsmetode' is null"

    override fun nyVersjon() = Versjon.of("0.5.2")

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.put("behandlingsmetode", "AUTOMATISK")
        return gammelData
    }
}