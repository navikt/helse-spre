package no.nav.helse.spre.styringsinfo.datafortelling.db

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.PatchOptions
import no.nav.helse.spre.styringsinfo.datafortelling.db.VedtakFattetDaoInterface
import no.nav.helse.spre.styringsinfo.datafortelling.db.VedtakFattetPatcher
import no.nav.helse.spre.styringsinfo.datafortelling.domain.VedtakFattet
import no.nav.helse.spre.styringsinfo.objectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class VedtakFattetPatcherTest {

    private class VedtakFattetDaoMock : VedtakFattetDaoInterface {
        val oppdaterteVedtakFattet = mutableListOf<VedtakFattet>()
        var vedtakFattetErHentet = false

        override fun lagre(vedtakFattet: VedtakFattet) {}

        override fun oppdaterMelding(vedtakFattet: VedtakFattet): Int {
            oppdaterteVedtakFattet.add(vedtakFattet)
            return 1
        }

        override fun hentMeldingerMedPatchLevelMindreEnn(patchLevel: Int, antallMeldinger: Int): List<VedtakFattet> {
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
                  "organisasjonsnummer": "987654321",
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
            assertEquals(2, it.patchLevel)
            val objectNode = objectMapper.readTree(it.melding) as ObjectNode
            Assertions.assertTrue(objectNode.at("/fødselsnummer").isMissingNode)
            Assertions.assertTrue(objectNode.at("/organisasjonsnummer").isMissingNode)
        }
    }
}