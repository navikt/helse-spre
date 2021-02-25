package no.nav.helse.spre.oppgaver

import org.apache.kafka.common.serialization.Serializer

class JacksonSerializer<T> : Serializer<T> {
    override fun serialize(topic: String?, data: T): ByteArray = objectMapper.writeValueAsBytes(data)
}
