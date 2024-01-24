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
            aktørId = "123",
            vedtaksperiodeId = UUID.randomUUID(),
            generasjonId = generasjonId,
            type = "FØRSTEGANGSBEHANDLING",
            hendelseId = UUID.randomUUID(),
            kilde = Kilde(
                meldingsreferanseId = UUID.randomUUID(),
                innsendt = LocalDateTime.now(),
                registrert = LocalDateTime.now(),
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
                    """select aktørId, vedtaksperiodeId, generasjonId, type, meldingsreferanseId, innsendt, registrert, avsender, hendelseId from generasjon_opprettet where generasjonId = :generasjonId""",
                    mapOf("generasjonId" to generasjonId)
                )
                    .map { row ->
                        GenerasjonOpprettet(
                            aktørId = row.string("aktørId"),
                            vedtaksperiodeId = row.uuid("vedtaksperiodeId"),
                            generasjonId = row.uuid("generasjonId"),
                            type = row.string("type"),
                            hendelseId = row.uuid("hendelseId"),
                            kilde = Kilde(
                                meldingsreferanseId = row.uuid("meldingsreferanseId"),
                                innsendt = row.localDateTime("innsendt"),
                                registrert = row.localDateTime("registrert"),
                                avsender = row.string("avsender")
                            )
                        )
                    }.asSingle
            )
        }
    }
}