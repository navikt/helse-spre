package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.behandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireBehandlingId
import java.time.LocalDateTime
import java.util.UUID

internal class AvsluttetMedVedtak(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val data: JsonNode,
    private val behandlingId: UUID
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val builder = behandlingshendelseDao.initialiser(BehandlingId(behandlingId)) ?: return false
        val ny = builder
            .behandlingstatus(Behandling.Behandlingstatus.AVSLUTTET)
            .behandlingsresultat(Behandling.Behandlingsresultat.VEDTAK_IVERKSATT)
            .behandlingsmetode(Behandling.Behandlingsmetode.AUTOMATISK)
            .build(opprettet)
        behandlingshendelseDao.lagre(ny, this.id)
        return true
    }

    internal companion object {
        private const val eventName = "avsluttet_med_vedtak"

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet -> packet.requireBehandlingId() },
            opprett = { packet -> AvsluttetMedVedtak(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                behandlingId = packet.behandlingId
            )}
        )
    }
}