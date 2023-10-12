package no.nav.helse.spre.styringsinfo.db

import no.nav.helse.spre.styringsinfo.PatchOptions
import no.nav.helse.spre.styringsinfo.domain.VedtakFattet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class VedtakFattetPatcherTest {

    private class VedtakFattetDaoMock : VedtakFattetDaoInterface {
        val oppdaterteVedtakFattet = mutableListOf<VedtakFattet>()
        var vedtakFattetErHentet = false

        override fun lagre(vedtakFattet: VedtakFattet) {
            TODO("Not yet implemented.")
        }

        override fun oppdaterMelding(vedtakFattet: VedtakFattet): Int {
            oppdaterteVedtakFattet.add(vedtakFattet)
            return 1
        }

        override fun hentMeldingerMedPatchLevelMindreEnn(patchLevel: Int, antallMeldinger: Int): List<VedtakFattet> {

            // TODO: Vurder å implementer patching og støtte for limit.
//            val ikkePatchet = soknader.map { it.patchLevel < patchLevel }.subList(0, limit)

            if (vedtakFattetErHentet) {
                return emptyList()
            }

            val vedtakFattet = VedtakFattet(
                vedtakFattetTidspunkt = LocalDateTime.parse("2023-10-01T01:00:00"),
                fom = LocalDate.parse("2023-10-01"),
                tom = LocalDate.parse("2023-10-02"),
                hendelseId = UUID.fromString("08a92c25-0e59-452f-ba60-83b7515de8e5"),
                melding = """
                {
                  "@id": "08a92c25-0e59-452f-ba60-83b7515de8e5",
                  "vedtakFattetTidspunkt": "2023-06-01T10:00:00.0",
                  "fødselsnummer": "12345678910",
                  "fom": "2023-10-01",
                  "tom": "2023-10-02",
                  "hendelser": []
                }
                """,
                patchLevel = 0,
                hendelser = emptyList()
            )
            vedtakFattetErHentet = true
            return listOf(vedtakFattet)
        }
    }

    @Test
    fun `patch VedtakFattet`() {
        val vedtakFattetDaoMock = VedtakFattetDaoMock()
        val vedtakFattetPatcher = VedtakFattetPatcher(vedtakFattetDaoMock)

        vedtakFattetPatcher.patchVedtakFattet(
            PatchOptions(
                patchLevelMindreEnn = 1,
                initialSleepMillis = 10,
                loopSleepMillis = 10,
                antallMeldinger = 1
            )
        )

        vedtakFattetDaoMock.oppdaterteVedtakFattet.forEach {
            assertEquals(1, it.patchLevel)
            assertFalse(it.melding.contains("fnr"))
        }
    }
}