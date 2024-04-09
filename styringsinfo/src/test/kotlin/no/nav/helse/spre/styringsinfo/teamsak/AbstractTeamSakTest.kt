package no.nav.helse.spre.styringsinfo.teamsak

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest
import no.nav.helse.spre.styringsinfo.teamsak.behandling.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Mottaker.ARBEIDSGIVER
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Periodetype.FØRSTEGANGSBEHANDLING
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Hendelse
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.lang.System.getenv
import java.util.*

internal abstract class AbstractTeamSakTest: AbstractDatabaseTest() {

    private val hendelseDao: HendelseDao = PostgresHendelseDao(dataSource)
    private val behandlingshendelseDao: BehandlingshendelseDao = PostgresBehandlingshendelseDao(dataSource)

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

    protected fun nyttVedtak(sakId: SakId = SakId(UUID.randomUUID()), behandlingId: BehandlingId = BehandlingId(UUID.randomUUID()), totrinnsbehandling: Boolean = false): Pair<Behandling, Hendelsefabrikk> {
        val hendelsefabrikk = Hendelsefabrikk(sakId, behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertFalse(behandlingshendelseDao.harLagretBehandingshendelseFor(behandlingId))
        var behandling = behandlingOpprettet.håndter(behandlingId)
        assertTrue(behandlingshendelseDao.harLagretBehandingshendelseFor(behandlingId))
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        behandling = hendelsefabrikk.utkastTilVedtak().håndter(behandlingId)
        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)
        assertEquals(ARBEIDSGIVER, behandling.mottaker)
        assertNull(behandling.behandlingsresultat)

        behandling = hendelsefabrikk.vedtaksperiodeGodkjent(totrinnsbehandling = totrinnsbehandling).håndter(behandlingId)
        assertEquals(GODKJENT, behandling.behandlingstatus)

        behandling = hendelsefabrikk.vedtakFattet().håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)
        assertEquals(ARBEIDSGIVER, behandling.mottaker)
        assertEquals(Behandling.Behandlingsresultat.INNVILGET, behandling.behandlingsresultat)
        return behandling to hendelsefabrikk
    }

    protected fun nyAuu(sakId: SakId = SakId(UUID.randomUUID()), behandlingId: BehandlingId = BehandlingId(UUID.randomUUID())): Behandling {
        val hendelsefabrikk = Hendelsefabrikk(sakId, behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        val behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(Behandling.Behandlingstype.SØKNAD, behandling.behandlingstype)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)
        assertNull(behandling.relatertBehandlingId)
        return behandling
    }

    protected val BehandlingId.rader get() =  sessionOf(dataSource).use { session ->
        session.run(queryOf("select count(1) from behandlingshendelse where behandlingId='$this'").map { row -> row.int(1) }.asSingle)
    } ?: 0

    protected fun Hendelse.håndter(behandlingId: BehandlingId): Behandling {
        if (ignorer(behandlingshendelseDao)) return behandling(behandlingId)
        if (behandlingshendelseDao.harHåndtertHendelseTidligere(this.id)) return behandling(behandlingId)
        hendelseDao.lagre(this)
        håndter(behandlingshendelseDao)
        return behandling(behandlingId)
    }

    protected fun behandling(behandlingId: BehandlingId) = checkNotNull(behandlingshendelseDao.hent(behandlingId)) { "Fant ingn behandling for behandlingId $behandlingId" }

    protected fun assertUkjentBehandling(behandlingId: BehandlingId) = assertFalse(behandlingshendelseDao.harLagretBehandingshendelseFor(behandlingId))

    private val alleRader get() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select * from behandlingshendelse").map { row ->
            (objectMapper.readTree(row.string("data")) as ObjectNode).apply {
                put("sekvensnummer", row.long("sekvensnummer"))
                put("sakId", row.uuid("sakId").toString())
                put("behandlingId", row.uuid("behandlingId").toString())
                put("funksjonellTid", row.offsetDateTime("funksjonellTid").toString())
                put("tekniskTid", row.offsetDateTime("tekniskTid").toString())
                put("versjon", row.string("versjon"))
            }
        }.asList)
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
        private val String.printbar get() = take(25).padEnd(25, ' ') + "   "
        private fun List<ObjectNode>.printTabell() {
            if (isEmpty()) return
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
   }
}