package no.nav.helse.spre.styringsinfo.teamsak

import kotliquery.queryOf
import kotliquery.sessionOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

internal class TidsstempelTest: AbstractTeamSakTest() {

    @Test
    fun `presisjon på tidsstempler truncates ned til 6 desimaler i databasen`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val tidspunkt = OffsetDateTime.parse("2024-02-13T15:29:54.123123123+01:00")
        val (behandlingId, behandlingOpprettet, _) = hendelsefabrikk.behandlingOpprettet(innsendt = tidspunkt, registrert = tidspunkt, opprettet = tidspunkt)
        behandlingOpprettet.håndter(behandlingId)

        assertEquals("2024-02-13T15:29:54.123123+01:00", funksjonellTid)
        assertEquals("2024-02-13T15:29:54.123123+01:00", mottattTid)
        assertEquals("2024-02-13T15:29:54.123123+01:00", registrertTid)
    }

    @Test
    fun `presisjon på tidsstempler justeres opp til 6 desimaler i databasen`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val innsendt = OffsetDateTime.parse("2024-02-13T15:29+01:00")
        val registrert = OffsetDateTime.parse("2024-02-20T15:29+01:00")
        val opprettet = OffsetDateTime.parse("2024-02-20T15:29:54.123+01:00")
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet(innsendt = innsendt, registrert = registrert, opprettet = opprettet)
        behandlingOpprettet.håndter(behandlingId)

        assertEquals("2024-02-13T15:29:00.000000+01:00", mottattTid)
        assertEquals("2024-02-20T15:29:00.000000+01:00", registrertTid)
    }

    private val mottattTid get() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select data->>'mottattTid' from behandlingshendelse LIMIT 1").map { row ->
            row.string(1)
        }.asSingle)
    }

    private val registrertTid get() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select data->>'registrertTid' from behandlingshendelse LIMIT 1").map { row ->
            row.string(1)
        }.asSingle)
    }

    private val funksjonellTid get() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select funksjonellTid from behandlingshendelse LIMIT 1").map { row ->
            row.offsetDateTime(1)
        }.asSingle).toString()
    }
}