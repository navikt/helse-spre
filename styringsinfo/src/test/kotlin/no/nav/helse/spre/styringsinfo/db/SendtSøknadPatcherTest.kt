package no.nav.helse.spre.styringsinfo.db

import no.nav.helse.spre.styringsinfo.PatchOptions
import no.nav.helse.spre.styringsinfo.domain.SendtSøknad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class SendtSøknadPatcherTest {

    private class SendtSøknadDaoMock : SendtSøknadDaoInterface {
        val oppdaterteSøknader = mutableListOf<SendtSøknad>()
        var soknaderErHentet = false

        override fun lagre(sendtSøknad: SendtSøknad) {
            TODO("Not yet implemented.")
        }

        override fun oppdaterMelding(sendtSøknad: SendtSøknad): Int {
            oppdaterteSøknader.add(sendtSøknad)
            return 1
        }

        override fun hentMeldingerMedPatchLevelMindreEnn(patchLevel: Int, antallMeldinger: Int): List<SendtSøknad> {

            // TODO: Vurder å implementer patching og støtte for limit.
//            val ikkePatchet = soknader.map { it.patchLevel < patchLevel }.subList(0, limit)

            if (soknaderErHentet) {
                return emptyList()
            }

            val sendtSoknad = SendtSøknad(
                sendt = LocalDateTime.parse("2023-10-01T01:00:00"),
                korrigerer = null,
                fom = LocalDate.parse("2023-10-01"),
                tom = LocalDate.parse("2023-10-02"),
                hendelseId = UUID.fromString("08a92c25-0e59-452f-ba60-83b7515de8e5"),
                melding = """
                {
                  "@id": "08a92c25-0e59-452f-ba60-83b7515de8e5",
                  "sendtArbeidsgiver": "2023-06-01T10:00:00.0",
                  "sendtNav": null,
                  "fnr": "12345678910",
                  "fom": "2023-10-01",
                  "tom": "2023-10-02"
                }
                """,
                patchLevel = 0
            )
            soknaderErHentet = true
            return listOf(sendtSoknad)
        }
    }

    @Test
    fun `patch SendtSøknad`() {
        val sendtSøknadDaoMock = SendtSøknadDaoMock()
        val sendtSøknadPatcher = SendtSøknadPatcher(sendtSøknadDaoMock)

        sendtSøknadPatcher.patchSendtSøknad(
            PatchOptions(
                patchLevelMindreEnn = 1,
                initialSleepMillis = 10,
                loopSleepMillis = 10,
                antallMeldinger = 1
            )
        )

        sendtSøknadDaoMock.oppdaterteSøknader.forEach {
            assertEquals(1, it.patchLevel)
            assertFalse(it.melding.contains("fnr"))
        }
    }
}