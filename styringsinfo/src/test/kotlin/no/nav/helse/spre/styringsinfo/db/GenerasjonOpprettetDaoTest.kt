package no.nav.helse.spre.styringsinfo.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.domain.GenerasjonOpprettet
import no.nav.helse.spre.styringsinfo.domain.Kilde
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerasjonOpprettetDaoTest : AbstractDatabaseTest() {

    private lateinit var generasjonOpprettetDao: GenerasjonOpprettetDao

    @BeforeAll
    fun setup() {
        generasjonOpprettetDao = GenerasjonOpprettetDao(dataSource)
    }

    @Test
    fun `lagre generasjonOpprettet`() {
        val generasjonId = UUID.randomUUID()
        val generasjonOpprettet = GenerasjonOpprettet(
            fødselsnummer = "12345678910",
            aktørId = "123",
            organisasjonsnummer = "123456789",
            vedtaksperiodeId = UUID.randomUUID(),
            generasjonId = generasjonId,
            type = "FØRSTEGANGSBEHANDLING",
            kilde = Kilde(
                meldingsreferanseId = UUID.randomUUID(),
                innsendt = LocalDateTime.now(),
                registert = LocalDateTime.now(),
                avsender = "SYKEMELDT"
            )
        )

        generasjonOpprettetDao.lagre(generasjonOpprettet)

        assertEquals(
            generasjonOpprettet,
            hent(generasjonId)
        )
    }

    private fun hent(generasjonId: UUID) = sessionOf(dataSource).use { session ->
        session.transaction { tx ->
            tx.run(
                queryOf(
                    """select fodselsnummer, aktorId, organisasjonsnummer, vedtaksperiodeId, generasjonsId, type, meldingsreferanseId, innsendt, registrert, avsender from generasjon_opprettet where generasjonId = :generasjonId""",
                    mapOf("generasjonId" to generasjonId)
                )
                    .map { row ->
                        GenerasjonOpprettet(
                            fødselsnummer = row.string("fodselsnummer"),
                            aktørId = row.string("aktorId"),
                            organisasjonsnummer = row.string("organisasjonsnummer"),
                            vedtaksperiodeId = row.uuid("vedtaksperiodeId"),
                            generasjonId = row.uuid("generasjonId"),
                            type = row.string("type"),
                            kilde = Kilde(
                                meldingsreferanseId = row.uuid("meldingsreferanseId"),
                                innsendt = row.localDateTime("innsendt"),
                                registert = row.localDateTime("registert"),
                                avsender = row.string("avsender")
                            )
                        )
                    }.asSingle
            )
        }
    }
}