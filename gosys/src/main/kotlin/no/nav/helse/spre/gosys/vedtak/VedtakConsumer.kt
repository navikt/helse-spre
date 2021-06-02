package no.nav.helse.spre.gosys.vedtak

import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.gosys.sikkerLogg
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.System.currentTimeMillis
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

internal val logger: Logger = LoggerFactory.getLogger("re-lesing")
internal val sikretLogg: Logger = LoggerFactory.getLogger("tjenestekall")

class VedtakConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val duplikatsjekkDao: DuplikatsjekkDao
) {
    private val hendelserMedProduserteVedtak by lazy { lesHendelserMedProduserteVedtakEtterRelesing() }
    private val topicName = "tbd.rapid.v1"

    fun consume() {
        val topicPartitions: List<TopicPartition> = consumer.partitionsFor(topicName)
            .map { info: PartitionInfo -> TopicPartition(topicName, info.partition()) }
        consumer.assign(topicPartitions)
        consumer.seekToBeginning(topicPartitions)

        var count = 0
        var finished = false
        val startMillis = currentTimeMillis()
        val sluttidspunktMillis = LocalDate.of(2021, 3, 30).toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC) * 1000
        val manglerIDuplikattabellen = mutableSetOf<String>()

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
                    .filterNot { (event, _) -> event["@id"].asText() in hendelserMedProduserteVedtak }
                    .forEach { (event, _) -> manglerIDuplikattabellen.add(event["@id"].asText()) }
            }
        }
        consumer.unsubscribe()
        consumer.close()

        sikkerLogg.info("Klargjort hendelseIder for insert i duplikat-tabellen: $manglerIDuplikattabellen")
        duplikatsjekkDao.insertAlleredeProdusertVedtak(manglerIDuplikattabellen)

        logger.info("Prosessert $count events på ${forbruktTid(startMillis)}")
        logger.info("""Lagt inn ${manglerIDuplikattabellen.size} "nye" duplikater""")

    }

    private fun lesHendelserMedProduserteVedtakEtterRelesing(): Set<String> =
        lesHendelserMedProduserteVedtakEtterFørsteRelesing() + lesHendelserMedProduserteVedtakEtterAndreRelesing()

    private fun lesHendelserMedProduserteVedtakEtterFørsteRelesing(): Set<String> =
        this::class.java.getResourceAsStream("/hendelse_ider_relest.txt")!!
            .bufferedReader(Charsets.UTF_8)
            .readLines()
            .toSet()

    private fun lesHendelserMedProduserteVedtakEtterAndreRelesing(): Set<String> =
        this::class.java.getResourceAsStream("/hendelse_ider_relest_i_andre_omgang.txt")!!
            .bufferedReader(Charsets.UTF_8)
            .readLines()
            .toSet()

    private fun forbruktTid(startMillis: Long) =
        Duration.ofMillis(currentTimeMillis() - startMillis).run {
            "${toHours()}t${toMinutesPart()}m${toSecondsPart()}s"
        }
}



