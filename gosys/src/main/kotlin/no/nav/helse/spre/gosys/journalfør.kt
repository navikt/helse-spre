package no.nav.helse.spre.gosys

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import no.nav.helse.spre.gosys.vedtakFattet.pdf.PdfJournalfører
import no.nav.helse.spre.gosys.vedtakFattet.pdf.PdfProduserer
import java.util.UUID

internal fun journalfør(
    meldingId: UUID,
    utbetaling: Utbetaling,
    vedtakFattetRad: VedtakFattetDao.VedtakFattetRad,
    pdfProduserer: PdfProduserer,
    pdfJournalfører: PdfJournalfører,
    duplikatsjekkDao: DuplikatsjekkDao,
) {
    check(utbetaling.type in setOf(Utbetaling.Utbetalingtype.UTBETALING, Utbetaling.Utbetalingtype.REVURDERING)) {
        "Vedtaket gjelder en utbetaling av type ${utbetaling.type}. Forventer kun Utbetaling/Revurdering"
    }
    val meldingOmVedtakJson = objectMapper.readTree(vedtakFattetRad.data)
    val (søknadsperiodeFom, søknadsperiodeTom) = utbetaling.søknadsperiode(meldingOmVedtakJson["fom"].asLocalDate() to meldingOmVedtakJson["tom"].asLocalDate())

    val pdfBytes = pdfProduserer.lagPdf(
        meldingOmVedtakJson = meldingOmVedtakJson,
        utbetaling = utbetaling,
        søknadsperiodeFom = søknadsperiodeFom,
        søknadsperiodeTom = søknadsperiodeTom
    )

    pdfJournalfører.journalførPdf(
        pdfBytes = pdfBytes,
        vedtakFattetRad = vedtakFattetRad,
        utbetaling = utbetaling,
        søknadsperiodeFom = søknadsperiodeFom,
        søknadsperiodeTom = søknadsperiodeTom
    )

    duplikatsjekkDao.insertTilDuplikatsjekk(meldingId)
}
