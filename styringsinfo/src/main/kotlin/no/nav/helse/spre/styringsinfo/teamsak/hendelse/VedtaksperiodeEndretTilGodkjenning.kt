package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVVENTER_GODKJENNING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.OffsetDateTime
import java.util.*

internal class VedtaksperiodeEndretTilGodkjenning(
    override val id: UUID,
    override val opprettet: OffsetDateTime,
    override val data: JsonNode,
    private val vedtaksperiodeId: UUID
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val sakId = SakId(vedtaksperiodeId)
        val builder = behandlingshendelseDao.initialiser(sakId) ?: return false
        val ny = builder
            .behandlingstatus(AVVENTER_GODKJENNING)
            .build(opprettet, AUTOMATISK)
            ?: return false
        return behandlingshendelseDao.lagre(ny, this.id)
    }

    override fun ignorer(behandlingshendelseDao: BehandlingshendelseDao) =
        behandlingshendelseDao.hent(SakId(vedtaksperiodeId))?.behandlingstatus == AVSLUTTET

    internal companion object {
        private const val eventName = "vedtaksperiode_endret"

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet ->
                packet.demand("gjeldendeTilstand") { check(it.asText().startsWith("AVVENTER_GODKJENNING")) }
                packet.requireVedtaksperiodeId()
            },
            opprett = { packet -> VedtaksperiodeEndretTilGodkjenning(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                vedtaksperiodeId = packet.vedtaksperiodeId
            )}
        )
    }
}