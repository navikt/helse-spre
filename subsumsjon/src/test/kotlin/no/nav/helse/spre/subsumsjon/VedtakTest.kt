package no.nav.helse.spre.subsumsjon

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class VedtakTest {

    private lateinit var fattetRiver: VedtakFattetRiver
    private lateinit var forkastetRiver: VedtakForkastetRiver
    private val testRapid = TestRapid()
    private val resultat = mutableListOf<Pair<String, String>>()


    @BeforeEach
    fun setup() {
        fattetRiver = VedtakFattetRiver(testRapid) { key, value -> resultat.add(Pair(key, value)) }
        forkastetRiver = VedtakForkastetRiver(testRapid) { key, value -> resultat.add(Pair(key, value)) }
    }

    @AfterEach
    fun before() {
        resultat.clear()
    }

    @Test
    fun `vedtak_fattet blir publisert`() {
        testRapid.sendTestMessage(testVedtakFattet("9bfdcbc4-854e-489c-9ea4-5ed650c32d05"))
        assertEquals(1, resultat.size)
    }

    @Test
    fun `vedtaksperiode_forkastet blir publisert`() {
        testRapid.sendTestMessage(testVedtaksperiodeForkastet("9bfdcbc4-854e-489c-9ea4-5ed650c32d05"))
        assertEquals(1, resultat.size)
    }

    @Test
    fun `vedtak fattet før 15 februar republiseres ikke`() {
        testRapid.sendTestMessage(testVedtakFattet(UUID.randomUUID().toString(), LocalDate.of(2022, 2, 14).atStartOfDay()))
        assertEquals(0, resultat.size)
    }

    @Test
    fun `vedtak forkastet før 15 februar republiseres ikke`() {
        testRapid.sendTestMessage(testVedtaksperiodeForkastet(UUID.randomUUID().toString(), LocalDate.of(2022, 2, 14).atStartOfDay()))
        assertEquals(0, resultat.size)
    }
}

@Language("JSON")
private fun testVedtakFattet(id: String, opprettet: LocalDateTime = LocalDateTime.parse("2022-02-15T00:00:00.000000000")) = """
    {
      "fom": "2022-02-01",
      "tom": "2022-02-15",
      "hendelser": [
        "34675e2a-7ae1-4c40-9028-4fe300e1f203",
        "14849374-dd58-4f06-b45b-a1096232e695",
        "53123ce5-f520-4f14-a2f8-2ef4d67c60eb"
      ],
      "skjæringstidspunkt": "2022-01-01",
      "inntekt": 21300.0,
      "vedtakFattetTidspunkt": "2022-02-15T12:27:16.214142227",
      "utbetalingId": "fb682440-524a-42f4-b4f6-9fb9fe7fe6bb",
      "@event_name": "vedtak_fattet",
      "@id": "$id",
      "@opprettet": "$opprettet",
      "fødselsnummer": "31056203918",
      "aktørId": "2298954396024",
      "organisasjonsnummer": "947064649",
      "vedtaksperiodeId": "c249e73d-53dc-4237-9f8d-8d7cf58dfb80"
    }
""".trimIndent()

@Language("JSON")
private fun testVedtaksperiodeForkastet(id: String, opprettet: LocalDateTime = LocalDateTime.parse("2022-02-15T00:00:00.000000000")) = """
    {
      "tilstand": "TIL_INFOTRYGD",
      "@event_name": "vedtaksperiode_forkastet",
      "@id": "$id",
      "@opprettet": "$opprettet",
      "fødselsnummer": "16029121021",
      "organisasjonsnummer": "947064649",
      "vedtaksperiodeId": "049aa630-c361-45fb-9aae-f86db7978c88"
    }
""".trimIndent()
