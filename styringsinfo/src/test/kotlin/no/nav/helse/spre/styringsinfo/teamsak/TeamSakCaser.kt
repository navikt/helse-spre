package no.nav.helse.spre.styringsinfo.teamsak

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest
import no.nav.helse.spre.styringsinfo.teamsak.TeamSakTest.Companion.Førstegangsbehandling
import no.nav.helse.spre.styringsinfo.teamsak.TeamSakTest.Companion.TilInfotrygd
import no.nav.helse.spre.styringsinfo.teamsak.TeamSakTest.Companion.avsluttetMedVedtak
import no.nav.helse.spre.styringsinfo.teamsak.TeamSakTest.Companion.avsluttetUtenVedtak
import no.nav.helse.spre.styringsinfo.teamsak.TeamSakTest.Companion.generasjonForkastet
import no.nav.helse.spre.styringsinfo.teamsak.TeamSakTest.Companion.generasjonOpprettet
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.PostgresBehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.GenerasjonOpprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TeamSakCaser : AbstractDatabaseTest() {

    private val hendelseDao: HendelseDao = PostgresHendelseDao(dataSource)
    private val behandlingshendelseDao: BehandlingshendelseDao = PostgresBehandlingshendelseDao(dataSource)

    @Test
    fun `case 1 lage litt eksempeldata til team sak`() {
        /*
        Scenario 1: en vedtaksperioder

        Bruker får sykmelding og sender søknad for januar. Arbeidsgiver sender inn inntektsmelding. Perioden utbetales.

        */

        // generasjon opprettet med vedtak - januar

        val (behandlingId, januarGenerasjonOpprettet) = generasjonOpprettet(
            Førstegangsbehandling,
            aktørId = "Scenario 1"
        )
        januarGenerasjonOpprettet.håndter(hendelseDao, behandlingshendelseDao)
        val januarAvsluttetMedVedtak = avsluttetMedVedtak(behandlingId)
        januarAvsluttetMedVedtak.håndter(hendelseDao, behandlingshendelseDao)
    }

    @Test
    fun `case 2 lage litt eksempeldata til team sak`() {
        /*
        Scenario 2: to vedtaksperioder, forlengelsen får én generasjon

        Bruker får sykmelding og sender søknad for januar. Arbeidsgiver sender inn inntektsmelding. Perioden utbetales.
        Bruker får sykmelding og sender søknad for februar. Perioden utbetales.
        Bruker sender korrigerende søknad med noen feriedager i februar. Perioden revurderes og utbetales.

        */
        // generasjon opprettet med vedtak - januar
        val (behandlingIdJanuar, januarGenerasjonOpprettet) = generasjonOpprettet(
            Førstegangsbehandling,
            aktørId = "Scenario 2"
        )
        januarGenerasjonOpprettet.håndter(hendelseDao, behandlingshendelseDao)

        val januarAvsluttetMedVedtak = avsluttetMedVedtak(behandlingIdJanuar)
        januarAvsluttetMedVedtak.håndter(hendelseDao, behandlingshendelseDao)

        // generasjon opprettet med vedtak - februar
        val (behandlingIdFebruar, februarGenerasjonOpprettet, sakIdFebruar) = generasjonOpprettet(
            Førstegangsbehandling,
            aktørId = "Scenario 2"
        )
        februarGenerasjonOpprettet.håndter(hendelseDao, behandlingshendelseDao)

        val februarAvsluttetMedVedtak = avsluttetMedVedtak(behandlingIdFebruar)
        februarAvsluttetMedVedtak.håndter(hendelseDao, behandlingshendelseDao)

        // generasjon opprettet med vedtak - februar igjen?
        val (andreGenerasjonFebruar, andreFebruarGenerasjonOpprettet) = generasjonOpprettet(
            Førstegangsbehandling,
            aktørId = "Scenario 2",
            sakId = sakIdFebruar
        )
        andreFebruarGenerasjonOpprettet.håndter(hendelseDao, behandlingshendelseDao)

        val andreFebruarAvsluttetMedVedtak = avsluttetMedVedtak(andreGenerasjonFebruar)
        andreFebruarAvsluttetMedVedtak.håndter(hendelseDao, behandlingshendelseDao)
    }

    @Test
    fun `case 3 en enkel auu`() {
        // scenario 3: En enkel AUU

        // Bruker sender søknad som er helt innenfor arbeidsgiverperioden.

        // generasjon opprettet med vedtak - januar
        val (generasjonJanuar, januarGenerasjonOpprettet) = generasjonOpprettet(
            Førstegangsbehandling,
            aktørId = "Scenario 3"
        )
        januarGenerasjonOpprettet.håndter(hendelseDao, behandlingshendelseDao)

        val januarAvsluttetUtenVedtak = avsluttetUtenVedtak(generasjonJanuar)
        januarAvsluttetUtenVedtak.håndter(hendelseDao, behandlingshendelseDao)
    }

    @Test
    fun `case 4 en forkastet periode`() {
        // scenario 4: En enkel forkasting

        // Bruker sender søknad som inneholder detaljer vi ikke støtter i vedtaksløsningen enda.
        // Arbeidsgiver sender inntektsmelding.
        // Saken forkastes og løses i Infotrygd

        // generasjon opprettet med vedtak - januar
        val (_, januarGenerasjonOpprettet, sakId) = generasjonOpprettet(
            Førstegangsbehandling,
            aktørId = "Scenario 4"
        )
        januarGenerasjonOpprettet.håndter(hendelseDao, behandlingshendelseDao)

        val generasjonForkastet = generasjonForkastet(sakId)
        generasjonForkastet.håndter(hendelseDao, behandlingshendelseDao)
    }

    @Test
    fun `case 5 en annullert periode`() {
        // scenario 5: En enkel forkasting

        // Bruker sender søknad.
        // Arbeidsgiver sender inntektsmelding. Vedtaksperioden utbetales
        // Ny inntektsmelding betyr at saken ikke kan håndteres av ny vedtaksløsning livevel og saksbehandler annullerer

        // generasjon opprettet med vedtak - januar
        val (generasjonJanuar, januarGenerasjonOpprettet, sakId) = generasjonOpprettet(
            Førstegangsbehandling,
            aktørId = "Scenario 5"
        )
        januarGenerasjonOpprettet.håndter(hendelseDao, behandlingshendelseDao)

        val januarAvsluttetMedVedtak = avsluttetMedVedtak(generasjonJanuar)
        januarAvsluttetMedVedtak.håndter(hendelseDao, behandlingshendelseDao)

        val (_, januarAnnullertGenerasjonOpprettet) = generasjonOpprettet(
            TilInfotrygd,
            aktørId = "Scenario 5",
            sakId = sakId,
            avsender = GenerasjonOpprettet.Avsender("SAKSBEHANDLER")
        )
        januarAnnullertGenerasjonOpprettet.håndter(hendelseDao, behandlingshendelseDao)

        val generasjonForkastet = generasjonForkastet(sakId)
        generasjonForkastet.håndter(hendelseDao, behandlingshendelseDao)
    }

    @BeforeEach
    fun beforeEach() {
        sessionOf(dataSource).use { session ->
            session.run(queryOf("truncate table behandlingshendelse;").asExecute)
        }
    }
}