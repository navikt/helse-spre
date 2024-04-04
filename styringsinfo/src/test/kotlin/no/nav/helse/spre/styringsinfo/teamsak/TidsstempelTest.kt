package no.nav.helse.spre.styringsinfo.teamsak

import kotliquery.queryOf
import kotliquery.sessionOf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

internal class TidsstempelTest: AbstractTeamSakTest() {

    @Test
    fun `presisjon på tidsstempler truncates ned til 6 desimaler i databasen`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val tidspunkt = OffsetDateTime.parse("2024-02-13T15:29:54.123123123+01:00")
        val (behandlingId, behandlingOpprettet, _) = hendelsefabrikk.behandlingOpprettet(innsendt = tidspunkt, registrert = tidspunkt, opprettet = tidspunkt)
        behandlingOpprettet.håndter(behandlingId)

        assertFormat(funksjonellTid)
        assertFormat(mottattTid)
        assertFormat(registrertTid)
    }

    @Test
    fun `presisjon på tidsstempler justeres opp til 6 desimaler i databasen`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val innsendt = OffsetDateTime.parse("2024-02-13T15:29+01:00")
        val registrert = OffsetDateTime.parse("2024-02-20T15:29+01:00")
        val opprettet = OffsetDateTime.parse("2024-02-20T15:29:54.123123+01:00")
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet(innsendt = innsendt, registrert = registrert, opprettet = opprettet)
        behandlingOpprettet.håndter(behandlingId)

        assertFormat(funksjonellTid)
        assertFormat(mottattTid)
        assertFormat(registrertTid)
    }

    @Test
    fun `input på forskjellige offsets`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val innsendt = OffsetDateTime.parse("2024-02-13T15:29:00.123123+00:00")
        val registrert = OffsetDateTime.parse("2024-02-13T16:29:00.123123+01:00")
        val opprettet = OffsetDateTime.parse("2024-02-13T20:29:00.123123+05")
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet(innsendt = innsendt, registrert = registrert, opprettet = opprettet)
        behandlingOpprettet.håndter(behandlingId)

        assertFormat(funksjonellTid)
        assertFormat(mottattTid)
        assertFormat(registrertTid)
    }

    @Test
    fun `ugyldige format`() {
        setOf(
            ".123123123+01:00",
            ".123123123+01",
            ".123123123",
            ".12312+01:00",
            ".12312+01",
            ".12312Z",
            ".123123",
            ".123123-01",
            ".123123-01:00"
        ).forEach {
            assertFalse(it.gyldigFormat) { "$it er et gyldig format" }
        }
    }

    @Test
    fun `OffsetDateTime frem og tilbake høyre og venstre`() {
        val tidspunktUtenOffset = "2024-03-20T10:59:50.141422"
        val tidspunktMedOsloOffset = "2024-03-20T10:59:50.141422+01:00"
        val tidspunktMedUtcOffset = "2024-03-20T10:59:50.141422+00:00"
        assertThrows<DateTimeParseException> { OffsetDateTime.parse(tidspunktUtenOffset) }

        assertEquals(OffsetDateTime.parse(tidspunktMedOsloOffset), tidspunktUtenOffset.offsetDateTimeOslo)
        assertEquals(OffsetDateTime.parse(tidspunktMedUtcOffset), tidspunktUtenOffset.offsetDateTime(ZoneId.of("UTC")))
    }

    private val mottattTid get() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select data->>'mottattTid' from behandlingshendelse LIMIT 1").map { row ->
            row.string(1)
        }.asSingle)!!
    }

    private val registrertTid get() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select data->>'registrertTid' from behandlingshendelse LIMIT 1").map { row ->
            row.string(1)
        }.asSingle)!!
    }

    private val funksjonellTid get() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select funksjonellTid from behandlingshendelse LIMIT 1").map { row ->
            row.string(1)
        }.asSingle)!!
    }

    private companion object {
        private val format = "\\d{6}((\\+\\d{2}(:\\d{2})?)|Z)".toRegex()
        private val String.gyldigFormat get() = substringAfter(".").matches(format)
        private fun assertFormat(tidspunkt: String) = assertTrue(tidspunkt.gyldigFormat) { "Ugyldig format $tidspunkt"}
    }
}