package no.nav.helse.spre.oppgaver

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.oppgaver.DokumentTypeDTO.Inntektsmelding
import no.nav.helse.spre.oppgaver.DokumentTypeDTO.Søknad
import no.nav.helse.spre.oppgaver.OppdateringstypeDTO.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS
import java.util.*
import kotlin.math.absoluteValue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndTest {
    private val dataSource = setupDataSourceMedFlyway()

    private val rapid = TestRapid()
    private val oppgaveDAO = OppgaveDAO(dataSource)
    private var publiserteOppgaver = mutableListOf<OppgaveDTO>()

    init {
    val fakePublisist = Publisist { dokumentId: String, dto: OppgaveDTO -> publiserteOppgaver.add(dto) }
        rapid.registerRivers(oppgaveDAO, fakePublisist)
    }

    @BeforeEach
    fun reset() {
        publiserteOppgaver.clear()
        rapid.reset()
    }

    @Test
    fun `spleis håndterer et helt sykeforløp`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.randomUUID()
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknad1HendelseId),
            tilstand = "AVVENTER_INNTEKTSMELDING_FERDIG_GAP"
        )
        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknad1HendelseId, inntektsmeldingHendelseId),
            tilstand = "AVVENTER_SIMULERING"
        )

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknad1HendelseId, inntektsmeldingHendelseId),
            tilstand = "AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD"
        )

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknad1HendelseId, inntektsmeldingHendelseId),
            tilstand = "AVSLUTTET"
        )

        publiserteOppgaver[0].assertInnhold(Utsett, søknad1DokumentId, Søknad)
        publiserteOppgaver[1].assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[2].assertInnhold(Ferdigbehandlet, søknad1DokumentId, Søknad)
        publiserteOppgaver[3].assertInnhold(Ferdigbehandlet, inntektsmeldingDokumentId, Inntektsmelding)
        assertEquals(4, publiserteOppgaver.size)

        assertEquals(4, rapid.inspektør.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_utsatt", søknad1HendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_ferdigbehandlet", søknad1HendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingHendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_ferdigbehandlet", inntektsmeldingHendelseId).size)
    }

    @Test
    fun `spleis replayer søknad`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.randomUUID()

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknad1HendelseId),
            tilstand = "AVVENTER_INNTEKTSMELDING_FERDIG_GAP"
        )
        sendVedtaksperiodeEndret(hendelseIder = listOf(søknad1HendelseId), tilstand = "AVSLUTTET")

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknad1HendelseId),
            tilstand = "AVVENTER_INNTEKTSMELDING_FERDIG_GAP"
        )
        sendVedtaksperiodeEndret(hendelseIder = listOf(søknad1HendelseId), tilstand = "AVSLUTTET")

        publiserteOppgaver[0].also { dto ->
            dto.assertInnhold(Utsett, søknad1DokumentId, Søknad)
            assertTrue(SECONDS.between(dto.timeout, LocalDateTime.now().plusDays(110)).absoluteValue < 2)
        }
        publiserteOppgaver[1].assertInnhold(Ferdigbehandlet, søknad1DokumentId, Søknad)
        assertEquals(2, publiserteOppgaver.size)

        assertEquals(2, rapid.inspektør.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_utsatt", søknad1HendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_ferdigbehandlet", søknad1HendelseId).size)
    }

    @Test
    fun `spleis gir opp behandling av søknad`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.randomUUID()

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendVedtaksperiodeEndret(hendelseIder = listOf(søknad1HendelseId), tilstand = "TIL_INFOTRYGD")
        opprettOppgave(hendelseIder = listOf(søknad1HendelseId))

        publiserteOppgaver[0].assertInnhold(Opprett, søknad1DokumentId, Søknad)
        assertEquals(1, publiserteOppgaver.size)

        assertEquals(1, rapid.inspektør.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", søknad1HendelseId).size)
    }

    @Test
    fun `spleis sender medlingen opprettOppgaveForSpeilsaksbehandler`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.randomUUID()
        val imDokumentId = UUID.randomUUID()
        val imHendelseId = UUID.randomUUID()

        sendInntektsmelding(imHendelseId, imDokumentId)
        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        opprettOppgaveForSpeilsaksbehandler(hendelseIder = listOf(søknad1HendelseId, imHendelseId))

        assertEquals(2, publiserteOppgaver.size)
        publiserteOppgaver[0].assertInnhold(OpprettSpeilRelatert, søknad1DokumentId, Søknad)
        publiserteOppgaver[1].assertInnhold(OpprettSpeilRelatert, imDokumentId, Inntektsmelding)

        assertEquals(2, rapid.inspektør.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett_speilrelatert", søknad1HendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett_speilrelatert", imHendelseId).size)
    }

    @Test
    fun `spleis gir opp behandling i vilkårsprøving`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendVedtaksperiodeEndret(hendelseIder = listOf(inntektsmeldingHendelseId), tilstand = "AVVENTER_VILKÅRSPRØVING")
        sendVedtaksperiodeEndret(hendelseIder = listOf(inntektsmeldingHendelseId), tilstand = "TIL_INFOTRYGD")
        opprettOppgave(hendelseIder = listOf(inntektsmeldingHendelseId))

        publiserteOppgaver[0].assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[1].assertInnhold(Opprett, inntektsmeldingDokumentId, Inntektsmelding)

        assertEquals(2, rapid.inspektør.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingHendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", inntektsmeldingHendelseId).size)
    }

    @Test
    fun `tåler meldinger som mangler kritiske felter`() = runBlocking {
        rapid.sendTestMessage("{}")
        assertTrue(publiserteOppgaver.isEmpty())
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `ignorerer endrede vedtaksperioder uten tidligere dokumenter`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        sendVedtaksperiodeEndret(hendelseIder = listOf(inntektsmeldingHendelseId), tilstand = "AVVENTER_VILKÅRSPRØVING")

        assertTrue(publiserteOppgaver.isEmpty())
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `ignorerer avslutting av AG-søknad`() {
        val søknadArbeidsgiverHendelseId = UUID.randomUUID()
        val søknadArbeidsgiverDokumentId = UUID.randomUUID()

        sendArbeidsgiversøknad(søknadArbeidsgiverHendelseId, søknadArbeidsgiverDokumentId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadArbeidsgiverHendelseId),
            tilstand = "AVSLUTTET_UTEN_UTBETALING"
        )

        assertTrue(publiserteOppgaver.isEmpty())
    }

    @Test
    fun `vedtaksperiode avsluttes uten utbetaling med inntektsmelding`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        val søknadHendelseId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()

        sendVedtaksperiodeEndret(hendelseIder = emptyList(), tilstand = "MOTTATT_SYKMELDING_FERDIG_GAP")
        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_SØKNAD_FERDIG_GAP"
        )
        sendSøknad(søknadHendelseId, søknadDokumentId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId, søknadHendelseId),
            tilstand = "AVSLUTTET_UTEN_UTBETALING"
        )

        publiserteOppgaver[0].assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[1].assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[2].assertInnhold(Ferdigbehandlet, søknadDokumentId, Søknad)

        assertEquals(3, publiserteOppgaver.size)
        assertEquals(2, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingHendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadHendelseId).size)
    }

    @Test
    fun `Forkastet oppgave på inntektsmelding skal opprettes`() {
        val periode1 = UUID.randomUUID()
        val periode2 = UUID.randomUUID()

        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        navSøknadUtenUtbetaling(inntektsmeldingHendelseId, periode1)

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_HISTORIKK",
            vedtaksperiodeId = periode2
        )
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "TIL_INFOTRYGD",
            vedtaksperiodeId = periode2
        )
        opprettOppgave(hendelseIder = listOf(inntektsmeldingHendelseId))

        assertEquals(3, publiserteOppgaver.size)

        publiserteOppgaver[0].assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[1].assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[2].assertInnhold(Opprett, inntektsmeldingDokumentId, Inntektsmelding)
    }

    @Test
    fun `Sender ikke flere opprett-meldinger hvis vi allerede har forkastet en periode`() {
        val periode1 = UUID.randomUUID()
        val periode2 = UUID.randomUUID()
        val periode3 = UUID.randomUUID()

        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        navSøknadUtenUtbetaling(inntektsmeldingHendelseId, periode1)

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_HISTORIKK",
            vedtaksperiodeId = periode2
        )
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "TIL_INFOTRYGD",
            vedtaksperiodeId = periode2
        )
        opprettOppgave(hendelseIder = listOf(inntektsmeldingHendelseId))


        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_HISTORIKK",
            vedtaksperiodeId = periode3
        )
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "TIL_INFOTRYGD",
            vedtaksperiodeId = periode3
        )
        opprettOppgave(hendelseIder = listOf(inntektsmeldingHendelseId))


        assertEquals(3, publiserteOppgaver.size)
        assertEquals(2, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingHendelseId).size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[1].oppdateringstype)

        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", inntektsmeldingHendelseId).size)
        assertEquals(Opprett, publiserteOppgaver[2].oppdateringstype)
    }

    @Test
    fun `Sender kun opprett oppgaver på forkastet inntektsmelding dersom forrige periode var en kort periode`() {
        val periode1 = UUID.randomUUID()
        val periode2 = UUID.randomUUID()
        val periode3 = UUID.randomUUID()

        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_HISTORIKK",
            vedtaksperiodeId = periode1
        )
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING",
            vedtaksperiodeId = periode1
        )

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_HISTORIKK",
            vedtaksperiodeId = periode2
        )
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVSLUTTET",
            vedtaksperiodeId = periode2
        )

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_HISTORIKK",
            vedtaksperiodeId = periode3
        )
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "TIL_INFOTRYGD",
            vedtaksperiodeId = periode3
        )
        opprettOppgave(hendelseIder = listOf(inntektsmeldingHendelseId))

        assertEquals(2, publiserteOppgaver.size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Ferdigbehandlet, publiserteOppgaver[1].oppdateringstype)
    }

    @Test
    fun `to perioder uten utbetaling og en lang periode hvor siste går til infotrygd`() {
        val periode1 = UUID.randomUUID()
        val periode2 = UUID.randomUUID()
        val periode3 = UUID.randomUUID()

        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        navSøknadUtenUtbetaling(inntektsmeldingHendelseId, periode1)

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        navSøknadUtenUtbetaling(inntektsmeldingHendelseId, periode2)

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_HISTORIKK",
            vedtaksperiodeId = periode3
        )
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "TIL_INFOTRYGD",
            vedtaksperiodeId = periode3
        )
        opprettOppgave(hendelseIder = listOf(inntektsmeldingHendelseId))

        assertEquals(3, rapid.inspektør.size)
        assertEquals(3, publiserteOppgaver.size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[1].oppdateringstype)
        assertEquals(Opprett, publiserteOppgaver[2].oppdateringstype)
    }

    @Test
    fun `to arbeidgiversøknad-perioder og en lang periode hvor siste går til infotrygd`() {
        val periode1 = UUID.randomUUID()
        val periode2 = UUID.randomUUID()
        val periode3 = UUID.randomUUID()

        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        arbeidsgiverSøknad(inntektsmeldingHendelseId, periode1)

        arbeidsgiverSøknad(inntektsmeldingHendelseId, periode2)

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_HISTORIKK",
            vedtaksperiodeId = periode3
        )
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "TIL_INFOTRYGD",
            vedtaksperiodeId = periode3
        )
        opprettOppgave(hendelseIder = listOf(inntektsmeldingHendelseId))

        assertEquals(2, rapid.inspektør.size)
        assertEquals(2, publiserteOppgaver.size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Opprett, publiserteOppgaver[1].oppdateringstype)
    }

    @Test
    fun `kort periode - forlengelse #1 utbetales - forlengelse #2 forkastes`() {
        val periode1 = UUID.randomUUID()
        val periode2 = UUID.randomUUID()
        val periode3 = UUID.randomUUID()

        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        arbeidsgiverSøknad(inntektsmeldingHendelseId, periode1)

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_HISTORIKK",
            vedtaksperiodeId = periode2
        )

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVSLUTTET",
            vedtaksperiodeId = periode2
        )

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_HISTORIKK",
            vedtaksperiodeId = periode3
        )
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "TIL_INFOTRYGD",
            vedtaksperiodeId = periode3
        )
        opprettOppgave(hendelseIder = listOf(inntektsmeldingHendelseId))

        assertEquals(2, rapid.inspektør.size)
        assertEquals(2, publiserteOppgaver.size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Ferdigbehandlet, publiserteOppgaver[1].oppdateringstype)
    }

    @Test
    fun `oppretter oppgaver for perioder som var avsluttet, men som blir kastet ut senere`() {
        val periode = UUID.randomUUID()
        val søknadId = UUID.randomUUID()

        sendSøknad(søknadId)
        arbeidsgiverSøknad(søknadId, periode)
        opprettOppgave(listOf(søknadId))

        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", søknadId).size)
        assertEquals(2, publiserteOppgaver.size)
        assertEquals(Ferdigbehandlet, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Opprett, publiserteOppgaver[1].oppdateringstype)
    }

    @Test
    fun `utsetter oppgave for inntektsmelding som treffer perioden i AVSLUTTET_UTEN_UTBETALING`() {
        val periode = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        sendSøknad(søknadId)
        arbeidsgiverSøknad(søknadId, periode)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId).size)

        sendInntektsmelding(inntektsmeldingId, UUID.randomUUID())
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadId, inntektsmeldingId),
            tilstand = "AVSLUTTET_UTEN_UTBETALING",
            vedtaksperiodeId = periode
        )

        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingId).size)
        assertEquals(2, publiserteOppgaver.size)
        assertEquals(Ferdigbehandlet, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[1].oppdateringstype)
    }

    @Test
    fun `utsetter oppgaver for inntektsmelding som ikke validerer der den treffer en periode i AVSLUTTET_UTEN_UTBETALING`() {
        val periode = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        sendSøknad(søknadId)
        arbeidsgiverSøknad(søknadId, periode)

        sendInntektsmelding(inntektsmeldingId, UUID.randomUUID())
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadId, inntektsmeldingId),
            tilstand = "TIL_INFOTRYGD",
            vedtaksperiodeId = periode
        )
        opprettOppgave(hendelseIder = listOf(inntektsmeldingId))

        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", inntektsmeldingId).size)
        assertEquals(2, publiserteOppgaver.size)
        assertEquals(Ferdigbehandlet, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Opprett, publiserteOppgaver[1].oppdateringstype)
    }

    @Test
    fun `mottar hendelse_ikke_håndtert uten at hendelsen er tidligere lest`() {
        val hendelseId = UUID.randomUUID()
        sendHendelseIkkeHåndtert(hendelseId)
        assertTrue(publiserteOppgaver.isEmpty())
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `spleis håndterer ikke søknad og vi mottar aldri vedtaksperiode_endret`() {
        val søknadId = UUID.randomUUID()
        val hendelseId = UUID.randomUUID()
        sendSøknad(hendelseId, søknadId)
        sendHendelseIkkeHåndtert(hendelseId)

        assertEquals(1, publiserteOppgaver.size)
        assertEquals(søknadId, publiserteOppgaver[0].dokumentId)
        assertEquals(Opprett, publiserteOppgaver[0].oppdateringstype)

        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", hendelseId).size)
    }

    @Test
    fun `spleis håndterer ikke søknad og vi mottar vedtaksperiode_endret uten søknadId`() {
        val søknadId = UUID.randomUUID()
        val hendelseId = UUID.randomUUID()

        sendSøknad(hendelseId, søknadId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(UUID.randomUUID()), // eksempelvis sykmeldingen
            tilstand = "TIL_INFOTRYGD"
        )
        sendHendelseIkkeHåndtert(hendelseId)
        assertEquals(1, publiserteOppgaver.size)
        assertEquals(søknadId, publiserteOppgaver[0].dokumentId)
        assertEquals(Opprett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", hendelseId).size)
    }

    @Test
    fun `spleis håndterer ikke søknad og vi mottar vedtaksperiode_endret med søknadId`() {
        val søknadId = UUID.randomUUID()
        val hendelseId = UUID.randomUUID()

        sendSøknad(hendelseId, søknadId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(hendelseId),
            tilstand = "AVVENTER_HISTORIKK"
        )
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(hendelseId),
            tilstand = "TIL_INFOTRYGD"
        )
        sendHendelseIkkeHåndtert(hendelseId)
        assertEquals(2, publiserteOppgaver.size)
        assertEquals(søknadId, publiserteOppgaver[0].dokumentId)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(søknadId, publiserteOppgaver[1].dokumentId)
        assertEquals(Opprett, publiserteOppgaver[1].oppdateringstype)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", hendelseId).size)
    }

    @Test
    fun `spleis håndterer ikke søknad og sier ifra flere ganger`() {
        val søknadId = UUID.randomUUID()
        val hendelseId = UUID.randomUUID()

        sendSøknad(hendelseId, søknadId)
        sendHendelseIkkeHåndtert(hendelseId)
        sendHendelseIkkeHåndtert(hendelseId)

        assertEquals(1, publiserteOppgaver.size)
        assertEquals(søknadId, publiserteOppgaver[0].dokumentId)
        assertEquals(Opprett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", hendelseId).size)
    }

    @Test
    fun `oppretter oppgaver for søknad og inntektsmelding når perioden går til infotrygd`() {
        val periode = UUID.randomUUID()
        val søknadId1 = UUID.randomUUID()
        val søknadId2 = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        sendSøknad(søknadId1)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadId1),
            tilstand = "AVSLUTTET_UTEN_UTBETALING",
            vedtaksperiodeId = periode
        )

        sendInntektsmelding(inntektsmeldingId, UUID.randomUUID())
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadId1, inntektsmeldingId),
            tilstand = "AVSLUTTET_UTEN_UTBETALING",
            vedtaksperiodeId = periode
        )
        opprettOppgave(hendelseIder = listOf(inntektsmeldingId))

        sendSøknad(søknadId2)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadId2, inntektsmeldingId),
            tilstand = "TIL_INFOTRYGD",
            vedtaksperiodeId = periode
        )
        opprettOppgave(hendelseIder = listOf(søknadId2))

        assertEquals(4, publiserteOppgaver.size)
        assertEquals(Ferdigbehandlet, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[1].oppdateringstype)
        assertEquals(Opprett, publiserteOppgaver[2].oppdateringstype)
        assertEquals(Opprett, publiserteOppgaver[3].oppdateringstype)
        assertEquals(4, rapid.inspektør.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId1).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", søknadId2).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", inntektsmeldingId).size)
    }

    @ParameterizedTest
    @MethodSource("permutations")
    fun `setter timeout på oppgave for inntektsmelding avhengig av utbetaling til søker`(
        inntekt: Double, refusjon: Double?, dager: Long
    ) {
        val hendelseId = UUID.randomUUID()
        val dokumentId = UUID.randomUUID()

        sendInntektsmelding(hendelseId, dokumentId, inntekt, refusjon)

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(hendelseId),
            tilstand = "AVVENTER_SØKNAD_FERDIG_GAP",
            vedtaksperiodeId = UUID.randomUUID()
        )

        assertEquals(1, publiserteOppgaver.size)

        publiserteOppgaver[0].also { dto ->
            dto.assertInnhold(Utsett, dokumentId, Inntektsmelding)
            assertTrue(SECONDS.between(dto.timeout, LocalDateTime.now().plusDays(dager)).absoluteValue < 2)
        }
    }

    @Test
    fun `setter ny timeout på oppgaver når vedtaksperioden går til godkjenning`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()
        val søknadHendelseId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()

        sendSøknad(søknadHendelseId, søknadDokumentId)
        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId, søknadHendelseId),
            tilstand = "AVVENTER_SØKNAD_FERDIG_GAP",
            vedtaksperiodeId = vedtaksperiodeId,
        )

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId, søknadHendelseId),
            tilstand = "AVVENTER_GODKJENNING",
            vedtaksperiodeId = vedtaksperiodeId,
        )

        assertEquals(4, publiserteOppgaver.size)

        publiserteOppgaver[2].also { dto ->
            dto.assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
            assertTrue(SECONDS.between(dto.timeout, LocalDateTime.now().plusDays(110)).absoluteValue < 2)
        }
        publiserteOppgaver[3].also { dto ->
            dto.assertInnhold(Utsett, søknadDokumentId, Søknad)
            assertTrue(SECONDS.between(dto.timeout, LocalDateTime.now().plusDays(110)).absoluteValue < 2)
        }
    }

    @Test
    fun `setter ikke ny timeout hvis IM-oppgave allerede er avsluttet`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknad1HendelseId),
            tilstand = "AVVENTER_UFERDIG",
            vedtaksperiodeId = vedtaksperiodeId,
        )
        opprettOppgave(hendelseIder = listOf(søknad1HendelseId))

        publiserteOppgaver[1].assertInnhold(Opprett, søknad1DokumentId, Søknad)
        val antallOppgaverFørOgEtterGodkjenningEvent = 2
        assertEquals(antallOppgaverFørOgEtterGodkjenningEvent, publiserteOppgaver.size)

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknad1HendelseId),
            tilstand = "AVVENTER_GODKJENNING",
            vedtaksperiodeId = vedtaksperiodeId,
        )
        assertEquals(antallOppgaverFørOgEtterGodkjenningEvent, publiserteOppgaver.size)
    }

    companion object {
        @JvmStatic
        fun permutations() = listOf(
            Arguments.of(40000.00, 50000.95, 1),
            Arguments.of(40000.00, null, 1),
            Arguments.of(40000.00, 40000.95, 40),
            Arguments.of(40000.95, 40000.00, 40),
            Arguments.of(40001.00, 40000.99, 1),
        )
    }

    private fun OppgaveDTO.assertInnhold(
        oppdateringstypeDTO: OppdateringstypeDTO,
        dokumentId: UUID,
        dokumentType: DokumentTypeDTO,
    ) {
        assertEquals(dokumentId, this.dokumentId)
        assertEquals(dokumentType, this.dokumentType)
        assertEquals(oppdateringstypeDTO, oppdateringstype)
    }

    private fun sendSøknad(hendelseId: UUID, dokumentId: UUID = UUID.randomUUID()) {
        rapid.sendTestMessage(sendtSøknad(hendelseId, dokumentId))
    }

    private fun sendArbeidsgiversøknad(hendelseId: UUID, dokumentId: UUID = UUID.randomUUID()) {
        rapid.sendTestMessage(sendtArbeidsgiversøknad(hendelseId, dokumentId))
    }

    private fun sendInntektsmelding(hendelseId: UUID, dokumentId: UUID, inntekt: Double = 30000.00, refusjon: Double? = inntekt) {
        rapid.sendTestMessage(inntektsmelding(hendelseId, dokumentId, inntekt, refusjon))
    }

    private fun sendHendelseIkkeHåndtert(hendelseId: UUID) {
        rapid.sendTestMessage(hendelseIkkeHåndtert(hendelseId))
    }

    private fun sendVedtaksperiodeEndret(
        hendelseIder: List<UUID>,
        tilstand: String,
        vedtaksperiodeId: UUID = UUID.randomUUID()
    ) {
        rapid.sendTestMessage(vedtaksperiodeEndret(hendelseIder, tilstand, vedtaksperiodeId))
    }

    private fun opprettOppgave(
        hendelseIder: List<UUID>,
    ) {
        rapid.sendTestMessage(no.nav.helse.spre.oppgaver.opprettOppgave(hendelseIder))
    }

    private fun opprettOppgaveForSpeilsaksbehandler(
        hendelseIder: List<UUID>,
    ) {
        rapid.sendTestMessage(no.nav.helse.spre.oppgaver.opprettOppgaveForSpeilsaksbehandler(hendelseIder))
    }

    private fun navSøknadUtenUtbetaling(inntektsmeldingHendelseId: UUID, periode2: UUID) {
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_HISTORIKK",
            vedtaksperiodeId = periode2
        )
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVSLUTTET_UTEN_UTBETALING",
            vedtaksperiodeId = periode2
        )
    }

    private fun arbeidsgiverSøknad(hendelseId: UUID, periode1: UUID) {
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(hendelseId),
            tilstand = "AVSLUTTET_UTEN_UTBETALING",
            vedtaksperiodeId = periode1
        )
    }
}

