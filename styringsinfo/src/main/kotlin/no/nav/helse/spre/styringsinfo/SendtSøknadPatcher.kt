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
        do {
            Thread.sleep(loopSleepMillis)
            val søknader = sendtSøknadDao.hentMeldingerMedPatchLevelMindreEnn(patchLevelMindreEnn, antallMeldinger = antallMeldinger)

            if (søknader.isEmpty()) {
                log.info("Avslutter patching siden ingen søknader har patchLevel mindre enn $patchLevelMindreEnn.")
                return
            }
            log.info("Hentet ${søknader.size} soknader med patchLevel mindre enn $patchLevelMindreEnn.")

            søknader.forEach {
                it.patch().also { patchetSoknad -> sendtSøknadDao.oppdaterMelding(patchetSoknad) }
            }
        } while (søknader.isNotEmpty())
    }
}