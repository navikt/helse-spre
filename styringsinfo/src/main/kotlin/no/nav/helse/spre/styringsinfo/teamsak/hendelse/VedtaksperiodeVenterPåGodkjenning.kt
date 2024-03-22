package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVVENTER_GODKJENNING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.*

internal class VedtaksperiodeVenterPåGodkjenning(
    override val id: UUID,
    override val opprettet: OffsetDateTime,
    override val data: JsonNode,
    private val vedtaksperiodeId: UUID,
    vedtaksperiodeIdSomVentesPå: UUID,
) : Hendelse {
    override val type = eventName
    private val behandlingsstatus = if (vedtaksperiodeId == vedtaksperiodeIdSomVentesPå) AVVENTER_GODKJENNING.name else "KOMPLETT_FAKTAGRUNNLAG"

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        behandlingshendelseDao.initialiser(SakId(vedtaksperiodeId)) ?: return false
        sikkerLogg.info("Denne tullegutten ville vi satt til behandlingsstatus $behandlingsstatus")
        return false
    }

    internal companion object {
        private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")
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
                packet.requireVedtaksperiodeId()
                packet.requireVedtaksperiodeIdSomVentesPå()
            },
            opprett = { packet -> VedtaksperiodeVenterPåGodkjenning(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                vedtaksperiodeId = packet.vedtaksperiodeId,
                vedtaksperiodeIdSomVentesPå = packet.vedtaksperiodeIdSomVentesPå
            )}
        )

        private fun JsonMessage.demandVenterPåGodkjenning() = demandValue("venterPå.venteårsak.hva", "GODKJENNING")
        private fun JsonMessage.requireVedtaksperiodeIdSomVentesPå() = require("venterPå.vedtaksperiodeId") { id -> UUID.fromString(id.asText()) }
        private val JsonMessage.vedtaksperiodeIdSomVentesPå get() = UUID.fromString(get("venterPå.vedtaksperiodeId").asText())
    }
}