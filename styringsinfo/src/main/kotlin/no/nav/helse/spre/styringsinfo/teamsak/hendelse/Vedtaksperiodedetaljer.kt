package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spre.styringsinfo.sikkerLogg
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.generasjonId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireGenerasjonId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.LocalDateTime
import java.util.*

internal class Vedtaksperiodedetaljer(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val blob: JsonNode,
    private val generasjonId: UUID?,
    private val vedtaksperiodeId: UUID?,
    private val ikkeHensyntatteDetaljer: Set<String>,
    private val automatiskUtbetalt: Boolean?
) : Hendelse {

    override val type = eventName

    override fun håndter(behandlingDao: BehandlingDao): Boolean {
        val behandlingId = when (generasjonId != null) {
            true -> BehandlingId(generasjonId)
            false -> behandlingDao.forrigeBehandlingId(SakId(vedtaksperiodeId!!))
        } ?: return false

        if (ikkeHensyntatteDetaljer.isNotEmpty()) {
            sikkerLogg.warn("Det er ${ikkeHensyntatteDetaljer.size} vedtaksperiodedetaljer som ikke håndteres for {}", keyValue("behandlingId", behandlingId.toString()))
        }

        val builder = behandlingDao.initialiser(behandlingId) ?: return false

        // TODO: Putte ting inn i builderen her
        return false
    }

    internal companion object {
        private const val eventName = "vedtaksperiodedetaljer"

        private val hensyntatteDetaljer = setOf("automatiskUtbetalt")

        internal fun river(rapidsConnection: RapidsConnection, behandlingDao: BehandlingDao) = HendelseRiver(
            rapidsConnection = rapidsConnection,
            behandlingDao = behandlingDao,
            eventName = eventName,
            valider = { packet ->
                packet.interestedIn("generasjonId") {
                    if (it.isMissingOrNull()) packet.requireVedtaksperiodeId()
                    else packet.requireGenerasjonId()
                }
                packet.require("detaljer", JsonNode::isObject)
                packet.interestedIn("detaljer.automatiskUtbetalt", JsonNode::asBoolean)
            },
            opprett = { packet -> Vedtaksperiodedetaljer(
                id = packet.hendelseId,
                opprettet = packet.opprettet,
                blob = packet.blob,
                generasjonId = packet["generasjonId"].takeIf { it.isTextual }?.let { packet.generasjonId },
                vedtaksperiodeId = packet["vedtaksperiodeId"].takeIf { it.isTextual }?.let { packet.vedtaksperiodeId },
                ikkeHensyntatteDetaljer = (packet["detaljer"] as ObjectNode).fieldNames().asSequence().toSet() - hensyntatteDetaljer,
                automatiskUtbetalt = packet["detaljer.automatiskUtbetalt"].takeUnless { it.isMissingOrNull() }?.asBoolean(),
            )}
        )
    }
}