package no.nav.helse.spre.stonadsstatistikk

import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.verify
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.stonadsstatistikk.DatabaseHelpers.Companion.dataSource
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.*
import kotlin.streams.asSequence

private const val FNR = "12020052345"
private const val ORGNUMMER = "987654321"

internal class EndToEndTest {
    private val testRapid = TestRapid()
    private val dokumentDao = DokumentDao(dataSource)
    private val utbetaltDao = UtbetaltDao(dataSource)
    private val annulleringDao = AnnulleringDao(dataSource)
    private val kafkaStønadProducer: KafkaProducer<String, String> = mockk(relaxed = true)
    private val utbetaltService = UtbetaltService(utbetaltDao, dokumentDao, annulleringDao, kafkaStønadProducer)

    init {
        NyttDokumentRiver(testRapid, dokumentDao)
        UtbetaltRiver(testRapid, utbetaltService)

        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "TRUNCATE TABLE utbetaling, oppdrag, vedtak, hendelse, vedtak_utbetalingsref, annullering"
            session.run(queryOf(query).asExecute)
        }
    }

    @Test
    fun `Dagens situasjon`() {
        val nyttVedtakSøknadHendelseId = UUID.randomUUID()
        val nyttVedtakSykmelding = Hendelse(UUID.randomUUID(), nyttVedtakSøknadHendelseId, Dokument.Sykmelding)
        val nyttVedtakSøknad = Hendelse(UUID.randomUUID(), nyttVedtakSøknadHendelseId, Dokument.Søknad)
        val nyttVedtakInntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
        testRapid.sendTestMessage(sendtSøknadMessage(nyttVedtakSykmelding, nyttVedtakSøknad))
        testRapid.sendTestMessage(inntektsmeldingMessage(nyttVedtakInntektsmelding))
        val nyttVedtakHendelseId = UUID.randomUUID()
        testRapid.sendTestMessage(
            utbetalingMessage(
                nyttVedtakHendelseId,
                LocalDate.of(2020, 7, 1),
                LocalDate.of(2020, 7, 8),
                0,
                listOf(nyttVedtakSykmelding, nyttVedtakSøknad, nyttVedtakInntektsmelding)
            )
        )

        val capture = CapturingSlot<ProducerRecord<String, String>>()
        verify { kafkaStønadProducer.send(capture(capture)) }
        val record = capture.captured
        assertEquals("UTBETALING", String(record.headers().headers("type").first().value()))

        val sendtTilStønad = objectMapper.readValue<UtbetaltEvent>(record.value())
        val event = UtbetaltEvent(
            fødselsnummer = FNR,
            organisasjonsnummer = ORGNUMMER,
            sykmeldingId = nyttVedtakSykmelding.dokumentId,
            soknadId = nyttVedtakSøknad.dokumentId,
            inntektsmeldingId = nyttVedtakInntektsmelding.dokumentId,
            oppdrag = listOf(
                UtbetaltEvent.Utbetalt(
                mottaker = ORGNUMMER,
                fagområde = "SPREF",
                fagsystemId = "77ATRH3QENHB5K4XUY4LQ7HRTY",
                totalbeløp = 8586,
                utbetalingslinjer = listOf(
                    UtbetaltEvent.Utbetalt.Utbetalingslinje(
                    fom = LocalDate.of(2020, 7, 1),
                    tom = LocalDate.of(2020, 7, 8),
                    dagsats = 1431,
                    beløp = 1431,
                    grad = 100.0,
                    sykedager = 6
                )
                )
            )
            ),
            fom = LocalDate.of(2020, 7, 1),
            tom = LocalDate.of(2020, 7, 8),
            forbrukteSykedager = 6,
            gjenståendeSykedager = 242,
            maksdato = LocalDate.of(2021, 6, 11),
            utbetalingstidspunkt = sendtTilStønad.utbetalingstidspunkt
        )

        val lagretVedtak = utbetaltDao.hentUtbetalinger().first()
        assertEquals(event, lagretVedtak)
    }


    @Test
    fun `genererer rapport fra alle vedtak i basen`() {

        data class Rapport(
            val fødselsnummer: String,
            val sykmeldingId: UUID,
            val soknadId: UUID,
            val inntektsmeldingId: UUID,
            val førsteUtbetalingsdag: LocalDate,
            val sisteUtbetalingsdag: LocalDate,
            val sum: Int,
            val maksgrad: Double,
            val utbetaltTidspunkt: LocalDateTime,
            val orgnummer: String,
            val forbrukteSykedager: Int,
            val gjenståendeSykedager: Int?,
            val fom: LocalDate,
            val tom: LocalDate
        )


        val nyttVedtakSøknadHendelseId = UUID.randomUUID()
        val nyttVedtakSykmelding = Hendelse(UUID.randomUUID(), nyttVedtakSøknadHendelseId, Dokument.Sykmelding)
        val nyttVedtakSøknad = Hendelse(UUID.randomUUID(), nyttVedtakSøknadHendelseId, Dokument.Søknad)
        val nyttVedtakInntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
        testRapid.sendTestMessage(sendtSøknadMessage(nyttVedtakSykmelding, nyttVedtakSøknad))
        testRapid.sendTestMessage(inntektsmeldingMessage(nyttVedtakInntektsmelding))
        val nyttVedtakHendelseId = UUID.randomUUID()
        testRapid.sendTestMessage(
            utbetalingMessage(
                nyttVedtakHendelseId,
                LocalDate.of(2020, 7, 1),
                LocalDate.of(2020, 7, 8),
                0,
                listOf(nyttVedtakSykmelding, nyttVedtakSøknad, nyttVedtakInntektsmelding)
            )
        )

        val rapport = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """SELECT v.fodselsnummer,
                                v.sykmelding_id,
                                v.soknad_id,
                                v.inntektsmelding_id,
                                o.totalbelop            sum,
                                o.fagsystem_id,
                                u.fom                   forste_utbetalingsdag,
                                u.tom                   siste_utbetalingsdag,
                                u.grad                  maksgrad,
                                u.belop,
                                u.dagsats,
                                u.sykedager,
                                (u.belop * u.sykedager) totalbelop,
                                v.utbetalingstidspunkt             utbetalt_tidspunkt,
                                v.organisasjonsnummer,
                                v.forbrukte_sykedager,
                                v.gjenstaende_sykedager,
                                v.fom,
                                v.tom
                         FROM vedtak v
                                  INNER JOIN oppdrag o on v.id = o.vedtak_id
                                  INNER JOIN utbetaling u on o.id = u.oppdrag_id
                        ORDER BY utbetalt_tidspunkt, forste_utbetalingsdag, siste_utbetalingsdag
                """
            session.run(queryOf(query).map { row ->
                Rapport(
                    fødselsnummer = row.string("fodselsnummer"),
                    sykmeldingId = row.uuid("sykmelding_id"),
                    soknadId = row.uuid("soknad_id"),
                    inntektsmeldingId = row.uuid("inntektsmelding_id"),
                    førsteUtbetalingsdag = row.localDate("forste_utbetalingsdag"),
                    sisteUtbetalingsdag = row.localDate("siste_utbetalingsdag"),
                    sum = row.int("sum"),
                    maksgrad = row.double("maksgrad"),
                    utbetaltTidspunkt = row.localDateTime("utbetalt_tidspunkt"),
                    orgnummer = row.string("organisasjonsnummer"),
                    forbrukteSykedager = row.int("forbrukte_sykedager"),
                    gjenståendeSykedager = row.intOrNull("gjenstaende_sykedager"),
                    fom = row.localDate("fom"),
                    tom = row.localDate("tom")
                )
            }.asList)
        }.sortedBy { it.fom }

        assertEquals(1, rapport.size)

        assertEquals(
            Rapport(
                fødselsnummer = FNR,
                sykmeldingId = nyttVedtakSykmelding.dokumentId,
                soknadId = nyttVedtakSøknad.dokumentId,
                inntektsmeldingId = nyttVedtakInntektsmelding.dokumentId,
                førsteUtbetalingsdag = LocalDate.of(2020, 7, 1),
                sisteUtbetalingsdag = LocalDate.of(2020, 7, 8),
                sum = 8586,
                maksgrad = 100.0,
                utbetaltTidspunkt = LocalDateTime.of(2020, 5, 4, 11, 27, 13, 521000000),
                orgnummer = ORGNUMMER,
                forbrukteSykedager = 6,
                gjenståendeSykedager = 242,
                fom = LocalDate.of(2020, 7, 1),
                tom = LocalDate.of(2020, 7, 8)
            ),
            rapport.first()
        )

    }

    @Language("JSON")
    private fun utbetalingMessage(
        hendelseId: UUID,
        fom: LocalDate,
        tom: LocalDate,
        tidligereBrukteSykedager: Int,
        hendelser: List<Hendelse>,
        fagsystemId: String = "77ATRH3QENHB5K4XUY4LQ7HRTY"
    ) = """{
    "aktørId": "aktørId",
    "fødselsnummer": "$FNR",
    "organisasjonsnummer": "$ORGNUMMER",
    "hendelser": ${hendelser.map { "\"${it.hendelseId}\"" }},
    "utbetalt": [
        {
            "mottaker": "$ORGNUMMER",
            "fagområde": "SPREF",
            "fagsystemId": "$fagsystemId",
            "førsteSykepengedag": "",
            "totalbeløp": 8586,
            "utbetalingslinjer": [
                {
                    "fom": "$fom",
                    "tom": "$tom",
                    "dagsats": 1431,
                    "beløp": 1431,
                    "grad": 100.0,
                    "sykedager": ${sykedager(fom, tom)}
                }
            ]
        },
        {
            "mottaker": "$FNR",
            "fagområde": "SP",
            "fagsystemId": "353OZWEIBBAYZPKU6WYKTC54SE",
            "totalbeløp": 0,
            "utbetalingslinjer": []
        }
    ],
    "fom": "$fom",
    "tom": "$tom",
    "forbrukteSykedager": ${tidligereBrukteSykedager + sykedager(fom, tom)},
    "gjenståendeSykedager": ${248 - tidligereBrukteSykedager - sykedager(fom, tom)},
    "maksdato": "${maksdato(tidligereBrukteSykedager, fom, tom)}",
    "opprettet": "2020-05-04T11:26:30.23846",
    "system_read_count": 0,
    "@event_name": "utbetalt",
    "@id": "$hendelseId",
    "@opprettet": "2020-05-04T11:27:13.521000",
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "cf28fbba-562e-4841-b366-be1456fdccee",
        "opprettet": "2020-05-04T11:26:47.088455"
    }
}
"""



    private fun sykedager(fom: LocalDate, tom: LocalDate) =
        fom.datesUntil(tom.plusDays(1)).asSequence()
            .filter { it.dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.count()

    private fun maksdato(tidligereBrukteSykedager: Int, fom: LocalDate, tom: LocalDate) =
        (0..247 - sykedager(fom, tom) - tidligereBrukteSykedager).fold(tom) { tilOgMed, _ ->
            if (tilOgMed.dayOfWeek in listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)) tilOgMed.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            else tilOgMed.plusDays(1)
        }
}

fun sendtSøknadMessage(sykmelding: Hendelse, søknad: Hendelse) =
    """{
            "@event_name": "sendt_søknad_nav",
            "@id": "${søknad.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sykmeldingId": "${sykmelding.dokumentId}"
        }"""

fun inntektsmeldingMessage(hendelse: Hendelse) =
    """{
            "@event_name": "inntektsmelding",
            "@id": "${hendelse.hendelseId}",
            "inntektsmeldingId": "${hendelse.dokumentId}"
        }"""
