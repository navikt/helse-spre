package no.nav.helse.spre.gosys.annullering

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
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

internal class VedtaksperiodeAnnullertRiver(
    rapidsConnection: RapidsConnection,
    private val planlagtAnnulleringDao: PlanlagtAnnulleringDao,
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
                message.requireKey("vedtaksperiodeId", "@id")
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
                behandleMelding(vedtaksperiodeId)
            }
        } catch (err: Exception) {
            logg.error("Feil i melding $id i vedtaksperiode annullert-river: ${err.message}", err)
            sikkerLogg.error("Feil i melding $id i vedtaksperiode annullert-river: ${err.message}", err)
            throw err
        }
    }

    private fun behandleMelding(vedtaksperiodeId: UUID) {
        sikkerLogg.info("vedtaksperiode_annullert leses inn for vedtaksperiode med id $vedtaksperiodeId")

        val planIder = planlagtAnnulleringDao.settVedtaksperiodeAnnullert(vedtaksperiodeId)
        val ferdigAnnullertePlaner = planIder
            .mapNotNull { planlagtAnnulleringDao.finnPlanlagtAnnullering(it) }
            .filter { it.erFerdigAnnullert() }
        if (ferdigAnnullertePlaner.isEmpty()) return

        // Journalfør
        ferdigAnnullertePlaner.forEach {
            val pdf = lagPdf(it)
            lagJournalpostPayload(it, pdf)
        }
    }

    private fun lagPdf(plan: PlanlagtAnnullering): String {
        val organisasjonsnavn = runBlocking { finnOrganisasjonsnavn(eregClient, plan.yrkesaktivitet) }
        logg.debug("Hentet organisasjonsnavn")
        val navn = hentNavn(speedClient, plan.fnr, UUID.randomUUID().toString()) ?: ""
        logg.debug("Hentet søkernavn")

        val pdfPayload = plan.toPdfPayload(organisasjonsnavn, navn)
        if (erUtvikling) sikkerLogg.info("ferdig annullering-payload: ${objectMapper.writeValueAsString(pdfPayload)}")
        return runBlocking { pdfClient.hentFerdigAnnulleringPdf(pdfPayload) }
    }

    private fun lagJournalpostPayload(plan: PlanlagtAnnullering, pdf: String) {
        val journalpostPayload = JournalpostPayload(
            tittel = "Annullering av vedtak om sykepenger",
            bruker = JournalpostPayload.Bruker(id = plan.fnr),
            dokumenter = listOf(
                JournalpostPayload.Dokument(
                    tittel = "Utbetaling annullert i ny løsning ${plan.norskFom} - ${plan.norskTom}",
                    dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                )
            ),
            eksternReferanseId = plan.id.toString(),
        )

        if (!journalførPdf(plan.id, journalpostPayload))
            return logg.warn("Feil oppstod under journalføring av annullering")

        logg.info("Annullering journalført for hendelseId=${plan.id}")
        planlagtAnnulleringDao.settNotatOpprettet(plan.id)
    }

    private fun journalførPdf(hendelsesId: UUID, journalpostPayload: JournalpostPayload): Boolean {
        return runBlocking { joarkClient.opprettJournalpost(hendelsesId, journalpostPayload) }
    }
}
