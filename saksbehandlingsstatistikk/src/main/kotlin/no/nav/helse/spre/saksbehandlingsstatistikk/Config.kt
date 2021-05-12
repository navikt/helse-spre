package no.nav.helse.spre.saksbehandlingsstatistikk

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

data class Environment(
    val raw: Map<String, String>,
    val db: DB,
) {
    constructor(raw: Map<String, String>) : this(
        raw = raw,
        db = DB(
            name = raw.getValue("DB_DATABASE"),
            host = raw.getValue("DB_HOST"),
            port = raw.getValue("DB_PORT").toInt(),
            username = raw.getValue("DB_USERNAME"),
            password = raw.getValue("DB_PASSWORD"),
        ),
    )

    data class DB(
        val name: String,
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
    ) {
        val jdbcUrl: String = "jdbc:postgresql://${host}:${port}/${name}"
    }
}

object global {
    private var v : String? = null;
    fun setVersjon(it: String = System.getenv().getValue("GIT_SHA")) {
        v = it;
    }

    val versjon get() = v!!
}


fun loadBaseConfig(env: Map<String, String>): Properties = Properties().also {
    it[(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)] = env["KAFKA_BROKERS"]
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
    it[ProducerConfig.ACKS_CONFIG] = "1"
    it[ProducerConfig.CLIENT_ID_CONFIG] = "spre-saksbehandlingsstatistikk-v1"
    it[ProducerConfig.LINGER_MS_CONFIG] = "5"
    it[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "1"
    it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
}
