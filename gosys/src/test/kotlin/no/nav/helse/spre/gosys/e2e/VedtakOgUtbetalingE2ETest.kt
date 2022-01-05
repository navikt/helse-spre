package no.nav.helse.spre.gosys.e2e

import io.ktor.util.*
import no.nav.helse.spre.gosys.e2e.AbstractE2ETest.Utbetalingstype.REVURDERING
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtbetaltRiver
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtenUtbetalingRiver
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayloadV2
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayloadV2.*
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetRiver
import no.nav.helse.spre.testhelpers.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@KtorExperimentalAPI
internal class VedtakOgUtbetalingE2ETest : AbstractE2ETest() {

    private val vedtakFattetDao = VedtakFattetDao(dataSource)
    private val utbetalingDao = UtbetalingDao(dataSource)

    init {
        VedtakFattetRiver(testRapid, vedtakFattetDao, utbetalingDao, duplikatsjekkDao, vedtakMediator)
        UtbetalingUtbetaltRiver(testRapid, utbetalingDao, vedtakFattetDao, duplikatsjekkDao, vedtakMediator)
        UtbetalingUtenUtbetalingRiver(testRapid, utbetalingDao, vedtakFattetDao, duplikatsjekkDao, vedtakMediator)
    }

    companion object {
        fun LocalDate.formatted(): String = format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }

