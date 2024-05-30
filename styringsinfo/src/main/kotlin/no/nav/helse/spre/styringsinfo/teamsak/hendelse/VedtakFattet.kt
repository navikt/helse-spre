package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.behandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireBehandlingId
import java.time.OffsetDateTime
import java.util.*

internal class VedtakFattet(
    override val id: UUID,
    override val opprettet: OffsetDateTime,
    override val data: JsonNode,
    private val behandlingId: UUID,
    private val tags: Tags
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val builder = behandlingshendelseDao.initialiser(BehandlingId(behandlingId))
        val ny = builder
            .mottaker(tags.mottaker)
            .avslutt(tags.behandlingsresultat)
            .periodetype(tags.periodetype)
            .build(opprettet, AUTOMATISK)
            ?: return false
        return behandlingshendelseDao.lagre(ny, this.id)
    }

    internal companion object {
        private const val eventName = "vedtak_fattet"

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = {
                packet ->
                    packet.requireBehandlingId()
                    packet.requireTags()
                    // Skal lese inn vedtak_fattet-event kun for perioder med vedtak, ikke AUU
                    packet.demandSykepengegrunnlagfakta()
                    packet.demandUtbetalingId()
            },
            opprett = { packet -> VedtakFattet(
                id = packet.hendelseId,
                opprettet = packet.opprettet,
                data = packet.blob,
                behandlingId = packet.behandlingId,
                tags = manuelleTags[packet.hendelseId] ?: Tags(packet.tags)
            )}
        )

        private val JsonMessage.tags get() = this["tags"].map { it.asText() }
        private fun JsonMessage.requireTags() = requireKey("tags")
        private fun JsonMessage.demandUtbetalingId() = demand("utbetalingId") { utbetalingId -> UUID.fromString(utbetalingId.asText()) }
        private fun JsonMessage.demandSykepengegrunnlagfakta() = demand("sykepengegrunnlagsfakta") {
            sykepengegrunnlagsfakta -> require(!sykepengegrunnlagsfakta.isMissingOrNull())
        }
        private val manuelleTags = mapOf(
            UUID.fromString("4ee30b61-5432-45f2-aa81-6f31726fa222") to Tags(setOf(
                Tag.Førstegangsbehandling, Tag.EnArbeidsgiver, Tag.Arbeidsgiverutbetaling, Tag.Innvilget
            )),
            UUID.fromString("a3f93a97-2d43-4060-8117-4320ab2166f3") to Tags(setOf(
                Tag.Forlengelse, Tag.FlereArbeidsgivere, Tag.IngenUtbetaling, Tag.Innvilget
            )),
            UUID.fromString("cf923f26-f39d-4346-bab3-2688f0b43468") to Tags(setOf(
                Tag.Forlengelse, Tag.FlereArbeidsgivere, Tag.NegativArbeidsgiverutbetaling, Tag.Personutbetaling, Tag.Innvilget
            ))
        )
    }
}