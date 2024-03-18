package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spre.styringsinfo.log
import no.nav.helse.spre.styringsinfo.sikkerLogg
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVSLAG
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.DELVIS_INNVILGET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.behandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireBehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag.Arbeidsgiverutbetaling
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag.Avslag
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag.DelvisInnvilget
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag.IngenUtbetaling
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag.Innvilget
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag.NegativArbeidsgiverutbetaling
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag.NegativPersonutbetaling
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag.Personutbetaling
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag.entries
import java.lang.IllegalArgumentException
import java.time.LocalDateTime
import java.util.UUID

// Skal lese inn vedtak_fattet-event kun for perioder med vedtak, ikke AUU
internal class VedtakFattet(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val data: JsonNode,
    private val behandlingId: UUID,
    private val tags: List<Tag> = emptyList()
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val builder = behandlingshendelseDao.initialiser(BehandlingId(behandlingId)) ?: return false
        val mottaker = mottaker(tags, behandlingId, id)
        val behandlingsresultat = behandlingsresultat(tags, behandlingId, id)
        val ny = builder
            .behandlingstatus(AVSLUTTET)
            .mottaker(mottaker)
            .behandlingsresultat(behandlingsresultat)
            .build(opprettet, AUTOMATISK)
            ?: return false
        return behandlingshendelseDao.lagre(ny, this.id)
    }

    internal companion object {
        private const val eventName = "vedtak_fattet"

        enum class Tag {
            IngenUtbetaling,
            NegativPersonutbetaling,
            Personutbetaling,
            Arbeidsgiverutbetaling,
            NegativArbeidsgiverutbetaling,
            Innvilget,
            DelvisInnvilget,
            Avslag,
            EnArbeidsgiver,
            FlereArbeidsgivere,
            `6GBegrenset`,
            SykepengegrunnlagUnder2G,
            IngenNyArbeidsgiverperiode
        }

        private fun valueOfOrNull(name: String): Tag? {
            return entries.firstOrNull { it.name == name }
        }

        private fun List<String>.tilKjenteTags(): List<Tag> {
            return mapNotNull { streng ->
                valueOfOrNull(streng).also {
                    if (it == null) {
                        log.warn("$streng er en ukjent Tag for spre-styringsinfo. Vurder å legge den inn, kanskje dette er nyttig data?")
                        sikkerLogg.warn("$streng er en ukjent Tag for spre-styringsinfo. Vurder å legge den inn, kanskje dette er nyttig data?")
                    }
                }
            }
        }

        private fun mottaker(tags: List<Tag>, behandlingId: UUID, hendelseId: UUID): Behandling.Mottaker {
            val sykmeldtErMottaker = tags.any { it in listOf(Personutbetaling, NegativPersonutbetaling) }
            val arbeidsgiverErMottaker = tags.any { it in listOf(Arbeidsgiverutbetaling, NegativArbeidsgiverutbetaling) }
            val ingenErMottaker = tags.contains(IngenUtbetaling)
            return when {
                ingenErMottaker -> Behandling.Mottaker.INGEN
                sykmeldtErMottaker && arbeidsgiverErMottaker -> Behandling.Mottaker.SYKMELDT_OG_ARBEIDSGIVER
                sykmeldtErMottaker -> Behandling.Mottaker.SYKMELDT
                arbeidsgiverErMottaker -> Behandling.Mottaker.ARBEIDSGIVER
                else -> throw IllegalArgumentException("Nå kom det jaggu et vedtak_fattet-event med en mottaker jeg ikke klarte å tolke. Dette må være en feil. Ta en titt på behandling $behandlingId fra hendelse $hendelseId.")
            }
        }

        private fun behandlingsresultat(tags: List<Tag>, behandlingId: UUID, hendelseId: UUID): Behandling.Behandlingsresultat {
            return when {
                tags.any { it == Innvilget } -> Behandling.Behandlingsresultat.INNVILGET
                tags.any { it == DelvisInnvilget } -> DELVIS_INNVILGET
                tags.any { it == Avslag } -> AVSLAG
                else -> throw IllegalArgumentException("Nå kom det jaggu et vedtak_fattet-event med et behandlingsresultat jeg ikke klarte å tolke. Dette må være en feil. Ta en titt på behandling $behandlingId fra hendelse $hendelseId.")
            }
        }

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = {
                packet ->
                    packet.requireBehandlingId()
                    packet.requireTags()
                    packet.demandSykepengegrunnlagfakta()
                    packet.demandUtbetalingId()
            },
            opprett = { packet -> VedtakFattet(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                behandlingId = packet.behandlingId,
                tags = packet.tags.tilKjenteTags()
            )}
        )

        private val JsonMessage.tags get() = this["tags"].map { it.asText() }
        private fun JsonMessage.requireTags() = requireKey("tags")
        private fun JsonMessage.demandUtbetalingId() = demand("utbetalingId") { utbetalingId -> UUID.fromString(utbetalingId.asText()) }
        private fun JsonMessage.demandSykepengegrunnlagfakta() = demand("sykepengegrunnlagsfakta") {
            sykepengegrunnlagsfakta -> require(!sykepengegrunnlagsfakta.isMissingOrNull())
        }
    }
}