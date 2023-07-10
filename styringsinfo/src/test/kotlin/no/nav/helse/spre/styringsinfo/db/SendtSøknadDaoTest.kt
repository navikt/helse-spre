package no.nav.helse.spre.styringsinfo.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.SendtSøknad
import no.nav.helse.spre.styringsinfo.SendtSøknadDao
import no.nav.helse.spre.styringsinfo.toOsloOffset
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SendtSøknadDaoTest : AbstractDatabaseTest() {

    private lateinit var sendtSøknadDao: SendtSøknadDao

    @BeforeAll
    fun setup() {
        sendtSøknadDao = SendtSøknadDao(dataSource)
    }

    @Test
    fun `lagre sendtSøknad`() {

        val json = """
            {
              "@id": "08a92c25-0e59-452f-ba60-83b7515de8e5",
              "sendtArbeidsgiver": "2023-06-01T10:00:00.0",
              "sendtNav": null,
              "korrigerer": "4c6f931d-63b6-3ff7-b3bc-74d1ad627201",
              "fnr": "12345678910",
              "fom": "2023-06-05",
              "tom": "2023-06-11"
            }
        """.trimIndent()

        val sendtSøknad = SendtSøknad(
            sendt = LocalDateTime.parse("2023-06-01T10:00:00.0"),
            korrigerer = UUID.fromString("4c6f931d-63b6-3ff7-b3bc-74d1ad627201"),
            fnr = "12345678910",
            fom = LocalDate.parse("2023-06-05"),
            tom = LocalDate.parse("2023-06-11"),
            hendelseId = UUID.fromString("08a92c25-0e59-452f-ba60-83b7515de8e5"),
            melding = json
        )
        assertEquals(sendtSøknad.sendt, sendtSøknad.sendt.toOsloOffset().toLocalDateTime())

        sendtSøknadDao.lagre(sendtSøknad)

        println("Timezone JVM: ${Calendar.getInstance().timeZone}")
        println("Timezone PG: ${hentTimezone()}")

        assertEquals(
            sendtSøknad,
            hent(UUID.fromString("08a92c25-0e59-452f-ba60-83b7515de8e5"))
        )
    }

    private fun hent(id: UUID) = sessionOf(dataSource).use { session ->
        session.run(
            queryOf(
                """select sendt, korrigerer, fnr, fom, tom, hendelse_id, melding from sendt_soknad where hendelse_id = :hendelseId""",
                mapOf("hendelseId" to id)
            )
                .map { row ->
                    println("Datostreng: ${row.string("sendt")}")
                    SendtSøknad(
                        sendt = row.offsetDateTime("sendt").toLocalDateTime(),
                        korrigerer = row.uuidOrNull("korrigerer"),
                        fnr = row.string("fnr"),
                        fom = row.localDate("fom"),
                        tom = row.localDate("tom"),
                        hendelseId = row.uuid("hendelse_id"),
                        melding = row.string("melding")
                    )
                }.asSingle
        )
    }

    private fun hentTimezone() = sessionOf(dataSource).use { session ->
        session.run(
            queryOf(
                "show timezone"
            ).map { it.string(1) }.asSingle
        )
    }
}