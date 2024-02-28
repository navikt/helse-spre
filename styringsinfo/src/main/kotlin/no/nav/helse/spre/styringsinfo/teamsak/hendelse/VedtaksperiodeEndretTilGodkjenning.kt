package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVVENTER_GODKJENNING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Periodetype.FORLENGELSE
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Periodetype.FØRSTEGANGSBEHANDLING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.LocalDateTime
import java.util.*

internal class VedtaksperiodeEndretTilGodkjenning(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val data: JsonNode,
    private val vedtaksperiodeId: UUID
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val generasjonId = behandlingshendelseDao.behandlingIdFraForrigeBehandlingshendelse(SakId(vedtaksperiodeId)) ?: return false
        val builder = behandlingshendelseDao.initialiser(generasjonId) ?: return false
        val periodetype = if (behandlingshendelseDao.erFørstegangsbehandling(generasjonId)) FØRSTEGANGSBEHANDLING else FORLENGELSE
        val ny = builder
            .behandlingstatus(AVVENTER_GODKJENNING)
            .behandlingsmetode(AUTOMATISK)
            .periodetype(periodetype)
            .build(opprettet)
        behandlingshendelseDao.lagre(ny, this.id)
        return true
    }

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