package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon

internal class V29__uppercase_enum_verdier: BehandlingshendelseJsonMigrering() {

    override fun query() = "select * from behandlingshendelse where versjon='0.0.1' and er_korrigert=false;"

    override fun nyVersjon() = Versjon.of("0.0.2")

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.oppdaterFeltFor("behandlingstatus")
        gammelData.oppdaterFeltFor("behandlingsmetode")
        gammelData.oppdaterFeltFor("behandlingskilde")
        gammelData.oppdaterFeltFor("behandlingsresultat")
        gammelData.oppdaterFeltFor("behandlingtype")
        return gammelData
    }

    private companion object {
        private fun String.MEGA() = when(this) {
            "AvventerGodkjenning" -> "AVVENTER_GODKJENNING"
            else -> this.uppercase()
        }

        private fun ObjectNode.oppdaterFeltFor(feltnavn: String) {
            path(feltnavn).takeUnless { it.isMissingOrNull() }?.let {
                put(feltnavn, it.asText().MEGA())
            }
        }
    }
}
