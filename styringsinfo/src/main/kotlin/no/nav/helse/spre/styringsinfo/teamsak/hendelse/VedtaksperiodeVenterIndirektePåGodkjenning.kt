package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.KOMPLETT_FAKTAGRUNNLAG
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.behandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireBehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.OffsetDateTime
import java.util.*

internal class VedtaksperiodeVenterIndirektePåGodkjenning(
    override val id: UUID,
    override val opprettet: OffsetDateTime,
    override val data: JsonNode,
    behandlingId: UUID,
) : Hendelse {
    override val type = eventName
    private val behandlingId = BehandlingId(behandlingId)

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val builder = behandlingshendelseDao.initialiser(behandlingId) ?: return false
        val ny = builder.behandlingstatus(KOMPLETT_FAKTAGRUNNLAG).build(opprettet, AUTOMATISK) ?: return false
        return behandlingshendelseDao.lagre(ny, id)
    }

    // 'vedtaksperiode_venter' sendes veldig hyppig, så for unngå å lagre alle disse hendelsene
    // når de bare sier det samme som før så ignoreres de
    override fun ignorer(behandlingshendelseDao: BehandlingshendelseDao) =
        behandlingshendelseDao.hent(behandlingId)?.behandlingstatus == KOMPLETT_FAKTAGRUNNLAG

    internal companion object {
        private const val eventName = "vedtaksperiode_venter"

        internal fun river(
            rapidsConnection: RapidsConnection,
            hendelseDao: HendelseDao,
            behandlingshendelseDao: BehandlingshendelseDao
        ) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet ->
                packet.demandVenterPåGodkjenning()
                packet.demandVenterPåAnnenVedtaksperiode()
                packet.requireBehandlingId()
            },
            opprett = { packet -> VedtaksperiodeVenterIndirektePåGodkjenning(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                behandlingId = packet.behandlingId
            )}
        )

        private fun JsonMessage.demandVenterPåGodkjenning() = demandValue("venterPå.venteårsak.hva", "GODKJENNING")
        private fun JsonMessage.demandVenterPåAnnenVedtaksperiode() {
            requireVedtaksperiodeId()
            demand("venterPå.vedtaksperiodeId") { id ->
                UUID.fromString(id.asText()) != vedtaksperiodeId
            }
        }
    }
}