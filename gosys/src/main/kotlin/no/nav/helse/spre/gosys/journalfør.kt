package no.nav.helse.spre.gosys

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import java.util.*
import kotliquery.TransactionalSession
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import no.nav.helse.spre.gosys.vedtakFattet.MeldingOmVedtak
import no.nav.helse.spre.gosys.vedtakFattet.MeldingOmVedtakRepository
import no.nav.helse.spre.gosys.vedtakFattet.pdf.PdfJournalfører
import no.nav.helse.spre.gosys.vedtakFattet.pdf.PdfProduserer

context(session: TransactionalSession)
internal fun journalfør(
    meldingId: UUID,
    utbetaling: Utbetaling,
    meldingOmVedtak: MeldingOmVedtak,
    pdfProduserer: PdfProduserer,
    pdfJournalfører: PdfJournalfører,
    duplikatsjekkDao: DuplikatsjekkDao,
    meldingOmVedtakRepository: MeldingOmVedtakRepository,
) {
    check(utbetaling.type in setOf(Utbetaling.Utbetalingtype.UTBETALING, Utbetaling.Utbetalingtype.REVURDERING)) {
        "Vedtaket gjelder en utbetaling av type ${utbetaling.type}. Forventer kun Utbetaling/Revurdering"
    }
    val meldingOmVedtakJson = objectMapper.readTree(meldingOmVedtak.json)
    val (søknadsperiodeFom, søknadsperiodeTom) = utbetaling.søknadsperiode(meldingOmVedtakJson["fom"].asLocalDate() to meldingOmVedtakJson["tom"].asLocalDate())

    val pdfBytes = pdfProduserer.lagPdf(
        meldingOmVedtakJson = meldingOmVedtakJson,
        utbetaling = utbetaling,
        søknadsperiodeFom = søknadsperiodeFom,
        søknadsperiodeTom = søknadsperiodeTom
    )

    pdfJournalfører.journalførPdf(
        pdfBytes = pdfBytes,
        utbetaling = utbetaling,
        søknadsperiodeFom = søknadsperiodeFom,
        søknadsperiodeTom = søknadsperiodeTom
    )

    meldingOmVedtak.journalfør()
    meldingOmVedtakRepository.lagre(meldingOmVedtak)

    duplikatsjekkDao.insertTilDuplikatsjekk(meldingId)
}
