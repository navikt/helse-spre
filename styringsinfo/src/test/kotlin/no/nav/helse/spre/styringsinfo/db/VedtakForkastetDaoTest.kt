package no.nav.helse.spre.styringsinfo.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.VedtakForkastet
import no.nav.helse.spre.styringsinfo.VedtakForkastetDao
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VedtakForkastetDaoTest : AbstractDatabaseTest() {

    private lateinit var vedtakForkastetDao: VedtakForkastetDao

    @BeforeAll
    fun setup() {
        vedtakForkastetDao = VedtakForkastetDao(dataSource)
    }

    @Test
    fun `lagre vedtakForkastet`() {

        val json = """
            {
                "@id": "08a92c25-0e59-452f-ba60-83b7515de8e5",
                "fødselsnummer": "12345678910",
                "fom": "2023-06-05",
                "tom": "2023-06-11",
                "@opprettet": "2023-06-01T00:00:00.0",
                "hendelser": [
                    "65ca68fa-0f12-40f3-ac34-141fa77c4270",
                    "6977170d-5a99-4e7f-8d5f-93bda94a9ba3",
                    "15aa9c84-a9cc-4787-b82a-d5447aa3fab1"
                ]
            }
        """.trimIndent()

        val vedtakForkastet = VedtakForkastet(
            fnr = "12345678910",
            fom = LocalDate.parse("2023-06-05"),
            tom = LocalDate.parse("2023-06-11"),
            forkastetTidspunkt = LocalDateTime.parse("2023-06-01T00:00:00.0"),
            hendelseId = UUID.fromString("08a92c25-0e59-452f-ba60-83b7515de8e5"),
            hendelser = listOf(
                UUID.fromString("65ca68fa-0f12-40f3-ac34-141fa77c4270"),
                UUID.fromString("6977170d-5a99-4e7f-8d5f-93bda94a9ba3"),
                UUID.fromString("15aa9c84-a9cc-4787-b82a-d5447aa3fab1")
            ),
            melding = json
        )
        vedtakForkastetDao.lagre(vedtakForkastet)

        assertEquals(
            vedtakForkastet,
            hent(UUID.fromString("08a92c25-0e59-452f-ba60-83b7515de8e5"))
        )
    }

    private fun hent(vedtakForkastet: UUID) = sessionOf(dataSource).use { session ->
        session.transaction { tx ->
            tx.run(
                queryOf(
                    """select fnr, fom, tom, forkastet_tidspunkt, hendelse_id, melding from vedtak_forkastet where hendelse_id = :hendelseId""",
                    mapOf("hendelseId" to vedtakForkastet)
                )
                    .map { row ->
                        VedtakForkastet(
                            fnr = row.string("fnr"),
                            fom = row.localDate("fom"),
                            tom = row.localDate("tom"),
                            forkastetTidspunkt = row.localDateTime("forkastet_tidspunkt"),
                            hendelseId = row.uuid("hendelse_id"),
                            hendelser = hentHendelser(vedtakForkastet, tx),
                            melding = row.string("melding")
                        )
                    }.asSingle
            )
        }
    }

    private fun hentHendelser(vedtakForkastet: UUID, tx: TransactionalSession): List<UUID> =
        tx.run(
            queryOf(
                "select dokument_hendelse_id from vedtak_dokument_mapping where vedtak_hendelse_id = :vedtakForkastet",
                mapOf("vedtakForkastet" to vedtakForkastet)
            ).map { row -> row.uuid("dokument_hendelse_id") }.asList
        )
}