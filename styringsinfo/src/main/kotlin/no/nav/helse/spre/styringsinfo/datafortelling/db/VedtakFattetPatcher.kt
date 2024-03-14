package no.nav.helse.spre.styringsinfo.datafortelling.db

import no.nav.helse.spre.styringsinfo.PatchOptions
import no.nav.helse.spre.styringsinfo.log

class VedtakFattetPatcher(
    private val vedtakFattetDao: VedtakFattetDaoInterface
) {

    fun patchVedtakFattet(patchOptions: PatchOptions) {
        val (patchLevelMindreEnn, initialSleepMillis, loopSleepMillis, antallMeldinger) = patchOptions

        Thread.sleep(initialSleepMillis)
        var antallPatchet = 0
        do {
            Thread.sleep(loopSleepMillis)
            val vedtakFattet = vedtakFattetDao.hentMeldingerMedPatchLevelMindreEnn(
                patchLevel = patchLevelMindreEnn,
                antallMeldinger = antallMeldinger
            )

            if (vedtakFattet.isEmpty()) {
                log.info("Avslutter patching siden ingen flere vedtak fattet har patchLevel mindre enn $patchLevelMindreEnn.")
                return
            }
            log.info("Hentet ${vedtakFattet.size} vedtak fattet med patchLevel mindre enn $patchLevelMindreEnn.")

            vedtakFattet.sumOf { vedtakFattetDao.oppdaterMelding(it.patch()) }.also {
                antallPatchet += it
                log.info("Patchet $it vedtak fattet. Totalt patchet: $antallPatchet")
            }

        } while (vedtakFattet.isNotEmpty())
    }
}