package no.nav.helse.spre.styringsinfo.teamsak

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest
import no.nav.helse.spre.styringsinfo.teamsak.behandling.*
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.List
import java.lang.System.getenv

internal class TeamSakTest: AbstractDatabaseTest() {

    private val behandlingshendelseDao: BehandlingshendelseDao = PostgresBehandlingshendelseDao(dataSource)

    @Test
    fun `funksjonell lik behandling`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling)

        assertEquals(0, behandlingId.rader)
        generasjonOpprettet.håndter(behandlingshendelseDao)
        assertEquals(1, behandlingId.rader)

        val avsluttetMedVedtak = avsluttetMedVedtak(behandlingId)
        avsluttetMedVedtak.håndter(behandlingshendelseDao)
        assertEquals(2, behandlingId.rader)

        avsluttetMedVedtak.håndter(behandlingshendelseDao)
        assertEquals(2, behandlingId.rader)
    }

    @Test
    fun `start og slutt for vedtak`() {
        val (behandlingId, generasjonOpprettet, sakId) = generasjonOpprettet(Førstegangsbehandling)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        behandling = vedtaksperiodeEndret(sakId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.AvventerGodkjenning, behandling.behandlingstatus)

        behandling = avsluttetMedVedtak(behandlingId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.Vedtatt, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for auu`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val avsluttetUtenVedtak = avsluttetUtenVedtak(behandlingId)
        behandling = avsluttetUtenVedtak.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.Henlagt, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for forkastet periode`() {
        val (behandlingId, generasjonOpprettet, sakId) = generasjonOpprettet(Førstegangsbehandling)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val generasjonForkastet = generasjonForkastet(sakId)
        behandling = generasjonForkastet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.Avbrutt, behandling.behandlingsresultat)
    }

    @Test
    fun `annullering av en tidligere utbetalt periode`() {
        val (januarBehandlingId, januarGenerasjonOpprettet, januarSakId) = generasjonOpprettet(Førstegangsbehandling)

        var utbetaltBehandling = januarGenerasjonOpprettet.håndter(behandlingshendelseDao, januarBehandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, utbetaltBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, utbetaltBehandling.behandlingstype)
        assertNull(utbetaltBehandling.behandlingsresultat)
        assertEquals(utbetaltBehandling.behandlingsmetode, Behandling.Behandlingsmetode.Automatisk)


        val januarAvsluttetMedVedtak = avsluttetMedVedtak(januarBehandlingId)
        utbetaltBehandling = januarAvsluttetMedVedtak.håndter(behandlingshendelseDao, januarBehandlingId)
        assertEquals(Behandling.Behandlingstatus.Avsluttet, utbetaltBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, utbetaltBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.Vedtatt, utbetaltBehandling.behandlingsresultat)
        assertNull(utbetaltBehandling.behandlingsmetode)

        val (annulleringBehandlingId, januarAnnullertGenerasjonOpprettet) = generasjonOpprettet(TilInfotrygd, januarSakId, avsender = Saksbehandler)
        var annullertBehandling = januarAnnullertGenerasjonOpprettet.håndter(behandlingshendelseDao, annulleringBehandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, annullertBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, annullertBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.Saksbehandler, annullertBehandling.behandlingskilde)
        assertNull(annullertBehandling.behandlingsresultat)

        val generasjonForkastet = generasjonForkastet(januarSakId)
        annullertBehandling = generasjonForkastet.håndter(behandlingshendelseDao, annulleringBehandlingId)
        utbetaltBehandling = behandlingshendelseDao.hent(januarBehandlingId)!!

        assertEquals(3, januarBehandlingId.rader) // Registret, Vedtatt, Avbrutt
        assertEquals(2, annulleringBehandlingId.rader) // Registret, Avbrutt

        listOf(annullertBehandling, utbetaltBehandling).forEach {
            assertEquals(Behandling.Behandlingstatus.Avsluttet, it.behandlingstatus)
            assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, it.behandlingstype)
            assertEquals(Behandling.Behandlingsresultat.Avbrutt, it.behandlingsresultat)
        }

        assertNull(utbetaltBehandling.behandlingsmetode)
    }

    @Test
    fun `periode som blir forkastet på direkten`() {
        val (behandlingId, generasjonOpprettet, sakId) = generasjonOpprettet(TilInfotrygd)
        var behandling = generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, behandling.behandlingstype)
        assertNull(behandling.behandlingsresultat)

        val generasjonForkastet = generasjonForkastet(sakId)
        behandling = generasjonForkastet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, behandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.Avbrutt, behandling.behandlingsresultat)
    }

    @Test
    fun `en omgjøring av auu`() {
        val (behandlingId, generasjonOpprettet, sakId) = generasjonOpprettet(Førstegangsbehandling)

        val avsluttetUtenVedtak = avsluttetUtenVedtak(behandlingId)

        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertNull(behandling.relatertBehandlingId)

        behandling = avsluttetUtenVedtak.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.Henlagt, behandling.behandlingsresultat)
        assertNull(behandling.relatertBehandlingId)

        val (behandlingId2, generasjonOpprettet2) = generasjonOpprettet(Omgjøring, sakId, avsender = Arbeidsgiver)
        val behandling2 = generasjonOpprettet2.håndter(behandlingshendelseDao, behandlingId2)

        assertEquals(Behandling.Behandlingstatus.Registrert, behandling2.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Omgjøring, behandling2.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.Arbeidsgiver, behandling2.behandlingskilde)
        assertEquals(behandlingId, behandling2.relatertBehandlingId)
    }

    @BeforeEach
    fun beforeEach() {
        sessionOf(dataSource).use { session ->
            session.run(queryOf("truncate table behandlingshendelse;").asExecute)
        }
    }

    @AfterEach
    fun afterEach() {
        if (getenv("CI") == "true") return
        alleRader.printTabell()
    }

    private val BehandlingId.rader get() =  sessionOf(dataSource).use { session ->
        session.run(queryOf("select count(1) from behandlingshendelse where behandlingId='$this'").map { row -> row.int(1) }.asSingle)
    } ?: 0

    private val alleRader get() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select * from behandlingshendelse").map { row ->
            (objectMapper.readTree(row.string("data")) as ObjectNode).apply {
                put("sekvensnummer", row.long("sekvensnummer"))
                put("sakId", row.uuid("sakId").toString())
                put("behandlingId", row.uuid("behandlingId").toString())
                put("funksjonellTid", row.localDateTime("funksjonellTid").toString())
                put("tekniskTid", row.localDateTime("tekniskTid").toString())
                put("versjon", row.string("versjon"))
            }
        }.asList)
    }

    private fun Hendelse.håndter(behandlingshendelseDao: BehandlingshendelseDao, behandlingId: BehandlingId): Behandling {
        håndter(behandlingshendelseDao)
        return checkNotNull(behandlingshendelseDao.hent(behandlingId)) { "Fant ikke behandling $behandlingId" }
    }

    internal companion object {
       private val nå = LocalDateTime.now()
       private var teller = 1L
       private val nesteTidspunkt get() = nå.plusDays(teller++)

       private val objectMapper = jacksonObjectMapper()
       private val blob = objectMapper.createObjectNode()

       internal val Sykmeldt = GenerasjonOpprettet.Avsender("SYKMELDT")
       internal val Arbeidsgiver = GenerasjonOpprettet.Avsender("ARBEIDSGIVER")
       internal val Saksbehandler = GenerasjonOpprettet.Avsender("SAKSBEHANDLER")
       internal val System = GenerasjonOpprettet.Avsender("SYSTEM")

       internal val Førstegangsbehandling = GenerasjonOpprettet.Generasjonstype("Førstegangsbehandling")
       internal val TilInfotrygd = GenerasjonOpprettet.Generasjonstype("TilInfotrygd")
       internal val Omgjøring = GenerasjonOpprettet.Generasjonstype("Omgjøring")
       internal val Revurdering = GenerasjonOpprettet.Generasjonstype("Revurdering")


        private val String.printbar get() = take(25).padEnd(25, ' ') + "   "
        private fun List<ObjectNode>.printTabell() {
            println()
            println("********** Kul tabell til Team Sak **********")
            first().fieldNames().forEach { print(it.printbar) }
            println()
            forEach {
                it.fields().forEach { (_,verdi) -> print(verdi.asText().printbar) }
                println()
            }
            println()
        }

       internal fun generasjonOpprettet(
           generasjonstype: GenerasjonOpprettet.Generasjonstype,
           sakId: SakId = SakId(UUID.randomUUID()),
           behandlingId: BehandlingId = BehandlingId(UUID.randomUUID()),
           aktørId: String = "1234",
           avsender: GenerasjonOpprettet.Avsender = Sykmeldt
       ): Triple<BehandlingId, GenerasjonOpprettet, SakId> {
           val innsendt = nesteTidspunkt
           val registret = nesteTidspunkt
           val opprettet = nesteTidspunkt
           val generasjonkilde = GenerasjonOpprettet.Generasjonkilde(innsendt, registret, avsender)
           val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), opprettet, blob, sakId.id, behandlingId.id, aktørId, generasjonkilde, generasjonstype)
           return Triple(behandlingId, generasjonOpprettet, sakId)
       }
       internal fun avsluttetMedVedtak(behandlingId: BehandlingId) = AvsluttetMedVedtak(UUID.randomUUID(), nesteTidspunkt, blob, behandlingId.id)
       internal fun avsluttetUtenVedtak(behandlingId: BehandlingId) = AvsluttetUtenVedtak(UUID.randomUUID(), nesteTidspunkt, blob, behandlingId.id)
       internal fun generasjonForkastet(sakId: SakId) = GenerasjonForkastet(UUID.randomUUID(), nesteTidspunkt, blob, sakId.id)
       internal fun vedtaksperiodeEndret(sakId: SakId) = VedtaksperiodeEndret(UUID.randomUUID(), nesteTidspunkt, blob, sakId.id)
   }
}