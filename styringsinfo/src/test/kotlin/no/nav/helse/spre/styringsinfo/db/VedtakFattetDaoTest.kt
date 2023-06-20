package no.nav.helse.spre.styringsinfo.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.VedtakFattet
import no.nav.helse.spre.styringsinfo.VedtakFattetDao
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VedtakFattetDaoTest : AbstractDatabaseTest() {

    private lateinit var vedtakFattetDao: VedtakFattetDao

    @BeforeAll
    fun setup() {
        vedtakFattetDao = VedtakFattetDao(dataSource)
    }

    @Test
    fun `lagre vedtakFattet`() {

        val json = """
            {
                "@id": "08a92c25-0e59-452f-ba60-83b7515de8e5",
                "fødselsnummer": "12345678910",
                "fom": "2023-06-05",
                "tom": "2023-06-11",
                "vedtakFattetTidspunkt": "2023-06-01T00:00:00.0",
                "hendelser": [
                    "65ca68fa-0f12-40f3-ac34-141fa77c4270",
                    "6977170d-5a99-4e7f-8d5f-93bda94a9ba3",
                    "15aa9c84-a9cc-4787-b82a-d5447aa3fab1"
                ]
            }
        """.trimIndent()

        val vedtakFattet = VedtakFattet(
            fnr = "12345678910",
            fom = LocalDate.parse("2023-06-05"),
            tom = LocalDate.parse("2023-06-11"),
            vedtakFattetTidspunkt = LocalDateTime.parse("2023-06-01T00:00:00.0"),
            hendelseId = UUID.fromString("65ca68fa-0f12-40f3-ac34-141fa77c4270"),
            melding = json
        )
        vedtakFattetDao.lagre(vedtakFattet)

        assertEquals(
            vedtakFattet,
            hent(UUID.fromString("65ca68fa-0f12-40f3-ac34-141fa77c4270"))
        )
    }

    private fun hent(id: UUID) = sessionOf(dataSource).use { session ->
        session.run(
            queryOf(
                """select fnr, fom, tom, vedtak_fattet_tidspunkt, hendelse_id, melding from vedtak_fattet where hendelse_id = :hendelseId""",
                mapOf("hendelseId" to id)
            )
                .map { row ->
                    VedtakFattet(
                        fnr = row.string("fnr"),
                        fom = row.localDate("fom"),
                        tom = row.localDate("tom"),
                        vedtakFattetTidspunkt = row.localDateTime("vedtak_fattet_tidspunkt"),
                        hendelseId = row.uuid("hendelse_id"),
                        melding = row.string("melding")
                    )
                }.asSingle
        )
    }
}