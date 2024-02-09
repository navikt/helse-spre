package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.Automatisk
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AvventerGodkjenning
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.LocalDateTime
import java.util.*

internal class VedtaksperiodeEndret(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val blob: JsonNode,
    private val vedtaksperiodeId: UUID
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingDao: BehandlingDao): Boolean {
        val generasjonId = behandlingDao.forrigeBehandlingId(SakId(vedtaksperiodeId)) ?: return false
        val builder = behandlingDao.initialiser(generasjonId) ?: return false
        val ny = builder
            .behandlingstatus(AvventerGodkjenning)
            .behandlingsmetode(Automatisk)
            .build(opprettet)
        behandlingDao.lagre(ny)
        return true
    }

    internal companion object {
        private const val eventName = "vedtaksperiode_endret"

        internal fun river(rapidsConnection: RapidsConnection, behandlingDao: BehandlingDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            behandlingDao = behandlingDao,
            valider = { packet ->
                packet.demand("gjeldendeTilstand") { check(it.asText().startsWith("AVVENTER_GODKJENNING")) }
                packet.requireVedtaksperiodeId()
            },
            opprett = { packet -> VedtaksperiodeEndret(
                id = packet.hendelseId,
                blob = packet.blob,
                opprettet = packet.opprettet,
                vedtaksperiodeId = packet.vedtaksperiodeId
            )}
        )
    }
}