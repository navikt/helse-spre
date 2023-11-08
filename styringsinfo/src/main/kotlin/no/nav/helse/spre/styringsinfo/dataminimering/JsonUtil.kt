package no.nav.helse.spre.styringsinfo.dataminimering

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.objectMapper

internal object JsonUtil {

    fun fjernRotNoderFraJson(json: String, navnPåNoderSomSkalFjernes: List<String>): String {
        val objectNode = objectMapper.readTree(json) as ObjectNode
        navnPåNoderSomSkalFjernes.map { objectNode.remove(it) }
        return objectMapper.writeValueAsString(objectNode)
    }

    fun traverserOgFjernNoder(json: String, navnPåNodeSomSkalFjernes: String): String {
        val jsonNode = objectMapper.readTree(json)
        traverserOgFjernNoder(jsonNode, navnPåNodeSomSkalFjernes)
        return objectMapper.writeValueAsString(jsonNode)
    }

    private fun traverserOgFjernNoder(jsonNode: JsonNode, navnPåNodeSomSkalFjernes: String) {
        if (jsonNode.isArray) {
            for (arrayItem in jsonNode) {
                fjernNode(arrayItem, navnPåNodeSomSkalFjernes)
            }
        } else if (jsonNode.isObject) {
            fjernNode(jsonNode, navnPåNodeSomSkalFjernes)
        }
    }

    private fun fjernNode(foreldreNode: JsonNode, navnPåNodeSomSkalFjernes: String) {
        if (foreldreNode.isObject && foreldreNode.get(navnPåNodeSomSkalFjernes) != null) {
            (foreldreNode as ObjectNode).remove(navnPåNodeSomSkalFjernes)
        }
        val fields = foreldreNode.fields()
        while (fields.hasNext()) {
            traverserOgFjernNoder(fields.next().value, navnPåNodeSomSkalFjernes)
        }
    }
}

