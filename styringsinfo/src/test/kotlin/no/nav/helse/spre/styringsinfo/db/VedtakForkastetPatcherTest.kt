package no.nav.helse.spre.styringsinfo.db

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.PatchOptions
import no.nav.helse.spre.styringsinfo.domain.VedtakForkastet
import no.nav.helse.spre.styringsinfo.objectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class VedtakForkastetPatcherTest {

    private class VedtakForkastetDaoMock : VedtakForkastetDaoInterface {
        val oppdaterteVedtakForkastet = mutableListOf<VedtakForkastet>()
        var vedtakForkastetErHentet = false

        override fun lagre(vedtakForkastet: VedtakForkastet) {
            TODO("Not yet implemented.")
        }

        override fun oppdaterMelding(vedtakForkastet: VedtakForkastet): Int {
            oppdaterteVedtakForkastet.add(vedtakForkastet)
            return 1
        }

        override fun hentMeldingerMedPatchLevelMindreEnn(patchLevel: Int, antallMeldinger: Int): List<VedtakForkastet> {
            if (vedtakForkastetErHentet) {
                return emptyList()
            }

            val vedtakForkastet = VedtakForkastet(
                forkastetTidspunkt = LocalDateTime.parse("2023-10-01T01:00:00"),
                fom = LocalDate.parse("2023-10-01"),
                tom = LocalDate.parse("2023-10-02"),
                hendelseId = UUID.fromString("08a92c25-0e59-452f-ba60-83b7515de8e5"),
                melding = """
                {
                  "@id": "08a92c25-0e59-452f-ba60-83b7515de8e5",
                  "forkastetTidspunkt": "2023-06-01T10:00:00.0",
                  "fødselsnummer": "12345678910",
                  "fom": "2023-10-01",
                  "tom": "2023-10-02",
                  "hendelser": []
                }
                """,
                patchLevel = 0,
                hendelser = emptyList()
            )
            vedtakForkastetErHentet = true
            return listOf(vedtakForkastet)
        }
    }

    @Test
    fun `patch VedtakForkastet`() {
        val vedtakForkastetDaoMock = VedtakForkastetDaoMock()
        val vedtakForkastetPatcher = VedtakForkastetPatcher(vedtakForkastetDaoMock)

        vedtakForkastetPatcher.patchVedtakForkastet(
            PatchOptions(
                patchLevelMindreEnn = 1,
                initialSleepMillis = 10,
                loopSleepMillis = 10,
                antallMeldinger = 1
            )
        )

        vedtakForkastetDaoMock.oppdaterteVedtakForkastet.forEach {
            assertEquals(2, it.patchLevel)
            val objectNode = objectMapper.readTree(it.melding) as ObjectNode
            Assertions.assertTrue(objectNode.at("/fødselsnummer").isMissingNode)
            Assertions.assertTrue(objectNode.at("/organisasjonsnummer").isMissingNode)
        }
    }
}