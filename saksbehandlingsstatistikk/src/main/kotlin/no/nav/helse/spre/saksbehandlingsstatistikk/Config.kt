package no.nav.helse.spre.saksbehandlingsstatistikk

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import java.nio.file.Paths
import java.util.Properties

data class Environment(
    val raw: Map<String, String>,
    val db: DB
) {
    constructor(raw: Map<String, String>) : this(
        raw = raw,
        db = DB(
            name = raw.getValue("DB_DATABASE"),
            host = raw.getValue("DB_HOST"),
            port = raw.getValue("DB_PORT").toInt(),
        ),
    )

    data class DB(
        val name: String,
        val host: String,
        val port: Int,
    ) {
        val jdbcUrl: String = "jdbc:postgresql://${host}:${port}/${name}"
    }
}

fun loadBaseConfig(env: Map<String, String>): Properties = Properties().also {
    it[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.SSL.name
    it[(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG)] = ""
    it[(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG)] = "jks"
    it[(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG)] = "PKCS12"
    it[(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG)] = env["KAFKA_TRUSTSTORE_PATH"]
    it[(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG)] = env["KAFKA_CREDSTORE_PASSWORD"]
    it[(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG)] = env["KAFKA_KEYSTORE_PATH"]
    it[(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG)] = env["KAFKA_CREDSTORE_PASSWORD"]
}

fun Properties.toProducerConfig(): Properties = Properties().also {
    it.putAll(this)
    it[ProducerConfig.ACKS_CONFIG] = "all"
    it[ProducerConfig.CLIENT_ID_CONFIG] = "spre-saksbehandlingsstatistikk"
    it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
}
