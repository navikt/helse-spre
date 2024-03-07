package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spre.styringsinfo.log
import no.nav.helse.spre.styringsinfo.sikkerLogg
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.VEDTAK_IVERKSATT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Mottaker.UKJENT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.behandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireBehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag.*
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
        val mottaker = mottaker(tags, data)
        val ny = builder
            .behandlingstatus(AVSLUTTET)
            .behandlingsresultat(VEDTAK_IVERKSATT)
            .mottaker(mottaker)
            .build(opprettet, AUTOMATISK)
        behandlingshendelseDao.lagre(ny, this.id)
        return true
    }

    internal companion object {
        private const val eventName = "vedtak_fattet"

        enum class Tag {
            IngenUtbetaling,
            NegativPersonutbetaling,
            Personutbetaling,
            Arbeidsgiverutbetaling,
            NegativArbeidsgiverutbetaling,
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

        private fun mottaker(tags: List<Tag>, data: JsonNode): Behandling.Mottaker {
            if (tags.isEmpty()) return UKJENT
            val sykmeldtErMottaker = tags.any { it in listOf(Personutbetaling, NegativPersonutbetaling) }
            val arbeidsgiverErMottaker = tags.any { it in listOf(Arbeidsgiverutbetaling, NegativArbeidsgiverutbetaling) }
            val ingenErMottaker = tags.contains(IngenUtbetaling)
            loggHumbug(sykmeldtErMottaker, arbeidsgiverErMottaker, ingenErMottaker, data)
            return when {
                ingenErMottaker -> Behandling.Mottaker.INGEN
                sykmeldtErMottaker && arbeidsgiverErMottaker -> Behandling.Mottaker.SYKMELDT_OG_ARBEIDSGIVER
                sykmeldtErMottaker -> Behandling.Mottaker.SYKMELDT
                arbeidsgiverErMottaker -> Behandling.Mottaker.ARBEIDSGIVER
                else -> UKJENT
            }
        }

        private fun loggHumbug(
            sykmeldtErMottaker: Boolean,
            arbeidsgiverErMottaker: Boolean,
            ingenErMottaker: Boolean,
            data: JsonNode
        ) {
            val ingenOgNoen = ingenErMottaker && (arbeidsgiverErMottaker || sykmeldtErMottaker)
            val nadaTrue = !(ingenErMottaker || arbeidsgiverErMottaker || sykmeldtErMottaker)
            if (ingenOgNoen) {
                log.warn("Vi synes at det er litt rart at ingen har mottatt penger og noen har mottatt penger, dette må være en feil (se sikkerlogg for melding)")
                sikkerLogg.warn("Vi synes at det er litt rart at ingen har mottatt penger og noen har mottatt penger, dette må være en feil. Melding: $data")
            }
            if (nadaTrue) {
                log.warn("Vi synes det er litt rart at mottaker ikke er spesifisert når vi liksom har tatt høyde for at det kan være en ingen mottaker, dette må være en feil (se sikkerlogg for melding)")
                sikkerLogg.warn("Vi synes det er litt rart at mottaker ikke er spesifisert når vi liksom har tatt høyde for at det kan være ingen mottaker, dette må være en feil. Melding $data")
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