package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.intellij.lang.annotations.Language

/**
Revurderinger av førstegangsbehandlinger skal ha periodetype førstebehandling
 */
internal class V35__riktig_periodetype_for_revurderte_førstegangsbehandlinger: BehandlingshendelseJsonMigrering() {

    @Language("postgresql")
    override fun query(): String = """
        select b.sekvensnummer, b.data, b.er_korrigert from behandlingshendelse b
            where b.data ->> 'periodetype' = 'FORLENGELSE'
            and b.er_korrigert = false
            and exists (
                select c.sakid from behandlingshendelse c
                where c.data ->>'periodetype' = 'FØRSTEGANGSBEHANDLING'
                and c.sakid = b.sakid
            );
    """.trimIndent()

    override fun nyVersjon(): Versjon? = null

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.put("periodetype", "FØRSTEGANGSBEHANDLING")
        return gammelData
    }
}