package no.nav.helse.spre.styringsinfo

class SendtSøknadPatcher(
    private val sendtSøknadDao: SendtSøknadDaoInterface
) {

    fun patchSendtSøknad(
        patchLevelMindreEnn: Int,
        initialSleepMillis: Long,
        loopSleepMillis: Long,
        antallMeldinger: Int
    ) {
        Thread.sleep(initialSleepMillis)
        var antallPatchet = 0
        do {
            Thread.sleep(loopSleepMillis)
            val søknader = sendtSøknadDao.hentMeldingerMedPatchLevelMindreEnn(
                patchLevel = patchLevelMindreEnn,
                antallMeldinger = antallMeldinger
            )

            if (søknader.isEmpty()) {
                log.info("Avslutter patching siden ingen flere søknader har patchLevel mindre enn $patchLevelMindreEnn.")
                return
            }
            log.info("Hentet ${søknader.size} søknader med patchLevel mindre enn $patchLevelMindreEnn.")

            søknader.sumOf { sendtSøknadDao.oppdaterMelding(it.patch()) }.also {
                antallPatchet += it
                log.info("Patchet $it søknader. Totalt patchet: $antallPatchet")
            }

        } while (søknader.isNotEmpty())
    }
}