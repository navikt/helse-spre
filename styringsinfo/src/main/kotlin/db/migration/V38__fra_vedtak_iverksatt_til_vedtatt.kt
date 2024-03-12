package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.intellij.lang.annotations.Language

internal class V38__fra_vedtak_iverksatt_til_vedtatt: BehandlingshendelseJsonMigrering() {

    @Language("postgresql")
    override fun query(): String = """
        select b.sekvensnummer, b.data, b.er_korrigert from behandlingshendelse b
            where b.data ->> 'behandlingsresultat' = 'VEDTAK_IVERKSATT'
            and b.er_korrigert = false;
    """.trimIndent()

    override fun nyVersjon(): Versjon? = null

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.put("behandlingsresultat", "VEDTATT")
        return gammelData
    }
}