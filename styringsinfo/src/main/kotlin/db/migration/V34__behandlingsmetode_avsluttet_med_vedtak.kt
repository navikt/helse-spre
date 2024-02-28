package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.intellij.lang.annotations.Language

/**
 * behandlingsmetode for avsluttet_med_vedtak skal settes til AUTOMATISK.
 */
internal class V34__behandlingsmetode_avsluttet_med_vedtak: BehandlingshendelseJsonMigrering() {

    @Language("postgresql")
    override fun query(): String = """
        select b.sekvensnummer, b.data, b.er_korrigert from behandlingshendelse b
            left join hendelse h on h.id = b.hendelseid
            where b.data ->> 'behandlingsmetode' is null
            and h.type = 'avsluttet_med_vedtak'
            and b.er_korrigert = false;
    """.trimIndent()

    override fun nyVersjon(): Versjon? = null

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.put("behandlingsmetode", "AUTOMATISK")
        return gammelData
    }
}