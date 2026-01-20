package no.nav.helse.spre.styringsinfo.teamsak

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import java.util.UUID
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperiodeVenterDto
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperioderVenterIndirektePåGodkjenning
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

internal class KontraktTest {

    @Test
    fun vedtaksperioder_venter() {
        @Language("JSON")
        val json = """
        {
          "@event_name": "vedtaksperioder_venter",
          "vedtaksperioder": [
            {
              "yrkesaktivitetstype": "ARBEIDSTAKER",
              "vedtaksperiodeId": "1c1e8d30-5379-4fa2-8f27-0ceee0ab1e03",
              "behandlingId": "86638dfa-8439-49cf-9ed3-7bbc5cd731be",
              "venterPå": {
                "vedtaksperiodeId": "ae57b8c9-ef74-45dd-9a0d-fb1ad7c4de4d",
                "venteårsak": {
                  "hva": "GODKJENNING"
                }
              }
            },
            {
              "yrkesaktivitetstype": "ARBEIDSTAKER",
              "vedtaksperiodeId": "f6ddd813-e75c-4aa3-9f75-d990958729c0",
              "behandlingId": "5947d72d-7e77-4490-8958-58a84b79ffc3",
              "venterPå": {
                "vedtaksperiodeId": "a014867e-d211-47d9-81e6-124012dce87a",
                "venteårsak": {
                  "hva": "ARBEIDSGIVER"
                }
              }
            }
          ],
          "@id": "913e7235-ccb6-4d3d-8a70-72ba07597f8c",
          "@opprettet": "2026-01-20T08:10:19.677780459"
        }
        """
        val (vedtaksperiodeVenter, blob) = opprettMeldingFra(
            json = json,
            eventName = "vedtaksperioder_venter",
            valider = VedtaksperioderVenterIndirektePåGodkjenning::valider,
            opprett = VedtaksperioderVenterIndirektePåGodkjenning::opprettet
        )!!

        val forventet = VedtaksperioderVenterIndirektePåGodkjenning(
            id = UUID.fromString("913e7235-ccb6-4d3d-8a70-72ba07597f8c"),
            opprettet = "2026-01-20T08:10:19.677780459".offsetDateTimeOslo,
            data = blob,
            venter = listOf(
                VedtaksperiodeVenterDto(
                    vedtaksperiodeId = UUID.fromString("1c1e8d30-5379-4fa2-8f27-0ceee0ab1e03"),
                    behandlingId = UUID.fromString("86638dfa-8439-49cf-9ed3-7bbc5cd731be"),
                    yrkesaktivitetstype = "ARBEIDSTAKER",
                    venterPå = VedtaksperiodeVenterDto.VenterPå(
                        vedtaksperiodeId = UUID.fromString("ae57b8c9-ef74-45dd-9a0d-fb1ad7c4de4d"),
                        venteårsak = VedtaksperiodeVenterDto.Venteårsak("GODKJENNING")
                    )
                ),
                VedtaksperiodeVenterDto(
                    vedtaksperiodeId = UUID.fromString("f6ddd813-e75c-4aa3-9f75-d990958729c0"),
                    behandlingId = UUID.fromString("5947d72d-7e77-4490-8958-58a84b79ffc3"),
                    yrkesaktivitetstype = "ARBEIDSTAKER",
                    venterPå = VedtaksperiodeVenterDto.VenterPå(
                        vedtaksperiodeId = UUID.fromString("a014867e-d211-47d9-81e6-124012dce87a"),
                        venteårsak = VedtaksperiodeVenterDto.Venteårsak("ARBEIDSGIVER")
                    )
                ))
        )

        assertEquals(forventet, vedtaksperiodeVenter)
    }


    private fun<T> opprettMeldingFra(@Language("JSON") json: String, eventName: String, valider: (packet: JsonMessage) -> Unit, opprett: (packet: JsonMessage) -> T): Pair<T, JsonNode>? {
        val packet = JsonMessage(json, MessageProblems(json))
        packet.requireKey("@event_name")
        if (packet["@event_name"].asText() != eventName) return null
        HendelseRiver.fellesValidering(packet)
        valider(packet)
        return try { opprett(packet) to objectMapper.readTree(packet.toJson())} catch (_: Exception) { null }
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}
