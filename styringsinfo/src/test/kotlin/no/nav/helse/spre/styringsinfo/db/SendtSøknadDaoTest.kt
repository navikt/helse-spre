package no.nav.helse.spre.styringsinfo.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.SendtSøknad
import no.nav.helse.spre.styringsinfo.SendtSøknadDao
import no.nav.helse.spre.styringsinfo.toOsloOffset
import no.nav.helse.spre.styringsinfo.toOsloTid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SendtSøknadDaoTest : AbstractDatabaseTest() {

    private lateinit var sendtSøknadDao: SendtSøknadDao

    @BeforeAll
    fun setup() {
        sendtSøknadDao = SendtSøknadDao(dataSource)
    }

    @Test
    fun `lagre sendtSøknad`() {
        val hendelseId = UUID.randomUUID().toString()
        val lagretSøknad = opprettOgLagreSendtSøknad(hendelseId)

        assertEquals(lagretSøknad, hentSøknad(UUID.fromString(hendelseId)))
    }

    @Test
    fun `oppdater sendtSøknad med ny melding`() {
        val hendelseId = UUID.randomUUID().toString()
        val sendtSøknad = opprettOgLagreSendtSøknad(hendelseId)

        sendtSøknad
            .patch()
            .also {
                sendtSøknadDao.patchMelding(null, it)
            }

        val patchetSøknad = hentSøknad(UUID.fromString(hendelseId))

        assertTrue(patchetSøknad!!.patchLevel == 1)

        val json = """
            {
              "@id": "$hendelseId",
              "sendtArbeidsgiver": "2023-06-01T10:00:00.0",
              "sendtNav": null,
              "korrigerer": "4c6f931d-63b6-3ff7-b3bc-74d1ad627201",
              "fom": "2023-06-05",
              "tom": "2023-06-11"
            }
            """

        JSONAssert.assertEquals(json, patchetSøknad.melding, JSONCompareMode.STRICT)
    }

    private fun opprettOgLagreSendtSøknad(hendelseId: String): SendtSøknad {
        val json =
            """
            {
              "@id": "$hendelseId",
              "sendtArbeidsgiver": "2023-06-01T10:00:00.0",
              "sendtNav": null,
              "korrigerer": "4c6f931d-63b6-3ff7-b3bc-74d1ad627201",
              "fnr": "12345678910",
              "fom": "2023-06-05",
              "tom": "2023-06-11"
            }
            """
        val sendtSøknad = SendtSøknad(
            sendt = LocalDateTime.parse("2023-06-01T10:00:00.0"),
            korrigerer = UUID.fromString("4c6f931d-63b6-3ff7-b3bc-74d1ad627201"),
            fom = LocalDate.parse("2023-06-05"),
            tom = LocalDate.parse("2023-06-11"),
            hendelseId = UUID.fromString(hendelseId),
            melding = json
        )
        assertEquals(sendtSøknad.sendt, sendtSøknad.sendt.toOsloOffset().toLocalDateTime())

        sendtSøknadDao.lagre(sendtSøknad)
        return sendtSøknad
    }

    private fun hentSøknad(hendelseId: UUID) = sessionOf(dataSource).use { session ->
        session.run(
            queryOf(
                """
               SELECT sendt, korrigerer, fom, tom, hendelse_id, melding, patch_level 
               FROM sendt_soknad
               WHERE hendelse_id = :hendelseId
               """,
                mapOf("hendelseId" to hendelseId)
            ).map { row ->
                SendtSøknad(
                    sendt = row.zonedDateTime("sendt").toOsloTid(),
                    korrigerer = row.uuidOrNull("korrigerer"),
                    fom = row.localDate("fom"),
                    tom = row.localDate("tom"),
                    hendelseId = row.uuid("hendelse_id"),
                    melding = row.string("melding"),
                    patchLevel = row.intOrNull("patch_level")
                )
            }.asSingle
        )
    }
}