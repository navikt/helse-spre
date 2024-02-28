package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.intellij.lang.annotations.Language

/**
 * behandlingsstatus for vedtaksperiode_godkjent skal hete "GODKJENT" og ikke "AVSLUTTET",
 * bl.a. fordi det kommer en hendelse etter vedtaksperiode_godkjent som avslutter
 */
internal class V32__behandlingsstatus_vedtaksperiode_godkjent: BehandlingshendelseJsonMigrering() {

    @Language("postgresql")
    override fun query(): String = """
        select b.sekvensnummer, b.data, b.er_korrigert from behandlingshendelse b
            left join hendelse h on h.id = b.hendelseid
            where b.data ->> 'behandlingstatus' = 'AVSLUTTET'
            and h.type = 'vedtaksperiode_godkjent'
            and b.er_korrigert = false;
    """.trimIndent()

    override fun nyVersjon(): Versjon? = null

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.put("behandlingstatus", "GODKJENT")
        return gammelData
    }
}