package no.nav.helse.spre.stonadsstatistikk

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader

internal class UtbetaltService(
    private val utbetaltDao: UtbetaltDao,
    private val dokumentDao: DokumentDao,
    private val annulleringDao: AnnulleringDao,
    private val stønadProducer: KafkaProducer<String, String>
) {
    internal fun håndter(vedtak: UtbetaltRiver.Vedtak) {
        val dokumenter = dokumentDao.finnDokumenter(vedtak.hendelser)
        val stønad: UtbetaltEvent = vedtak.toUtbetalt(dokumenter)
        utbetaltDao.opprett(vedtak.hendelseId, stønad)
        stønadProducer.send(ProducerRecord(
            "aapen-sykepenger-stonadsstatistikk",
            null,
            vedtak.fødselsnummer,
            objectMapper.writeValueAsString(stønad),
            listOf(RecordHeader("type", "UTBETALING".toByteArray()))
        ))
    }

    private fun UtbetaltRiver.Vedtak.toUtbetalt(dokumenter: Dokumenter) = UtbetaltEvent(
        fødselsnummer = fødselsnummer,
        organisasjonsnummer = orgnummer,
        sykmeldingId = dokumenter.sykmelding.dokumentId,
        soknadId = dokumenter.søknad.dokumentId,
        inntektsmeldingId = dokumenter.inntektsmelding?.dokumentId,
        oppdrag = oppdrag.filter { oppdrag -> oppdrag.utbetalingslinjer.isNotEmpty() }.map { oppdrag ->
            UtbetaltEvent.Utbetalt(
                mottaker = oppdrag.mottaker,
                fagområde = oppdrag.fagområde,
                fagsystemId = oppdrag.fagsystemId,
                totalbeløp = oppdrag.totalbeløp,
                utbetalingslinjer = oppdrag.utbetalingslinjer.map { linje ->
                    UtbetaltEvent.Utbetalt.Utbetalingslinje(
                        fom = linje.fom,
                        tom = linje.tom,
                        dagsats = linje.dagsats,
                        beløp = linje.beløp,
                        grad = linje.grad,
                        sykedager = linje.sykedager
                    )
                }
            )
        },
        fom = fom,
        tom = tom,
        forbrukteSykedager = forbrukteSykedager,
        gjenståendeSykedager = gjenståendeSykedager,
        maksdato = maksdato,
        utbetalingstidspunkt = opprettet
    )


    internal fun håndter(annullering: Annullering) {
        annulleringDao.opprett(annullering)
        stønadProducer.send(ProducerRecord(
            "aapen-sykepenger-stonadsstatistikk",
            null,
            annullering.fødselsnummer,
            objectMapper.writeValueAsString(annullering),
            listOf(RecordHeader("type", "ANNULLERING".toByteArray()))
        ))
    }



}
