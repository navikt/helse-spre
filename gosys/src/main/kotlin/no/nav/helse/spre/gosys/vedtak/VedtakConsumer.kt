package no.nav.helse.spre.gosys.vedtak

import no.nav.helse.spre.gosys.objectMapper
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.System.currentTimeMillis
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

internal val logger: Logger = LoggerFactory.getLogger("re-lesing")

class VedtakConsumer(
    private val consumer: KafkaConsumer<String, String>,
//    private val vedtakMediator: VedtakMediator,
//    private val duplikatsjekkDao: DuplikatsjekkDao
) {
    private val produserteVedtak by lazy { lesProduserteVedtak() }
    private val topicName = "tbd.rapid.v1"

    fun consume() {
        val topicPartitions: List<TopicPartition> = consumer.partitionsFor(topicName)
            .map { info: PartitionInfo -> TopicPartition(topicName, info.partition()) }
        consumer.assign(topicPartitions)
        consumer.seekToBeginning(topicPartitions)

        var count = 0
        var ingenBehandlingTeller = 0
        var alleredeProdusertTeller = 0
        var ingenBehandlingSammeTidsromTeller = 0
        var finished = false
        val startMillis = currentTimeMillis()

        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> logger.error(throwable.message, throwable) }
        while (!finished) {
            consumer.poll(Duration.ofMillis(5000)).let { records ->
                if (records.isEmpty || records.any { record ->
                        record.timestamp().toLocalDate() > LocalDate.of(2021, 3, 29)
                    }) {
                    finished = true
                }
                records
                    .filter { record -> objectMapper.readTree(record.value())["@event_name"].asText() == "utbetalt" }
                    .onEach {
                        if (count++ % 100 == (Math.random() * 100).toInt()) logger.info("Har prosessert $count events")
                    }
                    .forEach { record ->
                        val timestamp = record.timestamp().toLocalDate()
                        val aktørId = objectMapper.readTree(record.value())["aktørId"].asText()
                        val hendelseId = objectMapper.readTree(record.value())["@id"].asText()
                        val datoer = produserteVedtak[aktørId]

                        if (datoer == null) {
//                            vedtakMediator.opprettVedtak(VedtakMessage(record))
                            ingenBehandlingTeller++
                        } else {
                            if (datoer.any { dato ->
                                    ChronoUnit.DAYS.between(LocalDate.parse(dato), timestamp).absoluteValue < 2
                                }) {
//                                duplikatsjekkDao.insertAlleredeProdusertVedtak(hendelseId)
                                alleredeProdusertTeller++
                            } else {
//                                vedtakMediator.opprettVedtak(VedtakMessage(record))
                                ingenBehandlingSammeTidsromTeller++
                            }
                        }
                    }
            }
        }
        consumer.unsubscribe()
        consumer.close()
        logger.info("Prosessert $count events på ${forbruktTid(startMillis)}")
        logger.info("Tellere: ingen behandling: $ingenBehandlingTeller, allerede produsert: $alleredeProdusertTeller, ingen behandling i samme tidsrom: $ingenBehandlingSammeTidsromTeller")
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


