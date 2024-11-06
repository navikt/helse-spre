package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon

internal class V40__hendelsesmetode: BehandlingshendelseJsonMigrering() {
    override fun query() = "select sekvensnummer, data, er_korrigert from behandlingshendelse where siste=true and er_korrigert=false"

    override fun nyVersjon() = Versjon.of("0.5.0")

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        val metode = gammelData.path("behandlingsmetode").takeUnless { it.isMissingOrNull() }?.asText() ?: "AUTOMATISK"
        gammelData.put("hendelsesmetode", metode)
        return gammelData
    }
}