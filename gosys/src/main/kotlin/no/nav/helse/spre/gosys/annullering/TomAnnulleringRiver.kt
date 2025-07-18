package no.nav.helse.spre.gosys.annullering

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.micrometer.core.instrument.MeterRegistry
import java.util.*
import kotlinx.coroutines.runBlocking
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

internal class TomAnnulleringRiver(
    rapidsConnection: RapidsConnection,
    private val annulleringDao: AnnulleringDao,
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient,
    private val eregClient: EregClient,
    private val speedClient: SpeedClient
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "vedtaksperiode_annullert")
            }
            validate { message ->
                message.requireKey(
                    "fødselsnummer",
                    "@id",
                    "vedtaksperiodeId",
                    "organisasjonsnummer",
                    "yrkesaktivitetstype"
                )
                message.require("fom", JsonNode::asLocalDate)
                message.require("tom", JsonNode::asLocalDate)
                message.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.error("forstod ikke vedtaksperiode_annullert. (se sikkerlogg for melding)")
        sikkerLogg.error("forstod ikke vedtaksperiode_annullert:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val id = UUID.fromString(packet["@id"].asText())
        try {
            val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
            withMDC(
                mapOf(
                    "meldingsreferanseId" to "$id",
                    "vedtaksperiodeId" to "$vedtaksperiodeId",
                )
            ) {
                behandleMelding(id, vedtaksperiodeId, packet)
            }
        } catch (err: Exception) {
            logg.error("Feil i melding $id i vedtaksperiode annullert-river: ${err.message}", err)
            sikkerLogg.error("Feil i melding $id i vedtaksperiode annullert-river: ${err.message}", err)
            throw err
        }
    }

    private fun behandleMelding(meldingId: UUID, vedtaksperiodeId: UUID, packet: JsonMessage) {
        logg.info("vedtaksperiode_annullert leses inn for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId")
        val tomAnnulleringMessage = TomAnnulleringMessage(
            hendelseId = meldingId,
            vedtaksperiodeId = vedtaksperiodeId,
            fødselsnummer = packet["fødselsnummer"].asText(),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            organisasjonsnummer = packet["organisasjonsnummer"].asText(),
            opprettet = packet["@opprettet"].asLocalDateTime()
        )

        val annulleringer = annulleringDao.finnAnnulleringHvisFinnes(tomAnnulleringMessage.fødselsnummer, tomAnnulleringMessage.organisasjonsnummer)
        if (annulleringer.any { it.fom <= tomAnnulleringMessage.fom && it.tom >= tomAnnulleringMessage.tom }) return

        val pdf = lagPdf(tomAnnulleringMessage.organisasjonsnummer, tomAnnulleringMessage.fødselsnummer, tomAnnulleringMessage)
        val journalpostPayload = JournalpostPayload(
            tittel = "Annullering av vedtak om sykepenger",
            bruker = JournalpostPayload.Bruker(id = tomAnnulleringMessage.fødselsnummer),
            dokumenter = listOf(
                JournalpostPayload.Dokument(
                    tittel = "Utbetaling annullert i ny løsning ${tomAnnulleringMessage.norskFom} - ${tomAnnulleringMessage.norskTom}",
                    dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                )
            ),
            eksternReferanseId = tomAnnulleringMessage.hendelseId.toString(),
        )

        if (!journalførPdf(tomAnnulleringMessage, journalpostPayload))
            return logg.warn("Feil oppstod under journalføring av annullering")

        logg.info("Annullering journalført for hendelseId=${tomAnnulleringMessage.hendelseId}")
    }

    private fun lagPdf(organisasjonsnummer: String, fødselsnummer: String, vedtaksperiodeForkastet: TomAnnulleringMessage): String {
        val organisasjonsnavn = runBlocking { finnOrganisasjonsnavn(eregClient, organisasjonsnummer) }
        logg.debug("Hentet organisasjonsnavn")
        val navn = hentNavn(speedClient, fødselsnummer, UUID.randomUUID().toString()) ?: ""
        logg.debug("Hentet søkernavn")

        val tomAnnulleringPdfPayload = vedtaksperiodeForkastet.toPdfPayload(organisasjonsnavn, navn)
        if (erUtvikling) sikkerLogg.info("tom-annullering-payload: ${objectMapper.writeValueAsString(tomAnnulleringPdfPayload)}")
        return runBlocking { pdfClient.hentAnnulleringPdf(tomAnnulleringPdfPayload) }
    }

    private fun journalførPdf(vedtaksperiodeForkastet: TomAnnulleringMessage, journalpostPayload: JournalpostPayload): Boolean {
        return runBlocking { joarkClient.opprettJournalpost(vedtaksperiodeForkastet.hendelseId, journalpostPayload) }
    }
}
