package no.nav.helse.spre.oppgaver

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.oppgaver.DokumentTypeDTO.Inntektsmelding
import no.nav.helse.spre.oppgaver.DokumentTypeDTO.Søknad
import no.nav.helse.spre.oppgaver.OppdateringstypeDTO.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndTest {
    private lateinit var embeddedPostgres: EmbeddedPostgres
    private lateinit var hikariConfig: HikariConfig
    private lateinit var dataSource: HikariDataSource
    private lateinit var oppgaveDAO: OppgaveDAO
    private val rapid = TestRapid()
    private var captureslot = mutableListOf<ProducerRecord<String, OppgaveDTO>>()
    private val mockProducer = mockk<KafkaProducer<String, OppgaveDTO>> {
        every { send(capture(captureslot)) } returns mockk()
    }

    @BeforeAll
    fun setup() {
        embeddedPostgres = EmbeddedPostgres.builder().start()

        hikariConfig = HikariConfig().apply {
            this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
            maximumPoolSize = 3
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
        }

        dataSource = HikariDataSource(hikariConfig)

        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()

        oppgaveDAO = OppgaveDAO(dataSource)

        rapid.registerRivers(oppgaveDAO, listOf(OppgaveProducer("et_topic", mockProducer)))
    }

    @BeforeEach
    fun reset() {
        captureslot.clear()
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

        assertOppgave(Utsett, søknad1DokumentId, Søknad, captureslot[0].value())
        assertOppgave(
            Utsett,
            inntektsmeldingDokumentId,
            Inntektsmelding,
            captureslot[1].value()
        )
        assertOppgave(
            Ferdigbehandlet,
            søknad1DokumentId,
            Søknad,
            captureslot[2].value()
        )
        assertOppgave(
            Ferdigbehandlet,
            inntektsmeldingDokumentId,
            Inntektsmelding,
            captureslot[3].value()
        )
        assertEquals(4, captureslot.size)

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

        assertOppgave(Utsett, søknad1DokumentId, Søknad, captureslot[0].value())
        assertOppgave(
            Ferdigbehandlet,
            søknad1DokumentId,
            Søknad,
            captureslot[1].value()
        )
        assertEquals(2, captureslot.size)

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

        assertOppgave(Opprett, søknad1DokumentId, Søknad, captureslot[0].value())
        assertEquals(1, captureslot.size)

        assertEquals(1, rapid.inspektør.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", søknad1HendelseId).size)
    }

    @Test
    fun `spleis gir opp behandling i vilkårsprøving`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
        sendVedtaksperiodeEndret(hendelseIder = listOf(inntektsmeldingHendelseId), tilstand = "AVVENTER_VILKÅRSPRØVING")
        sendVedtaksperiodeEndret(hendelseIder = listOf(inntektsmeldingHendelseId), tilstand = "TIL_INFOTRYGD")

        assertOppgave(
            Utsett,
            inntektsmeldingDokumentId,
            Inntektsmelding,
            captureslot[0].value()
        )
        assertOppgave(
            Opprett,
            inntektsmeldingDokumentId,
            Inntektsmelding,
            captureslot[1].value()
        )

        assertEquals(2, rapid.inspektør.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingHendelseId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", inntektsmeldingHendelseId).size)
    }

    @Test
    fun `tåler meldinger som mangler kritiske felter`() = runBlocking {
        rapid.sendTestMessage("{}")
        assertTrue(captureslot.isEmpty())
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `ignorerer endrede vedtaksperioder uten tidligere dokumenter`() {
        val inntektsmeldingHendelseId = UUID.randomUUID()
        sendVedtaksperiodeEndret(hendelseIder = listOf(inntektsmeldingHendelseId), tilstand = "AVVENTER_VILKÅRSPRØVING")

        assertTrue(captureslot.isEmpty())
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

        assertTrue(captureslot.isEmpty())
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

        assertOppgave(
            Utsett,
            inntektsmeldingDokumentId,
            Inntektsmelding,
            captureslot[0].value()
        )
        assertOppgave(
            Utsett,
            inntektsmeldingDokumentId,
            Inntektsmelding,
            captureslot[1].value()
        )
        assertOppgave(
            Ferdigbehandlet,
            søknadDokumentId,
            Søknad,
            captureslot[2].value()
        )

        assertEquals(3, captureslot.size)
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
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVVENTER_HISTORIKK",
            vedtaksperiodeId = periode1
        )
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(inntektsmeldingHendelseId),
            tilstand = "AVSLUTTET_UTEN_UTBETALING",
            vedtaksperiodeId = periode1
        )

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

        assertEquals(3, captureslot.size)

        assertOppgave(Utsett, inntektsmeldingDokumentId, Inntektsmelding, captureslot[0].value())
        assertOppgave(Utsett, inntektsmeldingDokumentId, Inntektsmelding, captureslot[1].value())
        assertOppgave(Opprett, inntektsmeldingDokumentId, Inntektsmelding, captureslot[2].value())
    }

    @Test
    fun `Sender ikke flere opprett-meldinger hvis vi allerede har forkastet en periode`() {
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
            tilstand = "AVSLUTTET_UTEN_UTBETALING",
            vedtaksperiodeId = periode1
        )

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

        assertEquals(3, captureslot.size)
        assertEquals(2, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingHendelseId).size)
        assertEquals(Utsett, captureslot[0].value().oppdateringstype)
        assertEquals(Utsett, captureslot[1].value().oppdateringstype)

        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", inntektsmeldingHendelseId).size)
        assertEquals(Opprett, captureslot[2].value().oppdateringstype)
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

        assertEquals(2, captureslot.size)
        assertEquals(Utsett, captureslot[0].value().oppdateringstype)
        assertEquals(Ferdigbehandlet, captureslot[1].value().oppdateringstype)
    }

    @Test
    fun `to korte og en lang periode hvor siste går til infotrygd`() {
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
            tilstand = "AVSLUTTET_UTEN_UTBETALING",
            vedtaksperiodeId = periode1
        )

        sendInntektsmelding(inntektsmeldingHendelseId, inntektsmeldingDokumentId)
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

        assertEquals(3, rapid.inspektør.size)
        assertEquals(3, captureslot.size)
        assertEquals(Utsett, captureslot[0].value().oppdateringstype)
        assertEquals(Utsett, captureslot[1].value().oppdateringstype)
        assertEquals(Opprett, captureslot[2].value().oppdateringstype)
    }

    @Test
    fun `utsetter oppgave for inntektsmelding som treffer perioden i AVSLUTTET_UTEN_UTBETALING`() {
        val periode = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        sendSøknad(søknadId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadId),
            tilstand = "AVSLUTTET_UTEN_UTBETALING",
            vedtaksperiodeId = periode
        )
        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId).size)

        sendInntektsmelding(inntektsmeldingId, UUID.randomUUID())
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadId, inntektsmeldingId),
            tilstand = "AVSLUTTET_UTEN_UTBETALING",
            vedtaksperiodeId = periode
        )

        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingId).size)
        assertEquals(2, captureslot.size)
        assertEquals(Ferdigbehandlet, captureslot[0].value().oppdateringstype)
        assertEquals(Utsett, captureslot[1].value().oppdateringstype)
    }

    @Test
    fun `utsetter oppgaver for inntektsmelding som ikke validerer der den treffer en periode i AVSLUTTET_UTEN_UTBETALING`() {
        val periode = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        sendSøknad(søknadId)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadId),
            tilstand = "AVSLUTTET_UTEN_UTBETALING",
            vedtaksperiodeId = periode
        )

        sendInntektsmelding(inntektsmeldingId, UUID.randomUUID())
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadId, inntektsmeldingId),
            tilstand = "TIL_INFOTRYGD",
            vedtaksperiodeId = periode
        )

        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", inntektsmeldingId).size)
        assertEquals(2, captureslot.size)
        assertEquals(Ferdigbehandlet, captureslot[0].value().oppdateringstype)
        assertEquals(Opprett, captureslot[1].value().oppdateringstype)
    }

    @Test
    fun `mottar hendelse_ikke_håndtert uten at hendelsen er tidligere lest`() {
        val hendelseId = UUID.randomUUID()
        sendHendelseIkkeHåndtert(hendelseId)
        assertTrue(captureslot.isEmpty())
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `spleis håndterer ikke søknad og vi mottar aldri vedtaksperiode_endret`() {
        val søknadId = UUID.randomUUID()
        val hendelseId = UUID.randomUUID()
        sendSøknad(hendelseId, søknadId)
        sendHendelseIkkeHåndtert(hendelseId)

        assertEquals(1, captureslot.size)
        assertEquals(søknadId, captureslot[0].value().dokumentId)
        assertEquals(Opprett, captureslot[0].value().oppdateringstype)

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
        assertEquals(1, captureslot.size)
        assertEquals(søknadId, captureslot[0].value().dokumentId)
        assertEquals(Opprett, captureslot[0].value().oppdateringstype)
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
        assertEquals(2, captureslot.size)
        assertEquals(søknadId, captureslot[0].value().dokumentId)
        assertEquals(Utsett, captureslot[0].value().oppdateringstype)
        assertEquals(søknadId, captureslot[1].value().dokumentId)
        assertEquals(Opprett, captureslot[1].value().oppdateringstype)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", hendelseId).size)
    }

    @Test
    fun `spleis håndterer ikke søknad og sier ifra flere ganger`() {
        val søknadId = UUID.randomUUID()
        val hendelseId = UUID.randomUUID()

        sendSøknad(hendelseId, søknadId)
        sendHendelseIkkeHåndtert(hendelseId)
        sendHendelseIkkeHåndtert(hendelseId)

        assertEquals(1, captureslot.size)
        assertEquals(søknadId, captureslot[0].value().dokumentId)
        assertEquals(Opprett, captureslot[0].value().oppdateringstype)
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

        sendSøknad(søknadId2)
        sendVedtaksperiodeEndret(
            hendelseIder = listOf(søknadId2, inntektsmeldingId),
            tilstand = "TIL_INFOTRYGD",
            vedtaksperiodeId = periode
        )

        assertEquals(4, captureslot.size)
        assertEquals(Ferdigbehandlet, captureslot[0].value().oppdateringstype)
        assertEquals(Utsett, captureslot[1].value().oppdateringstype)
        assertEquals(Opprett, captureslot[2].value().oppdateringstype)
        assertEquals(Opprett, captureslot[3].value().oppdateringstype)
        assertEquals(4, rapid.inspektør.size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_kort_periode", søknadId1).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_utsatt", inntektsmeldingId).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", søknadId2).size)
        assertEquals(1, rapid.inspektør.events("oppgavestyring_opprett", inntektsmeldingId).size)
    }

    private fun assertOppgave(
        oppdateringstypeDTO: OppdateringstypeDTO,
        dokumentId: UUID,
        dokumentType: DokumentTypeDTO,
        oppgaveDTO: OppgaveDTO
    ) {
        assertEquals(dokumentId, oppgaveDTO.dokumentId)
        assertEquals(dokumentType, oppgaveDTO.dokumentType)
        assertEquals(oppdateringstypeDTO, oppgaveDTO.oppdateringstype)
    }

    private fun sendSøknad(hendelseId: UUID, dokumentId: UUID = UUID.randomUUID()) {
        rapid.sendTestMessage(sendtSøknad(hendelseId, dokumentId))
    }

    private fun sendArbeidsgiversøknad(hendelseId: UUID, dokumentId: UUID = UUID.randomUUID()) {
        rapid.sendTestMessage(sendtArbeidsgiversøknad(hendelseId, dokumentId))
    }

    private fun sendInntektsmelding(hendelseId: UUID, dokumentId: UUID) {
        rapid.sendTestMessage(inntektsmelding(hendelseId, dokumentId))
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


fun hendelseIkkeHåndtert(
    hendelseId: UUID,
) = """{
            "@event_name": "hendelse_ikke_håndtert",
            "hendelseId": "$hendelseId"
        }"""