private fun TestRapid.RapidInspector.events(eventnavn: String, hendelseId: UUID) =
    (0.until(size)).map(::message)
        .filter { it["@event_name"].textValue() == eventnavn }
        .filter { it["hendelseId"].textValue() == hendelseId.toString() }


fun vedtaksperiodeEndret(
    hendelser: List<UUID>,
    gjeldendeTilstand: String,
    vedtaksperiodeId: UUID
) =
    """{
            "@event_name": "vedtaksperiode_endret",
            "hendelser": ${hendelser.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }},
            "gjeldendeTilstand": "$gjeldendeTilstand",
            "vedtaksperiodeId": "$vedtaksperiodeId"
        }"""

fun opprettOppgaveForSpeilsaksbehandler(
    hendelser: List<UUID>
) =
    """{
            "@event_name": "opprett_oppgave_for_speilsaksbehandlere",
            "hendelser": ${hendelser.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}
        }"""

fun opprettOppgave(
    hendelser: List<UUID>
) =
    """{
            "@event_name": "opprett_oppgave",
            "hendelser": ${hendelser.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}
        }"""

fun hendelseIkkeHåndtert(
    hendelseId: UUID,
) = """{
            "@event_name": "hendelse_ikke_håndtert",
            "hendelseId": "$hendelseId"
        }"""
