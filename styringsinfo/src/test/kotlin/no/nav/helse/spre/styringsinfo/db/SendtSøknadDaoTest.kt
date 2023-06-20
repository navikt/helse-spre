package no.nav.helse.spre.styringsinfo.db

import no.nav.helse.spre.styringsinfo.SendtSøknad
import no.nav.helse.spre.styringsinfo.SendtSøknadDao
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
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
              "sendtArbeidsgiver": "2023-06-01T00:00:00.0",
              "sendtNav": null,
              "korrigerer": "4c6f931d-63b6-3ff7-b3bc-74d1ad627201",
              "fnr": "12345678910",
              "fom": "2023-06-05",
              "tom": "2023-06-11"
            }
        """.trimIndent()

        val sendtSøknad = SendtSøknad(
            sendt = LocalDateTime.parse("2023-06-01T00:00:00.0"),
            korrigerer = UUID.randomUUID(),
            fnr = "12345678910",
            fom = LocalDate.parse("2023-06-05"),
            tom = LocalDate.parse("2023-06-11"),
            hendelseId = UUID.randomUUID(),
            melding = json
        )
        sendtSøknadDao.lagre(sendtSøknad)
    }
}