    @Test
    fun `journalfører vedtak med vedtak_fattet og deretter utbetaling_utbetalt`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendVedtakFattet(
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId
        )
        sendUtbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId)
        )
        assertJournalpost()
        assertVedtakPdf(expectedPdfPayloadV2(arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver")))
    }


    @Test
    fun `journalfører vedtak med vedtak_fattet og deretter utbetaling_utbetalt for brukerutbetaling`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendVedtakFattet(
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId
        )
        sendBrukerutbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId)
        )
        assertJournalpost()

        val linjer = listOf(
            Linje(
                fom = 1.januar,
                tom = 31.januar,
                grad = 100,
                dagsats = 1431,
                mottaker = "123456 78910",
                mottakerType = MottakerType.Person,
                totalbeløp = 32913,
                erOpphørt = false
            )
        )
        assertVedtakPdf(
            expectedPdfPayloadV2(
                linjer = linjer,
                personOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdPerson")
            )
        )
    }

    @Test
    fun `delvis refusjon`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendVedtakFattet(
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId
        )
        sendUtbetalingDelvisRefusjon(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId),
            sykdomstidslinje = utbetalingsdager(1.januar, 31.januar)
        )

        val expectedLinjer = listOf(
            Linje(
                fom = 1.januar,
                tom = 31.januar,
                grad = 100,
                dagsats = 741,
                mottaker = "123 456 789",
                mottakerType = MottakerType.Arbeidsgiver,
                totalbeløp = 17043,
                erOpphørt = false
            ),
            Linje(
                fom = 1.januar,
                tom = 31.januar,
                grad = 100,
                dagsats = 700,
                mottaker = "123456 78910",
                mottakerType = MottakerType.Person,
                totalbeløp = 16100,
                erOpphørt = false
            )
        )

        assertJournalpost()
        assertVedtakPdf(
            expectedPdfPayloadV2(
                linjer = expectedLinjer,
                totaltTilUtbetaling = 33143,
                personOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdPerson"),
                arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver")
            )
        )
    }

    @Test
    fun `sorterer linjer`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendVedtakFattet(
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId
        )
        sendBrukerutbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId),
            sykdomstidslinje = utbetalingsdager(1.januar, 31.januar)
                    + arbeidsdager(1.februar, 7.februar)
                    + utbetalingsdager(8.februar, 18.februar)
        )
        assertJournalpost()

        val expectedLinjer = listOf(
            Linje(
                fom = 8.februar,
                tom = 18.februar,
                grad = 100,
                dagsats = 1431,
                mottaker = "123456 78910",
                mottakerType = MottakerType.Person,
                totalbeløp = 10017,
                erOpphørt = false
            ),
            Linje(
                fom = 1.januar,
                tom = 31.januar,
                grad = 100,
                dagsats = 1431,
                mottaker = "123456 78910",
                mottakerType = MottakerType.Person,
                totalbeløp = 32913,
                erOpphørt = false
            )
        )

        assertVedtakPdf(
            expectedPdfPayloadV2(
                linjer = expectedLinjer,
                totaltTilUtbetaling = 42930,
                behandlingsdato = 18.februar, //TODO: Bruk `vedtakFattetTidspunkt` på vedtaket
                personOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdPerson"),
                ikkeUtbetalteDager = listOf(
                    IkkeUtbetalteDager(
                        1.februar,
                        7.februar,
                        "Arbeidsdag",
                        emptyList()
                    )
                )
            )
        )
    }


    @Test
    fun `journalfører vedtak med vedtak_fattet og deretter utbetaling_uten_utbetaling for brukerutbetaling`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendVedtakFattet(
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId
        )
        sendBrukerutbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId),
            sykdomstidslinje = fridager(1.januar, 31.januar)
        )
        assertJournalpost()
        assertVedtakPdf(
            expectedPdfPayloadV2(
                linjer = emptyList(),
                totaltTilUtbetaling = 0,
                ikkeUtbetalteDager = listOf(
                    IkkeUtbetalteDager(
                        1.januar,
                        31.januar,
                        "Ferie/Permisjon",
                        emptyList()
                    )
                )
            )
        )
    }

    @Test
    fun `journalfører vedtak med vedtak_fattet og deretter utbetaling_uten_utbetaling`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendVedtakFattet(vedtaksperiodeId = vedtaksperiodeId, utbetalingId = utbetalingId)
        sendUtbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId),
            sykdomstidslinje = fridager(1.januar, 31.januar)
        )
        assertJournalpost()
        assertVedtakPdf(
            expectedPdfPayloadV2(
                totaltTilUtbetaling = 0,
                linjer = emptyList(),
                ikkeUtbetalteDager = listOf(
                    IkkeUtbetalteDager(
                        fom = 1.januar,
                        tom = 31.januar,
                        grunn = "Ferie/Permisjon",
                        begrunnelser = emptyList()
                    )
                )
            )
        )
    }

    @Test
    fun `journalfører vedtak med utbetaling_utbetalt og deretter vedtak_fattet`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendUtbetaling(utbetalingId = utbetalingId, vedtaksperiodeIder = listOf(vedtaksperiodeId))
        sendVedtakFattet(utbetalingId = utbetalingId, vedtaksperiodeId = vedtaksperiodeId)

        assertEquals(1, capturedJoarkRequests.size)
        assertJournalpost()
        assertVedtakPdf(expectedPdfPayloadV2(arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver")))
    }

    @Test
    fun `journalfører ikke dobbelt vedtak`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        val hendelseIdVedtak = UUID.randomUUID()
        val hendelseIdUtbetaling = UUID.randomUUID()
        sendVedtakFattet(
            hendelseId = hendelseIdVedtak,
            utbetalingId = utbetalingId,
            vedtaksperiodeId = vedtaksperiodeId
        )
        sendUtbetaling(
            hendelseId = hendelseIdUtbetaling,
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId)
        )

        sendUtbetaling(
            hendelseId = hendelseIdUtbetaling,
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId)
        )
        sendVedtakFattet(
            hendelseId = hendelseIdVedtak,
            utbetalingId = utbetalingId,
            vedtaksperiodeId = vedtaksperiodeId
        )
        assertEquals(1, capturedJoarkRequests.size)
        assertJournalpost()
        assertVedtakPdf(expectedPdfPayloadV2(arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver")))
    }

    @Test
    fun `journalfører ikke uten å ha mottatt utbetaling`() {
        sendVedtakFattet()
        assertEquals(0, capturedJoarkRequests.size)
        assertEquals(0, capturedPdfRequests.size)
    }

    @Test
    fun `journalfører ikke uten å ha mottatt vedtak_fattet`() {
        sendUtbetaling()
        assertEquals(0, capturedJoarkRequests.size)
        assertEquals(0, capturedPdfRequests.size)
    }

    @Test
    fun `journalfører ikke vedtak uten utbetalingId`() {
        sendVedtakFattet(utbetalingId = null)
        sendUtbetaling()
        assertEquals(0, capturedJoarkRequests.size)
        assertEquals(0, capturedPdfRequests.size)
    }

    @Test
    fun `tar med arbeidsdager og kollapser over inneklemte fridager`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        val sykdomstidslinje =
            utbetalingsdager(1.januar, 17.januar) +
                    arbeidsdager(18.januar) +
                    fridager(19.januar) +
                    feriedager(20.januar) +
                    permisjonsdager(21.januar) +
                    arbeidsdager(22.januar)
        sendUtbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId),
            sykdomstidslinje = sykdomstidslinje
        )
        sendVedtakFattet(
            utbetalingId = utbetalingId,
            vedtaksperiodeId = vedtaksperiodeId,
            sykdomstidslinje = sykdomstidslinje
        )
        assertJournalpost(expectedJournalpost(1.januar, 22.januar))

        assertVedtakPdf(
            expectedPdfPayloadV2(
                fom = 1.januar,
                tom = 22.januar,
                totaltTilUtbetaling = 18603,
                arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver"),
                ikkeUtbetalteDager = listOf(
                    IkkeUtbetalteDager(
                        fom = 18.januar,
                        tom = 22.januar,
                        grunn = "Arbeidsdag",
                        begrunnelser = emptyList()
                    )
                ),
                linjer = listOf(
                    Linje(
                        fom = 1.januar,
                        tom = 17.januar,
                        grad = 100,
                        dagsats = 1431,
                        mottaker = "123 456 789",
                        mottakerType = MottakerType.Arbeidsgiver,
                        totalbeløp = 18603,
                        erOpphørt = false
                    )
                )
            )
        )
    }

    @Test
    fun `en avvist dag med begrunnelse revurdering`() {
        val utbetalingId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        val sykdomstidslinje =
            utbetalingsdager(1.januar, 16.januar) +
                    avvistDager(17.januar, begrunnelser = listOf("EtterDødsdato"))
        sendVedtakFattet(
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId,
            sykdomstidslinje = sykdomstidslinje
        )
        sendUtbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId),
            sykdomstidslinje = sykdomstidslinje,
            type = "REVURDERING"
        )
        assertJournalpost(
            expected = expectedJournalpost(
                journalpostTittel = "Vedtak om revurdering av sykepenger",
                dokumentTittel = "Sykepenger revurdert i ny løsning, 01.01.2018 - 17.01.2018"
            )
        )
        assertVedtakPdf(
            expectedPdfPayloadV2(
                utbetalingstype = REVURDERING,
                fom = 1.januar,
                tom = 17.januar,
                totaltTilUtbetaling = 17172,
                arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver"),
                ikkeUtbetalteDager = listOf(
                    IkkeUtbetalteDager(
                        fom = 17.januar,
                        tom = 17.januar,
                        grunn = "Avvist dag",
                        begrunnelser = listOf("Personen er død")
                    )
                ),
                linjer = listOf(
                    Linje(
                        fom = 1.januar,
                        tom = 16.januar,
                        grad = 100,
                        dagsats = 1431,
                        mottaker = "123 456 789",
                        mottakerType = MottakerType.Arbeidsgiver,
                        totalbeløp = 17172,
                        erOpphørt = false
                    )
                )
            )
        )
    }

    @Test
    fun `arbeidsdager før skjæringstidspunkt`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        val sykdomstidslinje = utbetalingsdager(2.januar, 31.januar)
        sendVedtakFattet(
            utbetalingId = utbetalingId,
            vedtaksperiodeId = vedtaksperiodeId,
            sykdomstidslinje = sykdomstidslinje
        )
        sendUtbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId),
            sykdomstidslinje = arbeidsdager(1.januar) + sykdomstidslinje
        )

        assertJournalpost(expectedJournalpost(2.januar, 31.januar))


        assertVedtakPdf(expectedPdfPayloadV2(
            fom = 2.januar,
            tom = 31.januar,
            totaltTilUtbetaling = 31482,
            ikkeUtbetalteDager = emptyList(),
            arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver")
        ))
    }

    @Test
    fun `vedtak og utbetaling som linkes med ulik fnr`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendVedtakFattet(vedtaksperiodeId = vedtaksperiodeId, utbetalingId = utbetalingId, fødselsnummer = "123")
        assertThrows<IllegalStateException> {
            sendUtbetaling(
                fødselsnummer = "321",
                utbetalingId = utbetalingId,
                vedtaksperiodeIder = listOf(vedtaksperiodeId)
            )
        }
    }

    @Test
    fun `revurdering med flere vedtak knyttet til én utbetaling - utbetaling først`() {
        val utbetalingstidspunkt = LocalDateTime.now()
        val utbetalingId = UUID.randomUUID()
        val (v1, v2) = UUID.randomUUID() to UUID.randomUUID()
        val (p1, p2) = utbetalingsdager(1.januar, 31.januar) to utbetalingsdager(1.februar, 28.februar)
        sendRevurdering(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(v1, v2),
            sykdomstidslinje = p1 + p2,
            opprettet = utbetalingstidspunkt
        )
        sendVedtakFattet(
            utbetalingId = utbetalingId,
            vedtaksperiodeId = v1,
            sykdomstidslinje = p1
        )
        sendVedtakFattet(
            utbetalingId = utbetalingId,
            vedtaksperiodeId = v2,
            sykdomstidslinje = p2
        )

        assertEquals(1, capturedJoarkRequests.size)
        assertEquals(1, capturedPdfRequests.size)

        assertVedtakPdf(
            expectedPdfPayloadV2(
                fom = 1.januar,
                tom = 28.februar,
                utbetalingstype = REVURDERING,
                totaltTilUtbetaling = 61533,
                behandlingsdato = utbetalingstidspunkt.toLocalDate(),
                arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver")
            )
        )
        assertJournalpost(expectedJournalpost(fom = 1.januar, tom = 28.februar, utbetalingstype = REVURDERING))
    }

    @Test
    fun `revurdering med flere vedtak knyttet til én utbetaling - vedtak først`() {
        val utbetalingId = UUID.randomUUID()
        val (v1, v2) = UUID.randomUUID() to UUID.randomUUID()
        val (p1, p2) = utbetalingsdager(1.januar, 31.januar) to utbetalingsdager(1.februar, 28.februar)

        sendVedtakFattet(
            utbetalingId = utbetalingId,
            vedtaksperiodeId = v1,
            sykdomstidslinje = p1
        )
        sendRevurdering(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(v1, v2),
            sykdomstidslinje = p1 + p2
        )
        sendVedtakFattet(
            utbetalingId = utbetalingId,
            vedtaksperiodeId = v2,
            sykdomstidslinje = p2
        )

        assertEquals(1, capturedJoarkRequests.size)
        assertEquals(1, capturedPdfRequests.size)

        assertVedtakPdf(
            expectedPdfPayloadV2(
                utbetalingstype = REVURDERING,
                totaltTilUtbetaling = 61533,
                fom = 1.januar,
                tom = 28.februar,
                arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver")
            )
        )
    }

    @Test
    fun `revurdering med flere vedtak knyttet til én utbetaling - utbetaling sist`() {
        val utbetalingId = UUID.randomUUID()
        val (v1, v2) = UUID.randomUUID() to UUID.randomUUID()
        val (p1, p2) = utbetalingsdager(1.januar, 31.januar) to utbetalingsdager(1.februar, 28.februar)
        sendVedtakFattet(
            vedtaksperiodeId = v1,
            utbetalingId = utbetalingId,
            sykdomstidslinje = p1
        )
        sendVedtakFattet(
            vedtaksperiodeId = v2,
            utbetalingId = utbetalingId,
            sykdomstidslinje = p2
        )
        sendRevurdering(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(v1, v2),
            sykdomstidslinje = p1 + p2
        )

        assertEquals(1, capturedJoarkRequests.size)
        assertEquals(1, capturedPdfRequests.size)

        assertVedtakPdf(
            expectedPdfPayloadV2(
                utbetalingstype = REVURDERING,
                fom = 1.januar,
                tom = 28.februar,
                totaltTilUtbetaling = 61533,
                arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver")
            )
        )
    }

    @Test
    fun `revurdering med flere vedtak knyttet til én utbetaling - inneklemt fridag`() {
        val utbetalingstidspunkt = LocalDateTime.now()
        val utbetalingId = UUID.randomUUID()
        val (v1, v2) = UUID.randomUUID() to UUID.randomUUID()
        val (p1, p2) =
            utbetalingsdager(1.januar, 28.januar) +
                    fridager(29.januar) +
                    utbetalingsdager(30.januar, 31.januar) to
                    utbetalingsdager(1.februar, 28.februar)
        sendRevurdering(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(v1, v2),
            sykdomstidslinje = p1 + p2,
            opprettet = utbetalingstidspunkt
        )
        sendVedtakFattet(
            vedtaksperiodeId = v1,
            utbetalingId = utbetalingId,
            sykdomstidslinje = p1
        )
        sendVedtakFattet(
            vedtaksperiodeId = v2,
            utbetalingId = utbetalingId,
            sykdomstidslinje = p2
        )

        assertEquals(1, capturedJoarkRequests.size)
        assertEquals(1, capturedPdfRequests.size)

        assertVedtakPdf(
            expectedPdfPayloadV2(
                fom = 1.januar,
                tom = 28.februar,
                utbetalingstype = REVURDERING,
                totaltTilUtbetaling = 60102,
                behandlingsdato = utbetalingstidspunkt.toLocalDate(),
                arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver"),
                linjer = listOf(
                    Linje(
                        fom = 30.januar,
                        tom = 28.februar,
                        grad = 100,
                        dagsats = 1431,
                        mottaker = "123 456 789",
                        erOpphørt = false,
                        totalbeløp = 31482
                    ),
                    Linje(
                        fom = 1.januar,
                        tom = 28.januar,
                        grad = 100,
                        dagsats = 1431,
                        mottaker = "123 456 789",
                        erOpphørt = false,
                        totalbeløp = 28620
                    )
                ),
                ikkeUtbetalteDager = listOf(
                    IkkeUtbetalteDager(
                        fom = 29.januar,
                        tom = 29.januar,
                        grunn = "Ferie/Permisjon",
                        begrunnelser = emptyList()
                    )
                )
            )
        )
        assertJournalpost(expectedJournalpost(fom = 1.januar, tom = 28.februar, utbetalingstype = REVURDERING))
    }

    @Test
    fun `markerer linjer utbetaling_utbetalt som er opphørt`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendVedtakFattet(
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId,
            sykdomstidslinje = utbetalingsdager(6.november(2021), 19.november(2021))
        )
        testRapid.sendTestMessage(utbetalingMedOpphør(vedtaksperiodeId.toString(), utbetalingId.toString()))

        assertJournalpost(
            expectedJournalpost(
                journalpostTittel = "Vedtak om revurdering av sykepenger",
                dokumentTittel = "Sykepenger revurdert i ny løsning, 06.11.2021 - 19.11.2021",
                fom = 6.november(2021),
                tom = 19.november(2021)
            )
        )
        assertVedtakPdf(
            expectedPdfPayloadV2(
                fom = 6.november(2021),
                tom = 19.november(2021),
                totaltTilUtbetaling = -2500,
                maksdato = 19.oktober(2022),
                utbetalingstype = REVURDERING,
                behandlingsdato = 2.desember(2021),
                dagerIgjen = 238,
                godkjentAv = "K123456",
                arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver"),
                linjer = listOf(
                    Linje(
                        fom = 15.november(2021),
                        tom = 19.november(2021),
                        grad = 60,
                        dagsats = 700,
                        mottaker = "123 456 789",
                        mottakerType = MottakerType.Arbeidsgiver,
                        erOpphørt = false,
                        totalbeløp = 3900
                    ),
                    Linje(
                        fom = 8.november(2021),
                        tom = 12.november(2021),
                        grad = 60,
                        dagsats = 700,
                        mottaker = "123 456 789",
                        mottakerType = MottakerType.Arbeidsgiver,
                        erOpphørt = false,
                        totalbeløp = 3900
                    ),
                    Linje(
                        fom = 6.november(2021),
                        tom = 19.november(2021),
                        grad = 80,
                        dagsats = 1000,
                        mottaker = "123 456 789",
                        mottakerType = MottakerType.Arbeidsgiver,
                        erOpphørt = true,
                        totalbeløp = 0
                    )
                )
            )
        )
    }

    @Test
    fun `markerer linjer utbetaling_utbetalt som er opphørt i PDFPayloadV2`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendVedtakFattet(
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId,
            sykdomstidslinje = utbetalingsdager(6.november(2021), 19.november(2021))
        )
        testRapid.sendTestMessage(utbetalingMedOpphør(vedtaksperiodeId.toString(), utbetalingId.toString()))

        assertJournalpost(
            expectedJournalpost(
                journalpostTittel = "Vedtak om revurdering av sykepenger",
                dokumentTittel = "Sykepenger revurdert i ny løsning, 06.11.2021 - 19.11.2021",
                fom = 6.november(2021),
                tom = 19.november(2021)
            )
        )
        assertVedtakPdf(
            expectedPdfPayloadV2(
                fom = 6.november(2021),
                tom = 19.november(2021),
                totaltTilUtbetaling = -2500,
                maksdato = 19.oktober(2022),
                utbetalingstype = REVURDERING,
                behandlingsdato = 2.desember(2021),
                dagerIgjen = 238,
                godkjentAv = "K123456",
                arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver"),
                linjer = listOf(
                    Linje(
                        dagsats = 700,
                        fom = 15.november(2021),
                        tom = 19.november(2021),
                        grad = 60,
                        totalbeløp = 3900,
                        mottaker = "123 456 789",
                        mottakerType = MottakerType.Arbeidsgiver,
                        erOpphørt = false
                    ),
                    Linje(
                        dagsats = 700,
                        fom = 8.november(2021),
                        tom = 12.november(2021),
                        grad = 60,
                        totalbeløp = 3900,
                        mottaker = "123 456 789",
                        mottakerType = MottakerType.Arbeidsgiver,
                        erOpphørt = false
                    ),
                    Linje(
                        dagsats = 1000,
                        fom = 6.november(2021),
                        tom = 19.november(2021),
                        grad = 80,
                        totalbeløp = 0,
                        mottaker = "123 456 789",
                        mottakerType = MottakerType.Arbeidsgiver,
                        erOpphørt = true
                    )
                )
            )
        )
    }

    @Language("JSON")
    fun utbetalingMedOpphør(vedtaksperiodeId: String, utbetalingId: String) = """
        {
          "utbetalingId": "$utbetalingId",
          "korrelasjonsId": "4A6C8E3C-22DB-4B73-BB92-327BC4E50F6D",
          "type": "REVURDERING",
          "fom": "2021-11-06",
          "tom": "2021-11-19",
          "maksdato": "2022-10-19",
          "forbrukteSykedager": 10,
          "gjenståendeSykedager": 238,
          "stønadsdager": 10,
          "ident": "K123456",
          "epost": "saksbehandler@nav.no",
          "tidspunkt": "2021-12-02T08:00:44",
          "automatiskBehandling": false,
          "arbeidsgiverOppdrag": {
            "mottaker": "123456789",
            "fagområde": "SPREF",
            "linjer": [
              {
                "fom": "2021-11-06",
                "tom": "2021-11-19",
                "satstype": "DAG",
                "sats": 1000,
                "dagsats": 1000,
                "lønn": 1500,
                "grad": 80.0,
                "stønadsdager": 0,
                "totalbeløp": 0,
                "endringskode": "ENDR",
                "delytelseId": 1,
                "refDelytelseId": null,
                "refFagsystemId": null,
                "statuskode": "OPPH",
                "datoStatusFom": "2021-11-06",
                "klassekode": "SPREFAG-IOP"
              },
              {
                "fom": "2021-11-08",
                "tom": "2021-11-12",
                "satstype": "DAG",
                "sats": 700,
                "dagsats": 700,
                "lønn": 1500,
                "grad": 60.0,
                "stønadsdager": 5,
                "totalbeløp": 3900,
                "endringskode": "NY",
                "delytelseId": 2,
                "refDelytelseId": 1,
                "refFagsystemId": "44DZ446C52EYP5NSBTKSDCJRLX",
                "statuskode": null,
                "datoStatusFom": null,
                "klassekode": "SPREFAG-IOP"
              },
              {
                "fom": "2021-11-15",
                "tom": "2021-11-19",
                "satstype": "DAG",
                "sats": 700,
                "dagsats": 700,
                "lønn": 1500,
                "grad": 60.0,
                "stønadsdager": 5,
                "totalbeløp": 3900,
                "endringskode": "NY",
                "delytelseId": 3,
                "refDelytelseId": 2,
                "refFagsystemId": "9WUTHBNERC2L5CEQKZCN568L6P",
                "statuskode": null,
                "datoStatusFom": null,
                "klassekode": "SPREFAG-IOP"
              }
            ],
            "fagsystemId": "fagsystemIdArbeidsgiver",
            "endringskode": "ENDR",
            "sisteArbeidsgiverdag": "2021-11-05",
            "tidsstempel": "2021-12-02T10:35:88",
            "nettoBeløp": -2500,
            "stønadsdager": 10,
            "avstemmingsnøkkel": "1234567890123456234",
            "status": "AKSEPTERT",
            "overføringstidspunkt": "2021-12-02T11:00:37",
            "fom": "2021-11-06",
            "tom": "2021-11-19",
            "simuleringsResultat": {
              "totalbeløp": -2500,
              "perioder": [
                {
                  "fom": "2021-11-06",
                  "tom": "2021-11-19",
                  "utbetalinger": [
                    {
                      "forfallsdato": "2021-12-02",
                      "utbetalesTil": {
                        "id": "123456789",
                        "navn": "TEST ORG AS"
                      },
                      "feilkonto": false,
                      "detaljer": [
                        {
                          "fom": "2021-11-06",
                          "tom": "2021-11-19",
                          "konto": "1111111",
                          "beløp": -10000,
                          "klassekode": {
                            "kode": "SPREFAG-IOP",
                            "beskrivelse": "Sykepenger, Refusjon arbeidsgiver"
                          },
                          "uføregrad": 80,
                          "utbetalingstype": "YTEL",
                          "tilbakeføring": true,
                          "sats": {
                            "sats": 0,
                            "antall": 0,
                            "type": ""
                          },
                          "refunderesOrgnummer": "123456789"
                        }
                      ]
                    }
                  ]
                },
                {
                  "fom": "2021-11-08",
                  "tom": "2021-11-12",
                  "utbetalinger": [
                    {
                      "forfallsdato": "2021-12-02",
                      "utbetalesTil": {
                        "id": "123456789",
                        "navn": "TEST ORG AS"
                      },
                      "feilkonto": false,
                      "detaljer": [
                        {
                          "fom": "2021-11-08",
                          "tom": "2021-11-12",
                          "konto": "1111111",
                          "beløp": 3900,
                          "klassekode": {
                            "kode": "SPREFAG-IOP",
                            "beskrivelse": "Sykepenger, Refusjon arbeidsgiver"
                          },
                          "uføregrad": 60,
                          "utbetalingstype": "YTEL",
                          "tilbakeføring": false,
                          "sats": {
                            "sats": 700,
                            "antall": 5,
                            "type": "DAG"
                          },
                          "refunderesOrgnummer": "123456789"
                        }
                      ]
                    }
                  ]
                },
                {
                  "fom": "2021-11-15",
                  "tom": "2021-11-19",
                  "utbetalinger": [
                    {
                      "forfallsdato": "2021-12-02",
                      "utbetalesTil": {
                        "id": "123456789",
                        "navn": "TEST ORG AS"
                      },
                      "feilkonto": false,
                      "detaljer": [
                        {
                          "fom": "2021-11-15",
                          "tom": "2021-11-19",
                          "konto": "1111111",
                          "beløp": 3900,
                          "klassekode": {
                            "kode": "SPREFAG-IOP",
                            "beskrivelse": "Sykepenger, Refusjon arbeidsgiver"
                          },
                          "uføregrad": 60,
                          "utbetalingstype": "YTEL",
                          "tilbakeføring": false,
                          "sats": {
                            "sats": 700,
                            "antall": 5,
                            "type": "DAG"
                          },
                          "refunderesOrgnummer": "123456789"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
          },
          "personOppdrag": {
            "mottaker": "12345678910",
            "fagområde": "SP",
            "linjer": [],
            "fagsystemId": "fagsystemIdPerson",
            "endringskode": "NY",
            "sisteArbeidsgiverdag": "2021-11-05",
            "tidsstempel": "2021-12-02T07:35:00",
            "nettoBeløp": 0,
            "stønadsdager": 0,
            "avstemmingsnøkkel": null,
            "status": null,
            "overføringstidspunkt": null,
            "fom": "-999999999-01-01",
            "tom": "-999999999-01-01",
            "simuleringsResultat": null
          },
          "utbetalingsdager": [
            {
              "dato": "2021-10-26",
              "type": "ArbeidsgiverperiodeDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-10-27",
              "type": "ArbeidsgiverperiodeDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-10-28",
              "type": "ArbeidsgiverperiodeDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-10-29",
              "type": "ArbeidsgiverperiodeDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-10-30",
              "type": "ArbeidsgiverperiodeDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-10-31",
              "type": "ArbeidsgiverperiodeDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-01",
              "type": "ArbeidsgiverperiodeDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-02",
              "type": "ArbeidsgiverperiodeDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-03",
              "type": "ArbeidsgiverperiodeDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-04",
              "type": "ArbeidsgiverperiodeDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-05",
              "type": "ArbeidsgiverperiodeDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-06",
              "type": "NavHelgDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-07",
              "type": "NavHelgDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-08",
              "type": "NavDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-09",
              "type": "NavDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-10",
              "type": "NavDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-11",
              "type": "NavDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-12",
              "type": "NavDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-13",
              "type": "NavHelgDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-14",
              "type": "NavHelgDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-15",
              "type": "NavDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-16",
              "type": "NavDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-17",
              "type": "NavDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-18",
              "type": "NavDag",
              "begrunnelser": null
            },
            {
              "dato": "2021-11-19",
              "type": "NavDag",
              "begrunnelser": null
            }
          ],
          "@event_name": "utbetaling_utbetalt",
          "@id": "${UUID.randomUUID()}",
          "@opprettet": "2021-12-02T04:00:43",
          "fødselsnummer": "12345678910",
          "aktørId": "1234567890123",
          "organisasjonsnummer": "123456789",
          "vedtaksperiodeIder": [
            "$vedtaksperiodeId"
          ]
        }
    """.trimIndent()


}
