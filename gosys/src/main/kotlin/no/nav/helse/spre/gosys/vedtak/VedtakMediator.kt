package no.nav.helse.spre.gosys.vedtak

import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.*
import no.nav.helse.spre.gosys.log

class VedtakMediator(
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient,
    private val duplikatsjekkDao: DuplikatsjekkDao
) {
    internal fun opprettVedtak(vedtakMessage: VedtakMessage) {
        duplikatsjekkDao.sjekkDuplikat(vedtakMessage.hendelseId) {
            runBlocking {
                val pdf = pdfClient.hentVedtakPdf(vedtakMessage.toVedtakPdfPayload())
                val journalpostPayload = JournalpostPayload(
                    tittel = "Vedtak om sykepenger",
                    bruker = JournalpostPayload.Bruker(id = vedtakMessage.fødselsnummer),
                    dokumenter = listOf(
                        JournalpostPayload.Dokument(
                            tittel = "Sykepenger behandlet i ny løsning, ${vedtakMessage.norskFom} - ${vedtakMessage.norskTom}",
                            dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                        )
                    )
                )
                joarkClient.opprettJournalpost(vedtakMessage.hendelseId, journalpostPayload).let { success ->
                    if (success) log.info("Vedtak journalført for aktør: ${vedtakMessage.aktørId}")
                    else log.warn("Feil oppstod under journalføring av vedtak")
                }
            }
        }
    }
}
