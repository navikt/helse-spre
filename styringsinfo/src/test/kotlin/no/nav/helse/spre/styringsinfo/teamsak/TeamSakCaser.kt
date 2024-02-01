package no.nav.helse.spre.styringsinfo.teamsak

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest
import no.nav.helse.spre.styringsinfo.teamsak.TeamSakTest.Companion.avsluttetMedVedtak
import no.nav.helse.spre.styringsinfo.teamsak.TeamSakTest.Companion.avsluttetUtenVedtak
import no.nav.helse.spre.styringsinfo.teamsak.TeamSakTest.Companion.generasjonForkastet
import no.nav.helse.spre.styringsinfo.teamsak.TeamSakTest.Companion.generasjonOpprettet
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TeamSakCaser : AbstractDatabaseTest() {

    private val behandlingDao: BehandlingDao = TeamSakTest.Companion.PostgresBehandlingDao(dataSource)

    @Test
    fun `case 1 lage litt eksempeldata til team sak`() {
        /*
        Scenario 1: en vedtaksperioder

        Bruker får sykmelding og sender søknad for januar. Arbeidsgiver sender inn inntektsmelding. Perioden utbetales.

        */

        // generasjon opprettet med vedtak - januar

        val (behandlingId, januarGenerasjonOpprettet) = generasjonOpprettet(
            TeamSakTest.Førstegangsbehandling,
            aktørId = "Scenario 1"
        )
        januarGenerasjonOpprettet.håndter(behandlingDao)
        val januarAvsluttetMedVedtak = avsluttetMedVedtak(behandlingId)
        januarAvsluttetMedVedtak.håndter(behandlingDao)
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
            TeamSakTest.Førstegangsbehandling,
            aktørId = "Scenario 2"
        )
        januarGenerasjonOpprettet.håndter(behandlingDao)

        val januarAvsluttetMedVedtak = avsluttetMedVedtak(behandlingIdJanuar)
        januarAvsluttetMedVedtak.håndter(behandlingDao)

        // generasjon opprettet med vedtak - februar
        val (behandlingIdFebruar, februarGenerasjonOpprettet, sakIdFebruar) = generasjonOpprettet(
            TeamSakTest.Førstegangsbehandling,
            aktørId = "Scenario 2"
        )
        februarGenerasjonOpprettet.håndter(behandlingDao)

        val februarAvsluttetMedVedtak = avsluttetMedVedtak(behandlingIdFebruar)
        februarAvsluttetMedVedtak.håndter(behandlingDao)

        // generasjon opprettet med vedtak - februar igjen?
        val (andreGenerasjonFebruar, andreFebruarGenerasjonOpprettet) = generasjonOpprettet(
            TeamSakTest.Førstegangsbehandling,
            aktørId = "Scenario 2",
            sakId = sakIdFebruar
        )
        andreFebruarGenerasjonOpprettet.håndter(behandlingDao)

        val andreFebruarAvsluttetMedVedtak = avsluttetMedVedtak(andreGenerasjonFebruar)
        andreFebruarAvsluttetMedVedtak.håndter(behandlingDao)
    }

    @Test
    fun `case 3 en enkel auu`() {
        // scenario 3: En enkel AUU

        // Bruker sender søknad som er helt innenfor arbeidsgiverperioden.

        // generasjon opprettet med vedtak - januar
        val (generasjonJanuar, januarGenerasjonOpprettet) = generasjonOpprettet(
            TeamSakTest.Førstegangsbehandling,
            aktørId = "Scenario 3"
        )
        januarGenerasjonOpprettet.håndter(behandlingDao)

        val januarAvsluttetUtenVedtak = avsluttetUtenVedtak(generasjonJanuar)
        januarAvsluttetUtenVedtak.håndter(behandlingDao)
    }

    @Test
    fun `case 4 en forkastet periode`() {
        // scenario 4: En enkel forkasting

        // Bruker sender søknad som inneholder detaljer vi ikke støtter i vedtaksløsningen enda.
        // Arbeidsgiver sender inntektsmelding.
        // Saken forkastes og løses i Infotrygd

        // generasjon opprettet med vedtak - januar
        val (generasjonJanuar, januarGenerasjonOpprettet) = generasjonOpprettet(
            TeamSakTest.Førstegangsbehandling,
            aktørId = "Scenario 4"
        )
        januarGenerasjonOpprettet.håndter(behandlingDao)

        val generasjonForkastet = generasjonForkastet(generasjonJanuar)
        generasjonForkastet.håndter(behandlingDao)
    }

    @Test
    fun `case 5 en annullert periode`() {
        // scenario 5: En enkel forkasting

        // Bruker sender søknad.
        // Arbeidsgiver sender inntektsmelding. Vedtaksperioden utbetales
        // Ny inntektsmelding betyr at saken ikke kan håndteres av ny vedtaksløsning livevel og saksbehandler annullerer

        // generasjon opprettet med vedtak - januar
        val (generasjonJanuar, januarGenerasjonOpprettet, sakId) = generasjonOpprettet(
            TeamSakTest.Førstegangsbehandling,
            aktørId = "Scenario 5"
        )
        januarGenerasjonOpprettet.håndter(behandlingDao)

        val januarAvsluttetMedVedtak = avsluttetMedVedtak(generasjonJanuar)
        januarAvsluttetMedVedtak.håndter(behandlingDao)

        val (forkastetGenerasjon, januarAnnullertGenerasjonOpprettet) = generasjonOpprettet(
            TeamSakTest.TilInfotrygd,
            aktørId = "Scenario 5",
            sakId = sakId
        )
        januarAnnullertGenerasjonOpprettet.håndter(behandlingDao)

        val generasjonForkastet = generasjonForkastet(forkastetGenerasjon)
        generasjonForkastet.håndter(behandlingDao)
    }

    @BeforeEach
    fun beforeEach() {
        sessionOf(dataSource).use { session ->
            session.run(queryOf("truncate table behandling;").asExecute)
        }
    }
}