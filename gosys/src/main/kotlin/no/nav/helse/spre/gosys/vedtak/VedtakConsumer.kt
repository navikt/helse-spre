package no.nav.helse.spre.gosys.vedtak

import java.time.Duration
import java.time.LocalDate
import no.nav.helse.spre.gosys.log
import no.nav.helse.spre.gosys.objectMapper
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import java.time.Instant
import java.time.ZoneId

class VedtakConsumer(
    val consumer: KafkaConsumer<String, String>,
//    private val vedtakMediator: VedtakMediator,
//    private val duplikatsjekkDao: DuplikatsjekkDao
) {
    val produserteVedtak by lazy { lesProduserteVedtak() }
    val topicPartition = TopicPartition("tbd.rapid.v1", 0)

    fun consume() {
        consumer.assign(listOf(topicPartition))

        var count = 0
        var ingenBehandlingTeller = 0
        var alleredeProdusertTeller = 0
        var ingenBehandlingSammeTidsromTeller = 0
        var finished = false
        val startMillis = System.currentTimeMillis()

        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> log.error(throwable.message, throwable) }
        while (!finished) {
            consumer.poll(Duration.ofMillis(5000)).let { records ->
                if (records.isEmpty || records.all { record ->
                        record.timestamp() < LocalDate.of(2021, 3, 30).toEpochDay()
                    }) {
                    finished = true
                }
                records
                    .filter { record -> objectMapper.readTree(record.value())["@event_name"].asText() == "utbetalt" }
                    .onEach { count++ }
                    .forEach { record ->
                        val timestamp = LocalDate.ofInstant(Instant.ofEpochMilli(record.timestamp()), ZoneId.systemDefault())
                        val aktørId = objectMapper.readTree(record.value())["aktørId"].asText()
                        val hendelseId = objectMapper.readTree(record.value())["@id"].asText()
                        val datoer = produserteVedtak[aktørId]

                        if (datoer == null) {
//                            vedtakMediator.opprettVedtak(VedtakMessage(record))
                            ingenBehandlingTeller++
                        } else {
                            if (datoer.any { dato ->
                                    Duration.between(LocalDate.parse(dato), timestamp).toDays() < 2
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
        log.info("Prosessert $count events på ${(System.currentTimeMillis() - startMillis) / 1000} sekunder")
        log.info("Tellere: ingen behandling: $ingenBehandlingTeller, allerede produsert: $alleredeProdusertTeller, inegen behandling i samme tidsrom: $ingenBehandlingSammeTidsromTeller")
    }

    private fun lesProduserteVedtak(): MutableMap<String, List<String>> {
        val noe = mutableMapOf<String, List<String>>()
        this::class.java.getResourceAsStream("./vedtak_produsert.txt")
            .bufferedReader(Charsets.UTF_8)
            .readLines()
            .map {
                it.split(",").let {
                    val dato = it[0]
                    val aktørId = it[1]
                    noe.merge(
                        aktørId,
                        listOf(dato)
                    ) { eksisterende, neste -> eksisterende + neste }
                }
            }
        return noe
    }
}


