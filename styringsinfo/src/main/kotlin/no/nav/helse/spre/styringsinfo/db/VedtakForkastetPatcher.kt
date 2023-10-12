package no.nav.helse.spre.styringsinfo.db

import no.nav.helse.spre.styringsinfo.PatchOptions
import no.nav.helse.spre.styringsinfo.log

class VedtakForkastetPatcher(
    private val vedtakForkastetDao: VedtakForkastetDaoInterface
) {

    fun patchVedtakForkastet(patchOptions: PatchOptions) {
        val (patchLevelMindreEnn, initialSleepMillis, loopSleepMillis, antallMeldinger) = patchOptions

        Thread.sleep(initialSleepMillis)
        var antallPatchet = 0
        do {
            Thread.sleep(loopSleepMillis)
            val vedtakForkastet = vedtakForkastetDao.hentMeldingerMedPatchLevelMindreEnn(
                patchLevel = patchLevelMindreEnn,
                antallMeldinger = antallMeldinger
            )

            if (vedtakForkastet.isEmpty()) {
                log.info("Avslutter patching siden ingen flere vedtak forkastet har patchLevel mindre enn $patchLevelMindreEnn.")
                return
            }
            log.info("Hentet ${vedtakForkastet.size} vedtak forkastet med patchLevel mindre enn $patchLevelMindreEnn.")

            vedtakForkastet.sumOf { vedtakForkastetDao.oppdaterMelding(it.patch()) }.also {
                antallPatchet += it
                log.info("Patchet $it vedtak forkastet. Totalt patchet: $antallPatchet")
            }

        } while (vedtakForkastet.isNotEmpty())
    }
}