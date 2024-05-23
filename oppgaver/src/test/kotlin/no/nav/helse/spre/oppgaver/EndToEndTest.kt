package no.nav.helse.spre.oppgaver

import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.oppgaver.DokumentTypeDTO.Inntektsmelding
import no.nav.helse.spre.oppgaver.DokumentTypeDTO.Søknad
import no.nav.helse.spre.oppgaver.OppdateringstypeDTO.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.time.LocalDate
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
        val fakePublisist = Publisist { _: String, dto: OppgaveDTO ->
            publiserteOppgaver.add(dto)
        }
        rapid.registerRivers(oppgaveDAO, fakePublisist)
    }

    @BeforeEach
    fun reset() {
        publiserteOppgaver.clear()
        rapid.reset()
        sessionOf(dataSource).use {session ->
            session.run(queryOf(
                "TRUNCATE TABLE oppgave_tilstand, timeout;"
            ).asExecute)
        }
    }

    @Test
    fun `beholder forrige timeout på søknad om den er etter ny timeout`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.randomUUID()

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendSøknadHåndtert(søknad1HendelseId)

        assertEquals(1, publiserteOppgaver.size)
        val publisertOppgaveEtterSøknadHåndtert = publiserteOppgaver[0]
        publisertOppgaveEtterSøknadHåndtert.assertInnhold(Utsett, søknad1DokumentId, Søknad)
        val timeoutEtterSøknadHåndtert = publisertOppgaveEtterSøknadHåndtert.timeout!!

        sendVedtaksperiodeVenter(listOf(søknad1HendelseId), "GODKJENNING")

        assertEquals(2, publiserteOppgaver.size)
        val publisertOppgaveEtterVentingPåGodkjenning = publiserteOppgaver[1]
        val timeoutEtterVentingPåGodkjenning = publisertOppgaveEtterVentingPåGodkjenning.timeout!!

        assertTidsstempel(timeoutEtterSøknadHåndtert, timeoutEtterVentingPåGodkjenning)
    }

    @Test
    fun `beholder forrige timeout på inntektsmelding om den er etter ny timeout`() {
        val inntektsmelding1HendelseId = UUID.randomUUID()
        val inntektsmelding1DokumentId = UUID.randomUUID()

        sendInntektsmelding(inntektsmelding1HendelseId, inntektsmelding1DokumentId)
        sendInntektsmeldingHåndtert(inntektsmelding1HendelseId)

        assertEquals(1, publiserteOppgaver.size)
        val publisertOppgaveEtterInntektsmeldingHåndtert = publiserteOppgaver[0]
        publisertOppgaveEtterInntektsmeldingHåndtert.assertInnhold(Utsett, inntektsmelding1DokumentId, Inntektsmelding)
        val timeoutEtterInntektsmeldingHåndtert = publisertOppgaveEtterInntektsmeldingHåndtert.timeout!!

        sendVedtaksperiodeVenter(listOf(inntektsmelding1HendelseId), "GODKJENNING")

        assertEquals(2, publiserteOppgaver.size)
        val publisertOppgaveEtterVentingPåGodkjenning = publiserteOppgaver[1]
        val timeoutEtterVentingPåGodkjenning = publisertOppgaveEtterVentingPåGodkjenning.timeout!!

        assertTidsstempel(timeoutEtterInntektsmeldingHåndtert, timeoutEtterVentingPåGodkjenning)
    }

    @Test
    fun `spleis håndterer et helt sykeforløp`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.randomUUID()
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendSøknadHåndtert(søknad1HendelseId)

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendInntektsmeldingHåndtert(inntektsmeldingHendelseId)

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknad1HendelseId, inntektsmeldingHendelseId),
            tilstand = "AVVENTER_GODKJENNING"
        )
        sendAvsluttetMedVedtak(
            hendelseIder = listOf(søknad1HendelseId, inntektsmeldingHendelseId)
        )

        assertEquals(6, publiserteOppgaver.size)
        publiserteOppgaver[0].assertInnhold(Utsett, søknad1DokumentId, Søknad)
        publiserteOppgaver[1].assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[2].assertInnhold(Utsett, søknad1DokumentId, Søknad)
        publiserteOppgaver[3].assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[4].assertInnhold(Ferdigbehandlet, søknad1DokumentId, Søknad)
        publiserteOppgaver[5].assertInnhold(Ferdigbehandlet, inntektsmeldingDokumentId, Inntektsmelding)

        assertEquals(6, rapid.inspektør.size)
        assertEquals(2, rapid.inspektør.events("oppgavestyring_utsatt", søknad1HendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_ferdigbehandlet", søknad1HendelseId).size)
        assertEquals(2, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingHendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_ferdigbehandlet", inntektsmeldingHendelseId).size)
    }

    @Test
    fun `utsetter oppgave på forlengelse når perioden før avventer godkjenning`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.fromString("00000000-0000-0000-0000-500000000001")

        val søknad2HendelseId = UUID.randomUUID()
        val søknad2DokumentId = UUID.fromString("00000000-0000-0000-0000-500000000002")

        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.fromString("00000000-0000-0000-0000-100000000001")

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendSøknadHåndtert(søknad1HendelseId)

        assertEquals(1, publiserteOppgaver.size)
        publiserteOppgaver[0].let { søknadOppgave ->
            assertEquals(110, søknadOppgave.timeoutIDager)
            assertEquals(søknad1DokumentId, søknadOppgave.dokumentId)
        }

        sendSøknad(søknad2HendelseId, søknad2DokumentId)
        sendSøknadHåndtert(søknad2HendelseId)

        assertEquals(2, publiserteOppgaver.size)
        publiserteOppgaver[1].let { søknadOppgave ->
            assertEquals(110, søknadOppgave.timeoutIDager)
            assertEquals(søknad2DokumentId, søknadOppgave.dokumentId)
        }

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendInntektsmeldingHåndtert(inntektsmeldingHendelseId)

        assertEquals(3, publiserteOppgaver.size)
        publiserteOppgaver[2].let { inntektsmeldingOppgave ->
            assertEquals(60, inntektsmeldingOppgave.timeoutIDager)
            assertEquals(inntektsmeldingDokumentId, inntektsmeldingOppgave.dokumentId)
        }

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknad1HendelseId, inntektsmeldingHendelseId),
            tilstand = "AVVENTER_GODKJENNING"
        )
        assertEquals(5, publiserteOppgaver.size)
        publiserteOppgaver[3].let { søknadOppgave ->
            assertEquals(180, søknadOppgave.timeoutIDager)
            assertEquals(søknad1DokumentId, søknadOppgave.dokumentId)

        }
        publiserteOppgaver[4].let { inntektsmeldingOppgave ->
            assertEquals(180, inntektsmeldingOppgave.timeoutIDager)
            assertEquals(inntektsmeldingDokumentId, inntektsmeldingOppgave.dokumentId)
        }

        assertEquals(5, publiserteOppgaver.size)

        sendVedtaksperiodeVenter(
            hendelseIder = listOf(søknad2HendelseId, inntektsmeldingHendelseId),
            venterPåHva = "GODKJENNING"
        )

        assertEquals(7, publiserteOppgaver.size)
        publiserteOppgaver[5].let { søknadOppgave ->
            assertEquals(110, søknadOppgave.timeoutIDager)
            assertEquals(søknad2DokumentId, søknadOppgave.dokumentId)

        }
        publiserteOppgaver[6].let { inntektsmeldingOppgave ->
            assertEquals(180, inntektsmeldingOppgave.timeoutIDager)
            assertEquals(inntektsmeldingDokumentId, inntektsmeldingOppgave.dokumentId)
        }
    }

    @Test
    fun `utsetter når vi venter på overlappende abeidsgiver - MANGLER_TILSTREKKELIG_INFORMASJON_TIL_UTBETALING_ANDRE_ARBEIDSGIVERE`() {
        val periode = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        sendSøknad(søknadId)
        sendSøknadHåndtert(søknadId)
        sendInntektsmelding(inntektsmeldingId, UUID.randomUUID())
        sendInntektsmeldingHåndtert(inntektsmeldingId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadId),
            tilstand = "AVVENTER_BLOKKERENDE_PERIODE",
            vedtaksperiodeId = periode
        )

        assertEquals(2, publiserteOppgaver.size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[1].oppdateringstype)

        sendVedtaksperiodeVenter(listOf(søknadId, inntektsmeldingId), "INNTEKTSMELDING", "MANGLER_TILSTREKKELIG_INFORMASJON_TIL_UTBETALING_ANDRE_ARBEIDSGIVERE")

        assertEquals(4, publiserteOppgaver.size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[1].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[2].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[3].oppdateringstype)
    }

    @Test
    fun `utsetter når vi venter på overlappende abeidsgiver - HAR_SYKMELDING_SOM_OVERLAPPER_PÅ_ANDRE_ARBEIDSGIVERE`() {
        val periode = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        sendSøknad(søknadId)
        sendSøknadHåndtert(søknadId)
        sendInntektsmelding(inntektsmeldingId, UUID.randomUUID())
        sendInntektsmeldingHåndtert(inntektsmeldingId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadId),
            tilstand = "AVVENTER_BLOKKERENDE_PERIODE",
            vedtaksperiodeId = periode
        )

        assertEquals(2, publiserteOppgaver.size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[1].oppdateringstype)

        sendVedtaksperiodeVenter(listOf(søknadId, inntektsmeldingId), "INNTEKTSMELDING", "HAR_SYKMELDING_SOM_OVERLAPPER_PÅ_ANDRE_ARBEIDSGIVERE")

        assertEquals(4, publiserteOppgaver.size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[1].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[2].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[3].oppdateringstype)
    }

    @Test
    fun `spleis replayer søknad👽`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.randomUUID()

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendSøknadHåndtert(søknad1HendelseId)
        sendAvsluttetMedVedtak(hendelseIder = listOf(søknad1HendelseId))

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendSøknadHåndtert(søknad1HendelseId)
        sendAvsluttetMedVedtak(hendelseIder = listOf(søknad1HendelseId))

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
        vedtaksperiodeForkastet(hendelseIder = listOf(søknad1HendelseId))

        publiserteOppgaver[0].assertInnhold(Opprett, søknad1DokumentId, Søknad)
        assertEquals(1, publiserteOppgaver.size)

        assertEquals(1, rapid.inspektør.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", søknad1HendelseId).size)
    }

    @Test
    fun `oppretter ikke oppgave dersom perioden er behandlet i Infotrygd`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.randomUUID()

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        vedtaksperiodeForkastet(hendelseIder = listOf(søknad1HendelseId), behandletIInfotrygd = true)

        assertEquals(0, publiserteOppgaver.size)
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `oppgave opprettet speilrelatert harPeriodeInnenfor16Dager`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.randomUUID()
        val imDokumentId = UUID.randomUUID()
        val imHendelseId = UUID.randomUUID()

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendInntektsmelding(imHendelseId, imDokumentId)
        vedtaksperiodeForkastet(hendelseIder = listOf(søknad1HendelseId, imHendelseId), harPeriodeInnenfor16Dager = true)

        assertEquals(2, publiserteOppgaver.size)
        publiserteOppgaver[0].assertInnhold(OpprettSpeilRelatert, søknad1DokumentId, Søknad)
        publiserteOppgaver[1].assertInnhold(OpprettSpeilRelatert, imDokumentId, Inntektsmelding)

        assertEquals(2, rapid.inspektør.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett_speilrelatert", søknad1HendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett_speilrelatert", imHendelseId).size)
    }

    @Test
    fun `ignorer vedtaksperiode_forkastet som skyldes person_påminnelse`() {
        val imDokumentId = UUID.randomUUID()
        val imHendelseId = UUID.randomUUID()

        sendInntektsmelding(imHendelseId, imDokumentId)

        vedtaksperiodeForkastet(hendelseIder = listOf(imHendelseId), forårsaketAv = "person_påminnelse")
        assertEquals(0, publiserteOppgaver.size)

        vedtaksperiodeForkastet(hendelseIder = listOf(imHendelseId), forårsaketAv = "ikke_person_påminnelse")
        assertEquals(1, publiserteOppgaver.size)
    }

    @Test
    fun `oppgave opprettet speilrelatert forlenger periode`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.randomUUID()
        val imDokumentId = UUID.randomUUID()
        val imHendelseId = UUID.randomUUID()

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendInntektsmelding(imHendelseId, imDokumentId)
        vedtaksperiodeForkastet(hendelseIder = listOf(søknad1HendelseId, imHendelseId), forlengerPeriode = true)

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
        sendInntektsmeldingHåndtert(inntektsmeldingHendelseId)

        vedtaksperiodeForkastet(hendelseIder = listOf(inntektsmeldingHendelseId))

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
    fun `ignorerer signal på at dokument er håndtert uten at vi har hørt om dokument`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        sendInntektsmeldingHåndtert(inntektsmeldingHendelseId)

        assertTrue(publiserteOppgaver.isEmpty())
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `Håndterer AG-søknad som vanlig søknad`() {
        val søknadArbeidsgiverHendelseId = UUID.randomUUID()
        val søknadArbeidsgiverDokumentId = UUID.randomUUID()

        sendArbeidsgiversøknad(søknadArbeidsgiverHendelseId, søknadArbeidsgiverDokumentId)
        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(søknadArbeidsgiverHendelseId),
        )

        assertEquals(1, publiserteOppgaver.size)
        assertEquals(Ferdigbehandlet, publiserteOppgaver.single().oppdateringstype)
        assertEquals(søknadArbeidsgiverDokumentId, publiserteOppgaver.single().dokumentId)
    }

    @Test
    fun `vedtaksperiode avsluttes uten utbetaling med inntektsmelding`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        val søknadHendelseId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        inntektsmeldingFørSøknad(inntektsmeldingHendelseId)
        sendSøknad(søknadHendelseId, søknadDokumentId)
        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(inntektsmeldingHendelseId, søknadHendelseId),
        )

        publiserteOppgaver[0].assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[1].assertInnhold(Ferdigbehandlet, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[2].assertInnhold(Ferdigbehandlet, søknadDokumentId, Søknad)

        assertEquals(3, publiserteOppgaver.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_ferdigbehandlet", inntektsmeldingHendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingHendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadHendelseId).size)
    }

    @Test
    fun `Forkastet oppgave på inntektsmelding skal opprettes`() {
        val periode1 = UUID.randomUUID()

        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()
        val søknadHendelseId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()
        val søknadHendelseId2 = UUID.randomUUID()
        val søknadDokumentId2 = UUID.randomUUID()

        sendSøknad(søknadHendelseId, søknadDokumentId)
        sendSøknadHåndtert(søknadHendelseId)
        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendInntektsmeldingHåndtert(inntektsmeldingHendelseId)

        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            vedtaksperiodeId = periode1
        )

        sendSøknad(søknadHendelseId2, søknadDokumentId2)
        sendSøknadHåndtert(søknadHendelseId2)
        vedtaksperiodeForkastet(listOf(søknadHendelseId2, inntektsmeldingHendelseId))

        assertEquals(6, publiserteOppgaver.size)

        publiserteOppgaver[0].assertInnhold(Utsett, søknadDokumentId, Søknad)
        publiserteOppgaver[1].assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[2].assertInnhold(Ferdigbehandlet, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[3].assertInnhold(Utsett, søknadDokumentId2, Søknad)
        publiserteOppgaver[4].assertInnhold(Opprett, søknadDokumentId2, Søknad)
        publiserteOppgaver[5].assertInnhold(Opprett, inntektsmeldingDokumentId, Inntektsmelding)
    }

    @Test
    fun `Sender ikke flere opprett-meldinger hvis vi allerede har forkastet en periode`() {
        val periode1 = UUID.randomUUID()

        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()
        val søknadHendelseId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()
        val søknadHendelseId2 = UUID.randomUUID()
        val søknadDokumentId2 = UUID.randomUUID()
        val søknadHendelseId3 = UUID.randomUUID()
        val søknadDokumentId3 = UUID.randomUUID()

        sendSøknad(søknadHendelseId, søknadDokumentId)
        sendSøknadHåndtert(søknadHendelseId)

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendInntektsmeldingHåndtert(inntektsmeldingHendelseId)

        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            vedtaksperiodeId = periode1
        )

        sendSøknad(søknadHendelseId2, søknadDokumentId2)
        sendSøknadHåndtert(søknadHendelseId2)

        vedtaksperiodeForkastet(hendelseIder = listOf(søknadHendelseId2, inntektsmeldingHendelseId))

        sendSøknad(søknadHendelseId3, søknadDokumentId3)
        sendSøknadHåndtert(søknadHendelseId3)

        vedtaksperiodeForkastet(hendelseIder = listOf(søknadHendelseId3, inntektsmeldingHendelseId))

        assertEquals(8, publiserteOppgaver.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", inntektsmeldingHendelseId).size)
        assertEquals(Opprett, publiserteOppgaver[5].oppdateringstype)
    }

    @Test
    fun `to perioder uten utbetaling og en lang periode hvor siste går til infotrygd`() {
        val periode1 = UUID.randomUUID()
        val periode2 = UUID.randomUUID()
        val søknadHendelseId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()
        val søknadHendelseId2 = UUID.randomUUID()
        val søknadDokumentId2 = UUID.randomUUID()
        val søknadHendelseId3 = UUID.randomUUID()
        val søknadDokumentId3 = UUID.randomUUID()
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        sendSøknad(søknadHendelseId, søknadDokumentId)
        sendSøknadHåndtert(søknadHendelseId)

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendInntektsmeldingHåndtert(inntektsmeldingHendelseId)

        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(søknadHendelseId, inntektsmeldingHendelseId),
            vedtaksperiodeId = periode1
        )

        sendSøknad(søknadHendelseId2, søknadDokumentId2)
        sendSøknadHåndtert(søknadHendelseId2)
        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(søknadHendelseId2, inntektsmeldingHendelseId),
            vedtaksperiodeId = periode2
        )

        sendSøknad(søknadHendelseId3, søknadDokumentId3)
        sendSøknadHåndtert(søknadHendelseId3)
        vedtaksperiodeForkastet(hendelseIder = listOf(søknadHendelseId3, inntektsmeldingHendelseId))

        assertEquals(9, rapid.inspektør.size)
        assertEquals(9, publiserteOppgaver.size)
        assertEquals(Opprett, publiserteOppgaver[7].oppdateringstype)
        assertEquals(Opprett, publiserteOppgaver[8].oppdateringstype)
    }

    @Test
    fun `kort periode - forlengelse #1 utbetales - forlengelse #2 forkastes`() {
        val periode1 = UUID.randomUUID()
        val periode2 = UUID.randomUUID()
        val søknadHendelseId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()
        val søknadHendelseId2 = UUID.randomUUID()
        val søknadDokumentId2 = UUID.randomUUID()
        val søknadHendelseId3 = UUID.randomUUID()
        val søknadDokumentId3 = UUID.randomUUID()

        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        sendSøknad(søknadHendelseId, søknadDokumentId)
        sendSøknadHåndtert(søknadHendelseId)

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(søknadHendelseId, inntektsmeldingHendelseId),
            vedtaksperiodeId = periode1
        )

        sendSøknad(søknadHendelseId2, søknadDokumentId2)
        sendSøknadHåndtert(søknadHendelseId2)
        sendAvsluttetMedVedtak(
            hendelseIder = listOf(søknadHendelseId2, inntektsmeldingHendelseId),
            vedtaksperiodeId = periode2
        )

        sendSøknad(søknadHendelseId3, søknadDokumentId3)
        sendSøknadHåndtert(søknadHendelseId3)
        vedtaksperiodeForkastet(hendelseIder = listOf(søknadHendelseId3, inntektsmeldingHendelseId))

        assertEquals(8, rapid.inspektør.size)
        assertEquals(Opprett, publiserteOppgaver[7].oppdateringstype)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", søknadHendelseId3).size)
    }

    @Test
    fun `oppretter ikke oppgaver for perioder som var avsluttet, men som blir kastet ut senere`() {
        val periode = UUID.randomUUID()
        val søknadId = UUID.randomUUID()

        sendSøknad(søknadId)
        sendSøknadHåndtert(søknadId)
        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(søknadId),
            vedtaksperiodeId = periode
        )
        vedtaksperiodeForkastet(listOf(søknadId))

        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId).size)
        assertEquals(0, rapid.inspektør.events("oppgavestyring_opprett", søknadId).size)
        assertEquals(2, publiserteOppgaver.size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Ferdigbehandlet, publiserteOppgaver[1].oppdateringstype)
    }

    @Test
    fun `utsetter oppgave for inntektsmelding som treffer perioden i AVSLUTTET_UTEN_UTBETALING`() {
        val periode = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        sendSøknad(søknadId)
        sendSøknadHåndtert(søknadId)
        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(søknadId),
            vedtaksperiodeId = periode
        )
        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId).size)

        sendInntektsmelding(inntektsmeldingId, UUID.randomUUID())
        sendInntektsmeldingHåndtert(inntektsmeldingId)
        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(søknadId, inntektsmeldingId),
            vedtaksperiodeId = periode
        )

        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingId).size)
        assertEquals(4, publiserteOppgaver.size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Ferdigbehandlet, publiserteOppgaver[1].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[2].oppdateringstype)
        assertEquals(Ferdigbehandlet, publiserteOppgaver[3].oppdateringstype)
    }

    @Test
    fun `utsetter oppgaver for inntektsmelding som ikke validerer der den treffer en periode i AVSLUTTET_UTEN_UTBETALING`() {
        val periode = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        sendSøknad(søknadId)
        sendSøknadHåndtert(søknadId)
        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(søknadId),
            vedtaksperiodeId = periode
        )

        sendInntektsmelding(inntektsmeldingId, UUID.randomUUID())
        sendInntektsmeldingHåndtert(inntektsmeldingId)
        vedtaksperiodeForkastet(hendelseIder = listOf(inntektsmeldingId))

        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", inntektsmeldingId).size)
        assertEquals(4, publiserteOppgaver.size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Ferdigbehandlet, publiserteOppgaver[1].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[2].oppdateringstype)
        assertEquals(Opprett, publiserteOppgaver[3].oppdateringstype)
    }

    @Test
    fun `oppretter oppgaver for søknad og inntektsmelding når perioden går til infotrygd`() {
        val periode = UUID.randomUUID()
        val søknadId1 = UUID.randomUUID()
        val søknadId2 = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        sendSøknad(søknadId1)
        sendSøknadHåndtert(søknadId1)
        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(søknadId1),
            vedtaksperiodeId = periode
        )

        sendInntektsmelding(inntektsmeldingId, UUID.randomUUID())
        sendInntektsmeldingHåndtert(inntektsmeldingId)
        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(søknadId1, inntektsmeldingId),
            vedtaksperiodeId = periode
        )
        vedtaksperiodeForkastet(hendelseIder = listOf(inntektsmeldingId))

        sendSøknad(søknadId2)
        sendSøknadHåndtert(søknadId2)
        vedtaksperiodeForkastet(hendelseIder = listOf(søknadId2))

        assertEquals(7, publiserteOppgaver.size)
        assertEquals(Utsett, publiserteOppgaver[0].oppdateringstype)
        assertEquals(Ferdigbehandlet, publiserteOppgaver[1].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[2].oppdateringstype)
        assertEquals(Ferdigbehandlet, publiserteOppgaver[3].oppdateringstype)
        assertEquals(Opprett, publiserteOppgaver[4].oppdateringstype)
        assertEquals(Utsett, publiserteOppgaver[5].oppdateringstype)
        assertEquals(Opprett, publiserteOppgaver[6].oppdateringstype)
    }

    @Test
    fun `setter timeout på oppgave for inntektsmelding ved utbetaling til søker`() {
        val hendelseId = UUID.randomUUID()
        val dokumentId = UUID.randomUUID()
        val inntekt = 40000.00
        val refusjon = 20000.00

        sendInntektsmelding(hendelseId, dokumentId, inntekt, refusjon)
        sendInntektsmeldingHåndtert(hendelseId)
        assertEquals(1, publiserteOppgaver.size)
        val TimeoutLestInntektsmelding = 60L
        publiserteOppgaver[0].also { dto ->
            dto.assertInnhold(Utsett, dokumentId, Inntektsmelding)
            assertTrue(SECONDS.between(dto.timeout, LocalDateTime.now().plusDays(TimeoutLestInntektsmelding)).absoluteValue < 2)
        }
    }

    @Test
    fun `setter timeout på oppgave for inntektsmelding ved full refusjon`() {
        val hendelseId = UUID.randomUUID()
        val dokumentId = UUID.randomUUID()
        val inntekt = 40000.00
        val refusjon = 40000.00

        sendInntektsmelding(hendelseId, dokumentId, inntekt, refusjon)
        sendInntektsmeldingHåndtert(hendelseId)
        assertEquals(1, publiserteOppgaver.size)
        val TimeoutLestInntektsmelding = 60L
        publiserteOppgaver[0].also { dto ->
            dto.assertInnhold(Utsett, dokumentId, Inntektsmelding)
            assertTrue(SECONDS.between(dto.timeout, LocalDateTime.now().plusDays(TimeoutLestInntektsmelding)).absoluteValue < 2)
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
        sendSøknadHåndtert(søknadHendelseId)
        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendInntektsmeldingHåndtert(inntektsmeldingHendelseId)

        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId, søknadHendelseId),
            tilstand = "AVVENTER_GODKJENNING",
            vedtaksperiodeId = vedtaksperiodeId,
        )

        assertEquals(4, publiserteOppgaver.size)

        publiserteOppgaver[2].also { dto ->
            dto.assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
            assertTrue(SECONDS.between(dto.timeout, LocalDateTime.now().plusDays(180)).absoluteValue < 2)
        }
        publiserteOppgaver[3].also { dto ->
            dto.assertInnhold(Utsett, søknadDokumentId, Søknad)
            assertTrue(SECONDS.between(dto.timeout, LocalDateTime.now().plusDays(180)).absoluteValue < 2)
        }
    }

    @Test
    fun `utsetter inntektsmelding som treffer AUU`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()
        val søknadHendelseId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()

        sendSøknad(søknadHendelseId, søknadDokumentId)
        sendSøknadHåndtert(søknadHendelseId)

        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(søknadHendelseId),
            vedtaksperiodeId = vedtaksperiodeId,
        )
        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendInntektsmeldingHåndtert(inntektsmeldingHendelseId)

        assertEquals(3, publiserteOppgaver.size)
        assertEquals(3, publiserteOppgaver.size)
        publiserteOppgaver.last().also { dto ->
            dto.assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
        }
    }

    @Test
    fun `setter ny timeout hvis en IM kvikner en AUU-periode til live`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()
        val søknadHendelseId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()

        sendSøknad(søknadHendelseId, søknadDokumentId)
        sendSøknadHåndtert(søknadHendelseId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadHendelseId),
            tilstand = "AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK",
            vedtaksperiodeId = vedtaksperiodeId,
        )
        sendAvsluttetUtenVedtak(
            hendelseIder = listOf(søknadHendelseId),
            vedtaksperiodeId = vedtaksperiodeId,
        )
        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendInntektsmeldingHåndtert(inntektsmeldingHendelseId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId, søknadHendelseId),
            tilstand = "AVVENTER_GODKJENNING",
            vedtaksperiodeId = vedtaksperiodeId,
        )

        assertEquals(4, publiserteOppgaver.size)

        publiserteOppgaver[2].also { dto ->
            dto.assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
            assertTrue(SECONDS.between(dto.timeout, LocalDateTime.now().plusDays(60)).absoluteValue < 2)
        }
        publiserteOppgaver[3].also { dto ->
            dto.assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
            assertTrue(SECONDS.between(dto.timeout, LocalDateTime.now().plusDays(180)).absoluteValue < 2)
        }
    }

    @Test
    fun `setter ikke ny timeout hvis IM-oppgave allerede er avsluttet`() {
        val søknad1HendelseId = UUID.randomUUID()
        val søknad1DokumentId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()

        sendSøknad(søknad1HendelseId, søknad1DokumentId)
        sendSøknadHåndtert(søknad1HendelseId)
        vedtaksperiodeForkastet(hendelseIder = listOf(søknad1HendelseId))

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

    @Test
    fun `inntektsmelding kommer før søknad, søknad kastes ut ved håndtering - inntektsmelding får opprettet oppgave`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()
        sendInntektsmelding(hendelseId = inntektsmeldingHendelseId, dokumentId = inntektsmeldingDokumentId)
        inntektsmeldingFørSøknad(inntektsmeldingId = inntektsmeldingHendelseId)

        val søknadHendelseId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()
        sendSøknad(hendelseId = søknadHendelseId, dokumentId = søknadDokumentId)
        sendSøknadHåndtert(søknadHendelseId)
        vedtaksperiodeForkastet(listOf(søknadHendelseId))

        assertEquals(4, publiserteOppgaver.size)
        publiserteOppgaver[0].assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
        publiserteOppgaver[1].assertInnhold(Utsett, søknadDokumentId, Søknad)
        publiserteOppgaver[2].assertInnhold(Opprett, søknadDokumentId, Søknad)
        publiserteOppgaver[3].assertInnhold(Opprett, inntektsmeldingDokumentId, Inntektsmelding)
    }

    @Test
    fun `inntektsmelding kommer før søknad - utsetter oppgave `() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()
        sendInntektsmelding(hendelseId = inntektsmeldingHendelseId, dokumentId = inntektsmeldingDokumentId)
        inntektsmeldingFørSøknad(inntektsmeldingId = inntektsmeldingHendelseId)
        publiserteOppgaver[0].assertInnhold(Utsett, inntektsmeldingDokumentId, Inntektsmelding)
    }

    @Test
    fun `inntektsmelding ikke håndtert - oppretter oppgave`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()
        sendInntektsmelding(hendelseId = inntektsmeldingHendelseId, dokumentId = inntektsmeldingDokumentId)
        inntektsmeldingIkkeHåndtert(inntektsmeldingId = inntektsmeldingHendelseId)
        assertEquals(1, publiserteOppgaver.size)
        publiserteOppgaver[0].assertInnhold(Opprett, inntektsmeldingDokumentId, Inntektsmelding)
    }

    @Test
    fun `inntektsmelding ikke håndtert med periode innenfor 16 dager - oppretter oppgave på speilkø`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()
        sendInntektsmelding(hendelseId = inntektsmeldingHendelseId, dokumentId = inntektsmeldingDokumentId)
        inntektsmeldingIkkeHåndtert(inntektsmeldingId = inntektsmeldingHendelseId, harPeriodeInnenfor16Dager = true)
        assertEquals(1, publiserteOppgaver.size)
        publiserteOppgaver[0].assertInnhold(OpprettSpeilRelatert, inntektsmeldingDokumentId, Inntektsmelding)
    }

    companion object {

        private val FØDSELSNUMMER = "12345678910"
        private val ORGNUMMER = "ORGNUMMER"

        private val nå = LocalDateTime.now().minusSeconds(1)

        private val OppgaveDTO.timeoutIDager
            get() = Duration.between(nå, this.timeout).toDays()
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

    private fun sendInntektsmelding(
        hendelseId: UUID,
        dokumentId: UUID,
        inntekt: Double = 30000.00,
        refusjon: Double? = inntekt,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNUMMER
    ) {
        rapid.sendTestMessage(
            inntektsmelding(
                hendelseId,
                dokumentId,
                inntekt,
                refusjon,
                fødselsnummer,
                organisasjonsnummer
            )
        )
    }

    private fun sendVedtaksperiodeVenter(hendelseIder: List<UUID>, venterPåHva: String, venterPåHvorfor: String? = null) {
        rapid.sendTestMessage(vedtaksperiodeVenter(hendelseIder, venterPåHva, venterPåHvorfor))
    }

    private fun sendAvsluttetMedVedtak(
        hendelseIder: List<UUID>,
        vedtaksperiodeId: UUID = UUID.randomUUID()
    ) {
        rapid.sendTestMessage(avsluttetMedVedtak(hendelseIder, vedtaksperiodeId))
    }

    private fun sendAvsluttetUtenVedtak(
        hendelseIder: List<UUID>,
        vedtaksperiodeId: UUID = UUID.randomUUID()
    ) {
        rapid.sendTestMessage(avsluttetUtenVedtak(hendelseIder, vedtaksperiodeId))
    }

    private fun sendVedtaksperiodeEndret(
        hendelseIder: List<UUID>,
        tilstand: String,
        vedtaksperiodeId: UUID = UUID.randomUUID()
    ) {
        rapid.sendTestMessage(vedtaksperiodeEndret(hendelseIder, tilstand, vedtaksperiodeId))
    }

    private fun sendInntektsmeldingHåndtert(
        inntektsmeldingId: UUID
    ) {
        rapid.sendTestMessage(inntektsmeldingHåndtert(inntektsmeldingId))
    }

    private fun sendSøknadHåndtert(
        søknadId: UUID
    ) {
        rapid.sendTestMessage(søknadHåndtert(søknadId))
    }

    private fun vedtaksperiodeForkastet(
        hendelseIder: List<UUID>,
        behandletIInfotrygd: Boolean = false,
        harPeriodeInnenfor16Dager: Boolean = false,
        forlengerPeriode: Boolean = false,
        organisasjonsnummer: String = ORGNUMMER,
        fødselsnummer: String = FØDSELSNUMMER,
        forårsaketAv: String = "hva_som_helst"
    ) {
        rapid.sendTestMessage(no.nav.helse.spre.oppgaver.vedtaksperiodeForkastet(hendelseIder, behandletIInfotrygd, harPeriodeInnenfor16Dager, forlengerPeriode, fødselsnummer, organisasjonsnummer, forårsaketAv))
    }


    private fun inntektsmeldingFørSøknad(inntektsmeldingId: UUID, organisasjonsnummer: String = ORGNUMMER, fødselsnummer: String = FØDSELSNUMMER) {
        rapid.sendTestMessage(
            no.nav.helse.spre.oppgaver.inntektsmeldingFørSøknad(
                inntektsmeldingId,
                organisasjonsnummer,
                fødselsnummer
            )
        )
    }

    private fun inntektsmeldingIkkeHåndtert(inntektsmeldingId: UUID, harPeriodeInnenfor16Dager: Boolean = false, organisasjonsnummer: String = ORGNUMMER, fødselsnummer: String = FØDSELSNUMMER) {
        rapid.sendTestMessage(
            no.nav.helse.spre.oppgaver.inntektsmeldingIkkeHåndtert(
                inntektsmeldingId,
                organisasjonsnummer,
                fødselsnummer,
                harPeriodeInnenfor16Dager
            )
        )
    }

}

