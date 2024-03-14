package no.nav.helse.spre.styringsinfo.datafortelling.dataminimering

import no.nav.helse.spre.styringsinfo.datafortelling.dataminimering.JsonUtil.fjernRotNoderFraJson
import no.nav.helse.spre.styringsinfo.datafortelling.dataminimering.JsonUtil.traverserOgFjernNoder
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

internal class JsonUtilTest {

    @Test
    fun `navngitte rotnoder slettes`() {
        @Language("json")
        val json = """
            {
              "a": "",
              "b": null,
              "slett1": "",
              "slett2": []
            }
            """.trimIndent()
        @Language("json")
        val forventetJson = """
            {
              "a": "",
              "b": null
            }
            """.trimIndent()
        val vasketJson = fjernRotNoderFraJson(json, listOf("slett1", "slett2"))
        JSONAssert.assertEquals(forventetJson, vasketJson, JSONCompareMode.STRICT)
    }

    @Test
    fun `alle noder med et gitt navn fjernes uansett hvor i json-strukturen de befinner seg`() {
        @Language("json")
        val json = """
            {
              "a": "",
              "b": null,
              "slettmeg": "",
              "c": [],
              "d": [
                {
                  "a": null,
                  "slettmeg": null,
                  "b": {
                      "slettmeg": "foo",
                      "a": "bar"
                  }
                },
                {
                  "a": null,
                  "slettmeg": null,
                  "b": {
                      "a": "bar",
                      "slettmeg": "foo"
                  }
                }
              ]
            }
            """.trimIndent()

        val regex = "\\s".toRegex()
        @Language("json")
        val forventetJson = """
            {
              "a": "",
              "b": null,
              "c": [],
              "d": [
                {
                  "a": null,
                  "b": {
                      "a": "bar"
                  }
                },
                {
                  "a": null,
                  "b": {
                      "a": "bar"
                  }
                }
              ]
            }
            """.trimIndent().replace(regex = regex, replacement = "")

        val vasketJson = traverserOgFjernNoder(json, "slettmeg")
        JSONAssert.assertEquals(forventetJson, vasketJson, JSONCompareMode.STRICT)
        assertFalse(vasketJson.contains("slettmeg"))
    }
}