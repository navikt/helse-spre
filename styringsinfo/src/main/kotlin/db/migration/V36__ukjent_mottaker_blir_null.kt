package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.intellij.lang.annotations.Language

/**
Ukjent mottaker skal bare være null
 */
internal class V36__ukjent_mottaker_blir_null: BehandlingshendelseJsonMigrering() {

    @Language("postgresql")
    override fun query(): String = """
        select b.sekvensnummer, b.data, b.er_korrigert from behandlingshendelse b
            where b.data ->> 'mottaker' = 'UKJENT'
            and b.er_korrigert = false;
    """.trimIndent()

    override fun nyVersjon(): Versjon? = null

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.putNull("mottaker")
        return gammelData
    }
}