private fun assertTidsstempel(forventet: LocalDateTime, faktisk: LocalDateTime) = assertEquals(forventet.truncatedTo(SECONDS), faktisk.truncatedTo(SECONDS))

private fun TestRapid.RapidInspector.events(eventnavn: String, hendelseId: UUID) =
    (0.until(size)).map(::message)
        .filter { it["@event_name"].textValue() == eventnavn }
        .filter { it["hendelseId"].textValue() == hendelseId.toString() }



fun vedtaksperiodeVenter(
    hendelseIder: List<UUID>,
    venterPåHva: String,
    venterPåHvorfor: String?
) =
    """{
            "@event_name": "vedtaksperiode_venter",
            "@id": "${UUID.randomUUID()}",
            "hendelser": ${hendelseIder.tilJSONStringArray()},
            "venterPå": {
                "venteårsak": {
                  "hva": "$venterPåHva",
                  ${venterPåHvorfor.hvorfor()}
                }
             }
        }"""

private fun String?.hvorfor(): String? {
    return if (this == null) {
        "\"hvorfor\": ${null} "
    }
    else "\"hvorfor\": \"${this}\""
}

private fun Iterable<Any>.tilJSONStringArray() = joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

@Language("JSON")
fun avsluttetMedVedtak(
    hendelser: List<UUID>,
    vedtaksperiodeId: UUID
) =
    """{
            "@event_name": "avsluttet_med_vedtak",
            "hendelser": ${hendelser.tilJSONStringArray()},
            "vedtaksperiodeId": "$vedtaksperiodeId"
        }"""
