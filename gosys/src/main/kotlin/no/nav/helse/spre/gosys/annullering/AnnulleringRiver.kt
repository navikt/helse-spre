package no.nav.helse.spre.gosys.annullering

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.micrometer.core.instrument.MeterRegistry
import java.util.*
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.kv
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

class AnnulleringRiver(
    rapidsConnection: RapidsConnection,
    private val duplikatsjekkDao: DuplikatsjekkDao,
    private val pdfClient: PdfClient,
    private val eregClient: EregClient,
    private val joarkClient: JoarkClient,
    private val speedClient: SpeedClient
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "utbetaling_annullert") }
            validate {
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.requireKey(
                    "@id",
                    "fødselsnummer",
                    "organisasjonsnummer",
                    "ident",
                    "epost",
                    "personFagsystemId",
                    "arbeidsgiverFagsystemId",
                    "utbetalingId",
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("tidspunkt", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        sikkerLogg.error("forstår ikke utbetaling_annullert:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val id = UUID.fromString(packet["@id"].asText())
        try {
            duplikatsjekkDao.sjekkDuplikat(id) {
                withMDC(mapOf("meldingsreferanseId" to "$id")) {
                    behandleMelding(id, packet)
                }
            }
        } catch (err: Exception) {
            logg.error("Feil i melding $id i annullering-river: ${err.message}", err)
            sikkerLogg.error("Feil i melding $id i annullering-river: ${err.message}", err)
            throw err
        }
    }

    private fun behandleMelding(id: UUID, packet: JsonMessage) {
        logg.info("Oppdaget annullering-event {}", kv("id", packet["@id"].asText()))
        sikkerLogg.info("utbetaling_annullert lest inn: {}", packet.toJson())
        val annulleringMessage = AnnulleringMessage(
            hendelseId = id,
            utbetalingId = UUID.fromString(packet["utbetalingId"].asText()),
            fødselsnummer = packet["fødselsnummer"].asText(),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            organisasjonsnummer = packet["organisasjonsnummer"].asText(),
            dato = packet["tidspunkt"].asLocalDateTime(),
            saksbehandlerIdent = packet["ident"].asText(),
            saksbehandlerEpost = packet["epost"].asText(),
            personFagsystemId = packet["personFagsystemId"].takeUnless { it.isMissingOrNull() }?.asText(),
            arbeidsgiverFagsystemId = packet["arbeidsgiverFagsystemId"].takeUnless { it.isMissingOrNull() }?.asText()
        )

        val pdf = lagPdf(annulleringMessage.organisasjonsnummer, annulleringMessage.fødselsnummer, annulleringMessage)
        val journalpostPayload = JournalpostPayload(
            tittel = "Annullering av vedtak om sykepenger",
            bruker = JournalpostPayload.Bruker(id = annulleringMessage.fødselsnummer),
            dokumenter = listOf(
                JournalpostPayload.Dokument(
                    tittel = "Utbetaling annullert i ny løsning ${annulleringMessage.norskFom} - ${annulleringMessage.norskTom}",
                    dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                )
            ),
            eksternReferanseId = annulleringMessage.utbetalingId.toString(),
        )

        if (!journalførPdf(annulleringMessage, journalpostPayload))
            return logg.warn("Feil oppstod under journalføring av annullering")

        logg.info("Annullering journalført for hendelseId=${annulleringMessage.hendelseId}")
    }

    private fun lagPdf(organisasjonsnummer: String, fødselsnummer: String, annullering: AnnulleringMessage): String {
        val organisasjonsnavn = runBlocking { finnOrganisasjonsnavn(eregClient, organisasjonsnummer) }
        logg.debug("Hentet organisasjonsnavn")
        val navn = hentNavn(speedClient, fødselsnummer, UUID.randomUUID().toString()) ?: ""
        logg.debug("Hentet søkernavn")

        val annulleringPdfPayload = annullering.toPdfPayloadV2(organisasjonsnavn, navn)
        if (erUtvikling) sikkerLogg.info("annullering-payload: ${objectMapper.writeValueAsString(annulleringPdfPayload)}")
        return runBlocking { pdfClient.hentAnnulleringPdf(annulleringPdfPayload) }
    }

    private fun journalførPdf(annullering: AnnulleringMessage, journalpostPayload: JournalpostPayload): Boolean {
        return runBlocking { joarkClient.opprettJournalpost(annullering.hendelseId, journalpostPayload) }
    }
}
