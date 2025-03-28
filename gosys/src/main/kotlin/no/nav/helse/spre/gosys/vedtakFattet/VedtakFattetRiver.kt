package no.nav.helse.spre.gosys.vedtakFattet

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.micrometer.core.instrument.MeterRegistry
import java.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.EregClient
import no.nav.helse.spre.gosys.JoarkClient
import no.nav.helse.spre.gosys.JournalpostPayload
import no.nav.helse.spre.gosys.PdfClient
import no.nav.helse.spre.gosys.erUtvikling
import no.nav.helse.spre.gosys.finnOrganisasjonsnavn
import no.nav.helse.spre.gosys.hentNavn
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.gosys.sikkerLogg
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import no.nav.helse.spre.gosys.utbetaling.Utbetaling.Companion.IkkeUtbetalingsdagtyper
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.vedtak.VedtakMessage
import no.nav.helse.spre.gosys.vedtak.slåSammenLikePerioder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakFattetDao: VedtakFattetDao,
    private val utbetalingDao: UtbetalingDao,
    private val duplikatsjekkDao: DuplikatsjekkDao,
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient,
    private val eregClient: EregClient,
    private val speedClient: SpeedClient
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "vedtak_fattet")
                it.requireKey("sykepengegrunnlagsfakta")
                it.require("utbetalingId") { id -> UUID.fromString(id.asText()) }
            }
            validate { message ->
                message.requireKey(
                    "fødselsnummer",
                    "@id",
                    "vedtaksperiodeId",
                    "organisasjonsnummer",
                    "sykepengegrunnlag"
                )
                message.require("fom", JsonNode::asLocalDate)
                message.require("tom", JsonNode::asLocalDate)
                message.require("skjæringstidspunkt", JsonNode::asLocalDate)
                message.require("@opprettet", JsonNode::asLocalDateTime)
                message.interestedIn("begrunnelser")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        tjenestekall.info("Noe gikk galt: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val id = UUID.fromString(packet["@id"].asText())
        try {
            duplikatsjekkDao.sjekkDuplikat(id) {
                val utbetalingId = packet["utbetalingId"].asText().toUUID()
                val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
                withMDC(mapOf(
                    "meldingsreferanseId" to "$id",
                    "vedtaksperiodeId" to "$vedtaksperiodeId",
                    "utbetalingId" to "$utbetalingId"
                )) {
                    behandleMelding(id, utbetalingId, vedtaksperiodeId, packet)
                }
            }
        }  catch (err: Exception) {
            logg.error("Feil i melding $id i vedtak fattet-river: ${err.message}", err)
            sikkerLogg.error("Feil i melding $id i vedtak fattet-river: ${err.message}", err)
            throw err
        }
    }

    private fun behandleMelding(meldingId: UUID, utbetalingId: UUID, vedtaksperiodeId: UUID, packet: JsonMessage) {
        logg.info("vedtak_fattet leses inn for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId")

        if (erDuplikatBehandling(utbetalingId, vedtaksperiodeId))
            return logg.warn("har allerede behandlet vedtak_fattet for vedtaksperiode $vedtaksperiodeId og utbetaling $utbetalingId")

        val vedtakFattet = lagreVedtakFattet(meldingId, packet)
        val utbetaling = checkNotNull(utbetalingDao.finnUtbetalingData(utbetalingId)) {
            "forventer å finne utbetaling for vedtak for $vedtaksperiodeId"
        }
        check(utbetaling.type in setOf(Utbetaling.Utbetalingtype.UTBETALING, Utbetaling.Utbetalingtype.REVURDERING)) {
            "vedtaket for $vedtaksperiodeId peker på utbetalingtype ${utbetaling.type}. Forventer kun Utbetaling/Revurdering"
        }
        behandleVedtakFattet(vedtakFattet, utbetaling)
    }

    private fun lagreVedtakFattet(id: UUID, packet: JsonMessage): VedtakFattetData {
        val vedtakFattet = VedtakFattetData.fromJson(id, packet)
        vedtakFattetDao.lagre(vedtakFattet, packet.toJson())
        logg.info("vedtak_fattet lagret for vedtaksperiode med vedtaksperiodeId ${vedtakFattet.vedtaksperiodeId} på id $id")
        return vedtakFattet
    }

    private fun erDuplikatBehandling(utbetalingId: UUID, vedtaksperiodeId: UUID): Boolean {
        val tidligereVedtakFattet = vedtakFattetDao.finnVedtakFattetData(utbetalingId) ?: return false

        check(tidligereVedtakFattet.vedtaksperiodeId == vedtaksperiodeId) {
            "det finnes et tidligere vedtak (vedtaksperiode ${tidligereVedtakFattet.vedtaksperiodeId}) " +
                "med samme utbetalingId, som er ulik vedtaksperiode $vedtaksperiodeId"
        }

        return vedtakFattetDao.erJournalført(tidligereVedtakFattet)
    }

    private fun behandleVedtakFattet(vedtakFattet: VedtakFattetData, utbetaling: Utbetaling) {
        // justerer perioden ved å hoppe over AIG-dager i snuten (todo: gjøre dette i spleis?)
        val (søknadsperiodeFom, søknadsperiodeTom) = utbetaling.søknadsperiode(vedtakFattet.fom to vedtakFattet.tom)
        val vedtak = VedtakMessage(
            utbetalingId = utbetaling.utbetalingId,
            opprettet = utbetaling.opprettet,
            fødselsnummer = utbetaling.fødselsnummer,
            skjæringstidspunkt = vedtakFattet.skjæringstidspunkt,
            type = utbetaling.type,
            fom = søknadsperiodeFom,
            tom = søknadsperiodeTom,
            organisasjonsnummer = utbetaling.organisasjonsnummer,
            gjenståendeSykedager = utbetaling.gjenståendeSykedager,
            automatiskBehandling = utbetaling.automatiskBehandling,
            godkjentAv = utbetaling.ident,
            godkjentAvEpost = utbetaling.epost,
            maksdato = utbetaling.maksdato,
            sykepengegrunnlag = vedtakFattet.sykepengegrunnlag,
            sumNettobeløp = utbetaling.arbeidsgiverOppdrag.nettoBeløp + utbetaling.personOppdrag.nettoBeløp,
            sumTotalBeløp = utbetaling.arbeidsgiverOppdrag.utbetalingslinjer.sumOf { it.totalbeløp } + utbetaling.personOppdrag.utbetalingslinjer.sumOf { it.totalbeløp },
            arbeidsgiverFagsystemId = utbetaling.arbeidsgiverOppdrag.fagsystemId,
            arbeidsgiverlinjer = utbetaling.arbeidsgiverOppdrag.utbetalingslinjer,
            personFagsystemId = utbetaling.personOppdrag.fagsystemId,
            personlinjer = utbetaling.personOppdrag.utbetalingslinjer,
            avvistePerioder = utbetaling
                .utbetalingsdager
                .filter { it.type in IkkeUtbetalingsdagtyper }
                .filterNot { dag -> dag.dato < vedtakFattet.skjæringstidspunkt }
                .map { dag ->
                    VedtakMessage.AvvistPeriode(
                        fom = dag.dato,
                        tom = dag.dato,
                        type = dag.type,
                        begrunnelser = dag.begrunnelser
                    )
                }
                .slåSammenLikePerioder(),
            sykepengegrunnlagsfakta = vedtakFattet.sykepengegrunnlagsfakta,
            begrunnelser = vedtakFattet.begrunnelser
        )

        val pdf = lagPdf(utbetaling.organisasjonsnummer, vedtakFattet.fødselsnummer, vedtak)
        logg.debug("Hentet pdf")

        val journalpostPayload = JournalpostPayload(
            tittel = journalpostTittel(vedtak.type),
            bruker = JournalpostPayload.Bruker(id = vedtak.fødselsnummer),
            dokumenter = listOf(
                JournalpostPayload.Dokument(
                    tittel = dokumentTittel(vedtak),
                    dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                )
            ),
            eksternReferanseId = vedtak.utbetalingId.toString(),
        )

        if (!journalførPdf(vedtak, journalpostPayload)) return logg.warn("Feil oppstod under journalføring av vedtak")

        vedtakFattetDao.journalført(vedtakFattet.id)

        logg.info("Vedtak journalført for utbetalingId: ${vedtak.utbetalingId}")
        sikkerLogg.info("Vedtak journalført for fødselsnummer=${vedtak.fødselsnummer} utbetalingId: ${vedtak.utbetalingId}")
    }

    private fun lagPdf(organisasjonsnummer: String, fødselsnummer: String, vedtak: VedtakMessage): String {
        val organisasjonsnavn = runBlocking { finnOrganisasjonsnavn(eregClient, organisasjonsnummer) }
        logg.debug("Hentet organisasjonsnavn")
        val navn = hentNavn(speedClient, fødselsnummer, UUID.randomUUID().toString()) ?: ""
        logg.debug("Hentet søkernavn")

        val vedtakPdfPayload = vedtak.toVedtakPdfPayloadV2(organisasjonsnavn, navn)
        if (erUtvikling) sikkerLogg.info("vedtak-payload: ${objectMapper.writeValueAsString(vedtakPdfPayload)}")
        return runBlocking { pdfClient.hentVedtakPdfV2(vedtakPdfPayload) }
    }

    private fun journalførPdf(vedtak: VedtakMessage, journalpostPayload: JournalpostPayload): Boolean {
        return runBlocking { joarkClient.opprettJournalpost(vedtak.utbetalingId, journalpostPayload) }
    }

    private fun dokumentTittel(vedtakMessage: VedtakMessage): String {
        return when (vedtakMessage.type) {
            Utbetaling.Utbetalingtype.UTBETALING -> "Sykepenger behandlet, ${vedtakMessage.norskFom} - ${vedtakMessage.norskTom}"
            Utbetaling.Utbetalingtype.ETTERUTBETALING -> "Sykepenger etterutbetalt, ${vedtakMessage.norskFom} - ${vedtakMessage.norskTom}"
            Utbetaling.Utbetalingtype.REVURDERING -> "Sykepenger revurdert, ${vedtakMessage.norskFom} - ${vedtakMessage.norskTom}"
            Utbetaling.Utbetalingtype.ANNULLERING -> throw IllegalArgumentException("Forsøkte å opprette vedtaksnotat for annullering")
        }
    }


    private fun journalpostTittel(type: Utbetaling.Utbetalingtype): String {
        return when (type) {
            Utbetaling.Utbetalingtype.UTBETALING -> "Vedtak om sykepenger"
            Utbetaling.Utbetalingtype.ETTERUTBETALING -> "Vedtak om etterutbetaling av sykepenger"
            Utbetaling.Utbetalingtype.REVURDERING -> "Vedtak om revurdering av sykepenger"
            Utbetaling.Utbetalingtype.ANNULLERING -> throw IllegalArgumentException("Forsøkte å opprette vedtaksnotat for annullering")
        }
    }
}