@Language("JSON")
fun avsluttetUtenVedtak(
    hendelser: List<UUID>,
    vedtaksperiodeId: UUID
) =
    """{
            "@event_name": "avsluttet_uten_vedtak",
            "hendelser": ${hendelser.tilJSONStringArray()},
            "vedtaksperiodeId": "$vedtaksperiodeId"
        }"""

@Language("JSON")
fun vedtaksperiodeEndret(
    hendelser: List<UUID>,
    gjeldendeTilstand: String,
    vedtaksperiodeId: UUID
) =
    """{
            "@event_name": "vedtaksperiode_endret",
            "hendelser": ${hendelser.tilJSONStringArray()},
            "gjeldendeTilstand": "$gjeldendeTilstand",
            "vedtaksperiodeId": "$vedtaksperiodeId"
        }"""

@Language("JSON")
fun inntektsmeldingHåndtert(
    inntektsmeldingId: UUID
) =
    """{
            "@event_name": "inntektsmelding_håndtert",
            "inntektsmeldingId": "$inntektsmeldingId"
        }"""
@Language("JSON")
fun søknadHåndtert(
    søknadId: UUID
) =
    """{
            "@event_name": "søknad_håndtert",
            "søknadId": "$søknadId"
        }"""


fun vedtaksperiodeForkastet(
    hendelser: List<UUID>,
    behandletIInfotrygd: Boolean,
    harPeriodeInnenfor16Dager: Boolean,
    forlengerPeriode: Boolean,
    fødselsnummer: String,
    organisasjonsnummer: String,
    forårsaketAv: String
) =
    """{
            "@event_name": "vedtaksperiode_forkastet",
            "harPeriodeInnenfor16Dager": "$harPeriodeInnenfor16Dager",
            "forlengerPeriode": "$forlengerPeriode",
            "behandletIInfotrygd": $behandletIInfotrygd,
            "fødselsnummer": "$fødselsnummer",
            "aktørId": "aktør",
            "tilstand": "AVVENTER_INNTEKTSMELDING",
            "vedtaksperiodeId": "${UUID.randomUUID()}",
            "fom": "${LocalDate.now()}",
            "tom": "${LocalDate.now()}",
            "organisasjonsnummer": "$organisasjonsnummer",
            "hendelser": ${hendelser.tilJSONStringArray()},
            "@forårsaket_av": {
                "event_name": "$forårsaketAv"
            }
        }"""


fun inntektsmeldingFørSøknad(
    inntektsmeldingId: UUID,
    organisasjonsnummer: String,
    fødselsnummer: String
) =
    """{
            "@event_name": "inntektsmelding_før_søknad",
            "inntektsmeldingId": "$inntektsmeldingId",
            "organisasjonsnummer": "$organisasjonsnummer",
            "fødselsnummer": "$fødselsnummer",
            "overlappende_sykmeldingsperioder": [
                {
                    "fom":"2018-01-01",
                    "tom":"2018-01-16"
                }
            ]
        }
"""

fun inntektsmeldingIkkeHåndtert(
    inntektsmeldingId: UUID,
    organisasjonsnummer: String,
    fødselsnummer: String,
    harPeriodeInnenfor16Dager: Boolean = false
) =
    """{
            "@event_name": "inntektsmelding_ikke_håndtert",
            "inntektsmeldingId": "$inntektsmeldingId",
            "organisasjonsnummer": "$organisasjonsnummer",
            "harPeriodeInnenfor16Dager" : "$harPeriodeInnenfor16Dager",
            "fødselsnummer": "$fødselsnummer"
        }"""

