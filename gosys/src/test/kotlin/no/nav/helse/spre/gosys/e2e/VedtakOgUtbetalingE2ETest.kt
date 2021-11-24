package no.nav.helse.spre.gosys.e2e

import io.ktor.util.*
import no.nav.helse.spre.Toggle
import no.nav.helse.spre.gosys.e2e.AbstractE2ETest.Utbetalingstype.REVURDERING
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtbetaltRiver
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtenUtbetalingRiver
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload.*
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayloadV2
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetRiver
import no.nav.helse.spre.testhelpers.arbeidsdager
import no.nav.helse.spre.testhelpers.avvistDager
import no.nav.helse.spre.testhelpers.februar
import no.nav.helse.spre.testhelpers.feriedager
import no.nav.helse.spre.testhelpers.fridager
import no.nav.helse.spre.testhelpers.januar
import no.nav.helse.spre.testhelpers.permisjonsdager
import no.nav.helse.spre.testhelpers.utbetalingsdager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@KtorExperimentalAPI
internal class VedtakOgUtbetalingE2ETest : AbstractE2ETest() {

    val vedtakFattetDao = VedtakFattetDao(dataSource)
    val utbetalingDao = UtbetalingDao(dataSource)

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
        assertVedtakPdf(expectedPdfPayload())
    }


    @Test
    fun `journalfører vedtak med vedtak_fattet og deretter utbetaling_utbetalt for brukerutbetaling`() =
        Toggle.PDFTemplateV2.enable {
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
                VedtakPdfPayloadV2.Linje(
                    fom = 1.januar,
                    tom = 31.januar,
                    grad = 100,
                    beløp = 1431,
                    mottaker = "123456 78910",
                    mottakerType = VedtakPdfPayloadV2.MottakerType.Person
                )
            )
            assertVedtakPdf(
                expectedPdfPayloadV2(
                    linjer = linjer,
                    arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag(),
                    personOppdrag = VedtakPdfPayloadV2.Oppdrag(
                        linjer
                    )
                )
            )
        }

    @Test
    fun `delvis refusjon`() {
        Toggle.PDFTemplateV2.enable {
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
                VedtakPdfPayloadV2.Linje(
                    fom = 1.januar,
                    tom = 31.januar,
                    grad = 100,
                    beløp = 741,
                    mottaker = "123 456 789",
                    mottakerType = VedtakPdfPayloadV2.MottakerType.Arbeidsgiver
                ),
                VedtakPdfPayloadV2.Linje(
                    fom = 1.januar,
                    tom = 31.januar,
                    grad = 100,
                    beløp = 700,
                    mottaker = "123456 78910",
                    mottakerType = VedtakPdfPayloadV2.MottakerType.Person
                )
            )

            assertJournalpost()
            assertVedtakPdf(
                expectedPdfPayloadV2(
                    linjer = expectedLinjer,
                    arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag(expectedLinjer.subList(0, 1)),
                    personOppdrag = VedtakPdfPayloadV2.Oppdrag(expectedLinjer.subList(1, 2)),
                    totaltTilUtbetaling = 33143
                )
            )
        }
    }

    @Test
    fun `sorterer linjer`() {
        Toggle.PDFTemplateV2.enable {
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
                VedtakPdfPayloadV2.Linje(
                    fom = 8.februar,
                    tom = 18.februar,
                    grad = 100,
                    beløp = 1431,
                    mottaker = "123456 78910",
                    mottakerType = VedtakPdfPayloadV2.MottakerType.Person
                ),
                VedtakPdfPayloadV2.Linje(
                    fom = 1.januar,
                    tom = 31.januar,
                    grad = 100,
                    beløp = 1431,
                    mottaker = "123456 78910",
                    mottakerType = VedtakPdfPayloadV2.MottakerType.Person
                )
            )

            assertVedtakPdf(
                expectedPdfPayloadV2(
                    linjer = expectedLinjer,
                    arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag(),
                    personOppdrag = VedtakPdfPayloadV2.Oppdrag(expectedLinjer.reversed()),
                    totaltTilUtbetaling = 42930,
                    behandlingsdato = 18.februar, //TODO: Bruk `vedtakFattetTidspunkt` på vedtaket
                    ikkeUtbetalteDager = listOf(VedtakPdfPayloadV2.IkkeUtbetalteDager(1.februar, 7.februar, "Arbeidsdag", emptyList()))
                )
            )
        }
    }

    @Test
    fun `journalfører vedtak med vedtak_fattet og deretter utbetaling_uten_utbetaling for brukerutbetaling`() =
        Toggle.PDFTemplateV2.enable {
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
                    arbeidsgiverOppdrag = VedtakPdfPayloadV2.Oppdrag(),
                    personOppdrag = VedtakPdfPayloadV2.Oppdrag(),
                    totaltTilUtbetaling = 0,
                    ikkeUtbetalteDager = listOf(VedtakPdfPayloadV2.IkkeUtbetalteDager(1.januar, 31.januar, "Ferie/Permisjon", emptyList()))
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
            expectedPdfPayload().copy(
                totaltTilUtbetaling = 0,
                linjer = emptyList(),
                arbeidsgiverOppdrag = Oppdrag(),
                ikkeUtbetalteDager = listOf(
                    IkkeUtbetalteDager(
                        fom = 1.januar,
                        tom = 31.januar,
                        grunn = "Ferie/Permisjon",
                        begrunnelser = emptyList()
                    )
                ),
                dagsats = null
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
        assertVedtakPdf(expectedPdfPayload())
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
        assertVedtakPdf(expectedPdfPayload())
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
        val payload = actualPdfPayload()
        assertEquals(
            listOf(
                IkkeUtbetalteDager(
                    fom = 18.januar,
                    tom = 22.januar,
                    grunn = "Arbeidsdag",
                    begrunnelser = emptyList()
                )
            ),
            payload.ikkeUtbetalteDager
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
        val actual = actualPdfPayload()
        assertEquals(
            listOf(
                IkkeUtbetalteDager(
                    fom = 17.januar,
                    tom = 17.januar,
                    grunn = "Avvist dag",
                    begrunnelser = listOf("Personen er død")
                )
            ), actual.ikkeUtbetalteDager
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
        val pdfPayload = actualPdfPayload()
        assertEquals(2.januar, pdfPayload.fom)
        assertEquals(31.januar, pdfPayload.tom)
        assertTrue(pdfPayload.ikkeUtbetalteDager.isEmpty())
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
            expectedPdfPayload(
                fom = 1.januar,
                tom = 28.februar,
                utbetalingstype = REVURDERING,
                totaltTilUtbetaling = 61533,
                behandlingsdato = utbetalingstidspunkt.toLocalDate()
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

        val payload = actualPdfPayload()
        assertEquals(1.januar, payload.fom)
        assertEquals(28.februar, payload.tom)
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

        val payload = actualPdfPayload()
        assertEquals(1.januar, payload.fom)
        assertEquals(28.februar, payload.tom)
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

        val payload = actualPdfPayload()
        assertEquals(1.januar, payload.fom)
        assertEquals(28.februar, payload.tom)

        assertVedtakPdf(
            expectedPdfPayload(
                fom = 1.januar,
                tom = 28.februar,
                utbetalingstype = REVURDERING,
                totaltTilUtbetaling = 60102,
                behandlingsdato = utbetalingstidspunkt.toLocalDate(),
                linjer = listOf(
                    Linje(fom = 1.januar, tom = 28.januar, grad = 100, beløp = 1431, mottaker = "123 456 789"),
                    Linje(fom = 30.januar, tom = 28.februar, grad = 100, beløp = 1431, mottaker = "123 456 789")
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
}
