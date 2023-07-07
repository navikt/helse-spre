package no.nav.helse.spre.styringsinfo.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.toOsloOffset
import org.intellij.lang.annotations.Language
import org.junit.Ignore
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime
import java.util.Calendar
import java.util.TimeZone

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimezoneTest : AbstractDatabaseTest() {

    lateinit var timeZone: String

    @BeforeAll
    fun hentTimeZone() {
        timeZone = hentTimezone()!!
    }

    @AfterAll
    fun setSetTimeZone() {
        // Må rydde opp i tilfelle timezone har blitt endret
        executeQuery("SET TIMEZONE = '$timeZone'")
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))
    }

    @Test
    @Ignore
    fun `Tester lagring og spørring mot timestamptz-kolonne`() {
        executeQuery("DROP TABLE IF EXISTS timezonetest")
        executeQuery("CREATE TABLE timezonetest (tidspunkt TIMESTAMPTZ)")

        executeQuery("SET TIMEZONE = 'Europe/Oslo'")
        assertEquals("Europe/Oslo", hentTimezone())

        // Oppretter tidspunkt som vil lagres med Oslo-offset.
        val tidspunkt = LocalDateTime.parse("2023-06-01T10:00:00.0")
        // Vil lagres med Oslo-offset.
        lagre(tidspunkt)

        // Postgres forventes å vise en streng-representasjon av tidspunktet med Oslo-offset (selv om tidspunktet alltid er lagret i UTC i databasen).
        assertEquals("2023-06-01 10:00:00+02", hentTidspunkt<String>())
        // LocalDateTime som hentes opp forventes også å representere testens "baseline-Oslo-tidspunkt".
        assertEquals(tidspunkt, hentTidspunkt<LocalDateTime>())

        // Endrer timezone i postgres
        executeQuery("SET TIMEZONE = 'UTC'")
        assertEquals("UTC", hentTimezone())

        // Nå forventes det at postgres viser en streng-representasjon som reflekterer ny timezone (UTC). Den underliggende lagrede verdien er uforandret.
        assertEquals("2023-06-01 08:00:00+00", hentTidspunkt<String>())
        // LocalDateTime som hentes opp forventes fremdeles å representere Oslo-tid ettersom denne baserer seg på timezone satt på JVM, uavhengig av databasen.
        assertEquals(tidspunkt, hentTidspunkt<LocalDateTime>())

        // Endrer timezone på JVM. NB: Kan potensielt gi krøll dersom tester kjøres i parallell.
        assertEquals(TimeZone.getTimeZone("Europe/Oslo"), Calendar.getInstance().timeZone)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
        assertEquals(TimeZone.getTimeZone("Europe/Helsinki"), Calendar.getInstance().timeZone)

        // Forventer ingen endring i oppførsel til postgres, skal fremdeles hente ut ihht sin konfigurerte timezone (UTC).
        assertEquals("2023-06-01 08:00:00+00", hentTidspunkt<String>())
        // LocalDateTime som hentes opp forventes derimot å representere Helsinki-tid.
        assertEquals(tidspunkt.plusHours(1), hentTidspunkt<LocalDateTime>())
    }
}

private fun executeQuery(@Language("PostgreSQL") sql: String) = sessionOf(dataSource).use { session ->
    session.run(
        queryOf(sql).asExecute
    )
}

private fun hentTimezone() = sessionOf(dataSource).use { session ->
    session.run(
        queryOf(
            "show timezone"
        ).map { it.string(1) }.asSingle
    )
}

inline fun <reified T> hentTidspunkt(): T =
    sessionOf(dataSource).use { session ->
        session.single<T>(
            queryOf("SELECT tidspunkt FROM timezonetest")
        ) {
            when (T::class.java) {
                String::class.java -> return@single it.string("tidspunkt") as T
                LocalDateTime::class.java -> return@single it.localDateTime("tidspunkt") as T
                else -> throw Exception()
            }
        }!!
    }

private fun lagre(tidspunkt: LocalDateTime) = sessionOf(dataSource).use { session ->
    session.run(
        queryOf(
            "INSERT INTO timezonetest (tidspunkt) VALUES (:tidspunkt)",
            mapOf("tidspunkt" to tidspunkt.toOsloOffset())
        ).asUpdate
    )
}