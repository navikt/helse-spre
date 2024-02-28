package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.automatiskBehandling
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.LocalDateTime
import java.util.UUID

internal class GenerasjonForkastet(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val data: JsonNode,
    private val vedtaksperiodeId: UUID,
    private val behandlingsmetode: Behandlingsmetode
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val builders = behandlingshendelseDao.initialiser(SakId(vedtaksperiodeId)).takeUnless { it.isEmpty() } ?: return false
        builders.forEach { builder ->
            val ny = builder
                .behandlingstatus(Behandling.Behandlingstatus.AVSLUTTET)
                .behandlingsresultat(Behandling.Behandlingsresultat.AVBRUTT)
                .behandlingsmetode(behandlingsmetode)
                .build(opprettet)
            behandlingshendelseDao.lagre(ny, this.id)
        }
        return true
    }

    internal companion object {
        private const val eventName = "generasjon_forkastet"

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet ->
                packet.requireVedtaksperiodeId()
                packet.interestedIn("automatiskBehandling")
            },
            opprett = { packet -> GenerasjonForkastet(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                vedtaksperiodeId = packet.vedtaksperiodeId,
                behandlingsmetode = if (packet.automatiskBehandling) AUTOMATISK else MANUELL
            )}
        )
    }
}