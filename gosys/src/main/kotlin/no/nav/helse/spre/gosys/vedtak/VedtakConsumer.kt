package no.nav.helse.spre.gosys.vedtak

import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.objectMapper
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.System.currentTimeMillis
import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

internal val logger: Logger = LoggerFactory.getLogger("re-lesing")
internal val sikretLogger: Logger = LoggerFactory.getLogger("tjenestekall")

class VedtakConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val vedtakMediator: VedtakMediator,
    private val duplikatsjekkDao: DuplikatsjekkDao
) {
    private val produserteVedtak by lazy { lesProduserteVedtak() }
    private val topicName = "tbd.rapid.v1"

    fun consume() {
        val topicPartitions: List<TopicPartition> = consumer.partitionsFor(topicName)
            .map { info: PartitionInfo -> TopicPartition(topicName, info.partition()) }
        consumer.assign(topicPartitions)
        consumer.seekToBeginning(topicPartitions)

        var count = 0
        var finished = false
        val startMillis = currentTimeMillis()
        val sluttidspunktMillis = LocalDate.of(2021, 3, 21).toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC) * 1000
        val produsereForDatoer = mutableMapOf<LocalDate, Int>()
        val hendelseIdToAktørId = mutableMapOf<String, String>()
        var nyePdferÅProdusere = 0
        var nyeInsertsIDuplikattabellen = 0

        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> logger.error(throwable.message, throwable) }
        while (!finished) {
            consumer.poll(Duration.ofMillis(5000)).let { records ->
                if (records.isEmpty || records.any { record -> record.timestamp() > sluttidspunktMillis }) {
                    finished = true
                }
                records
                    .filter { it.value().contains("utbetalt") }
                    .map { record -> objectMapper.readTree(record.value()) to record.timestamp() }
                    .filter { (event, _) -> event["@event_name"].asText() == "utbetalt" }
                    .onEach {
                        if (count++ % 100 == (Math.random() * 100).toInt()) logger.info("Har prosessert $count events")
                    }
                    .forEach { (event, timestamp) ->
                        val aktørId = event["aktørId"].asText()
                        val hendelseId = event["@id"].asText()
                        val datoer = produserteVedtak[aktørId]

                        val recordDato = timestamp.toLocalDate()
                        if (datoer == null || datoer.all { dato ->
                                ChronoUnit.DAYS.between(LocalDate.parse(dato), recordDato).absoluteValue > 1
                            }) {
                            logger.info(
                                "Vi har ikke logget at vi har produsert PDF for utbetalt-event: {}, {}, {}",
                                keyValue("hendelseId", hendelseId),
                                keyValue("aktørId", aktørId),
                                keyValue("recordDate", recordDato),
                            )
                            vedtakMediator.opprettVedtak(VedtakMessage(event))
                            hendelseIdToAktørId[hendelseId] = aktørId
                            produsereForDatoer.merge(recordDato, 1) { total, neste -> total + neste}
                            nyePdferÅProdusere++
                        } else {
                            logger.info(
                                "Vi har allerede produsert PDF for {}, legges til i duplikattabellen",
                                keyValue("hendelseId", hendelseId)
                            )
                            duplikatsjekkDao.insertAlleredeProdusertVedtak(hendelseId)
                            nyeInsertsIDuplikattabellen++
                        }
                    }
            }
        }
        consumer.unsubscribe()
        consumer.close()
        logger.info("Prosessert $count events på ${forbruktTid(startMillis)}")
        logger.info("Tellere: Antall PDF-er vi tenker å produsere: $nyePdferÅProdusere, nye inserts i duplikattabellen: $nyeInsertsIDuplikattabellen")
        logger.info("Datoer med manglende PDF-er: $produsereForDatoer")
        sikretLogger.info("Produsere PDF-er for $hendelseIdToAktørId")
    }

    private fun lesProduserteVedtak(): MutableMap<String, List<String>> {
        val noe = mutableMapOf<String, List<String>>()
        this::class.java.getResourceAsStream("/vedtak_produsert.txt")!!
            .bufferedReader(Charsets.UTF_8)
            .readLines()
            .map {
                it.split(",").let { (dato, aktørId) ->
                    noe.merge(aktørId, listOf(dato)) { eksisterende, neste -> eksisterende + neste }
                }
            }
        return noe
    }

    private fun forbruktTid(startMillis: Long) =
        Duration.ofMillis(currentTimeMillis() - startMillis).run {
            "${toHours()}t${toMinutesPart()}m${toSecondsPart()}s"
        }
}

private fun Long.toLocalDate() = LocalDate.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())


