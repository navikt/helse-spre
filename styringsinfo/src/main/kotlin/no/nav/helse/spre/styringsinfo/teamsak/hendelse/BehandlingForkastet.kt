package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.ANNULLERT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.behandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireBehandlingId
import java.time.OffsetDateTime
import java.util.UUID

internal class BehandlingForkastet(
    override val id: UUID,
    override val opprettet: OffsetDateTime,
    override val data: JsonNode,
    behandlingId: UUID,
    automatiskBehandling: Boolean
) : Hendelse {
    private val hendelsesmetode = if (automatiskBehandling) AUTOMATISK else MANUELL
    private val behandlingId = BehandlingId(behandlingId)

    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val behandling = behandlingshendelseDao.hent(behandlingId) ?: return false
        if (behandling.behandlingsresultat == ANNULLERT) return false // TODO: Denne kan vi fjern når Spleis ikke forkaster generasjon når det er blitt annullert
        val builder = Behandling.Builder(behandling)
        val ny = builder
            .avslutt(AVBRUTT)
            .build(opprettet, hendelsesmetode)
            ?: return false
        return behandlingshendelseDao.lagre(ny, this.id)
    }

    internal companion object {
        private const val eventName = "behandling_forkastet"

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet ->
                packet.requireBehandlingId()
                packet.require("automatiskBehandling", JsonNode::isBoolean)
            },
            opprett = { packet -> BehandlingForkastet(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                behandlingId = packet.behandlingId,
                automatiskBehandling = packet.automatiskBehandling
            )}
        )

        private val JsonMessage.automatiskBehandling get() = this["automatiskBehandling"].asBoolean()
    }
}