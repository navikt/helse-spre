package no.nav.helse.spre.arbeidsgiver

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

const val vaultBase = "/var/run/secrets/nais.io/service_user"
val vaultBasePath: Path = Paths.get(vaultBase)

fun readServiceUserCredentials() = ServiceUser(
    username = Files.readString(vaultBasePath.resolve("username")),
    password = Files.readString(vaultBasePath.resolve("password"))
)

data class ServiceUser(
    val username: String,
    val password: String
)

fun loadBaseConfig(kafkaBootstrapServers: String, serviceUser: ServiceUser): Properties = Properties().also {
    it[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SASL_SSL"
    it[SaslConfigs.SASL_MECHANISM] = "PLAIN"
    it[SaslConfigs.SASL_JAAS_CONFIG] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
        "username=\"${serviceUser.username}\" password=\"${serviceUser.password}\";"
    it[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = kafkaBootstrapServers
}

fun Properties.toProducerConfig(): Properties = Properties().also {
    it.putAll(this)
    it[ProducerConfig.ACKS_CONFIG] = "all"
    it[ProducerConfig.CLIENT_ID_CONFIG] = "spre-arbeidsgiver-v1"
    it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = TrengerInntektsmeldingSerializer::class.java
}
