package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.intellij.lang.annotations.Language

/**
 * behandlingsresultat for avsluttet_med_vedtak skal hete "VEDTAK_IVERKSATT" og ikke "VEDTATT",
 * bl.a. fordi det først kommer en hendelse vedtaksperiode_godkjent som setter VEDTATT
 */
internal class V33__behandlingsresultat_avsluttet_med_vedtak: BehandlingshendelseJsonMigrering() {

    @Language("postgresql")
    override fun query(): String = """
        select b.sekvensnummer, b.data, b.er_korrigert from behandlingshendelse b
            left join hendelse h on h.id = b.hendelseid
            where b.data ->> 'behandlingsresultat' = 'VEDTATT'
            and h.type = 'avsluttet_med_vedtak'
            and b.er_korrigert = false;
    """.trimIndent()

    override fun nyVersjon(): Versjon? = null

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.put("behandlingsresultat", "VEDTAK_IVERKSATT")
        return gammelData
    }
}