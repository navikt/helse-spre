package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon

internal class V36__behandlingstype_med_s: BehandlingshendelseJsonMigrering() {

    override fun query() = """
        select sekvensnummer, data, er_korrigert from behandlingshendelse
        where er_korrigert=false and data ? 'behandlingtype' 
    """

    override fun nyVersjon() = Versjon.of("1.0.0")

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.putString(NY, gammelData.path(GAMMEL).takeUnless { it.isNull }?.asText())
        gammelData.remove(GAMMEL)
        return gammelData
    }

    private companion object {
        private const val GAMMEL = "behandlingtype"
        private const val NY = "behandlingstype"
        private fun ObjectNode.putString(fieldName: String, value: String?) {
            if (value == null) putNull(fieldName)
            else put(fieldName, value)
        }
    }
}