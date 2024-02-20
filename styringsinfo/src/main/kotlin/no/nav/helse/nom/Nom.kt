package no.nav.helse.nom

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import java.time.LocalDate

typealias Saksbehandler = String
typealias Enhet = String

private const val dollar = '$'
class Nom {
    fun enhet(ident: Saksbehandler, gyldigPåDato: LocalDate): Enhet = "Dette er ikke en ekte enhet"

        private val finnEnhetQuery: String = """
        query enhet(`$dollar`navIdent: String!) {
          ressurs(where: {navident: `$dollar`navIdent}) {
              navident
              orgTilknytning {
                  gyldigFom
                  gyldigTom
                  orgEnhet {
                      id
                      navn
                      remedyEnhetId
                      orgEnhetsType
                  }
              }
          }
        }
""".trimIndent()

}
internal fun JsonNode.enhet(gyldigPåDato: LocalDate): String? {
    val tilknytninger = this["data"]["ressurs"]["orgTilknytning"].map {
        Tilknytning(
            gyldigFom = it["gyldigFom"].asLocalDate(),
            gyldigTom = it["gyldigTom"].asOptionalLocalDate(),
            orgEnhetsType = it["orgEnhet"]["orgEnhetsType"].asText(),
            enhet = it["orgEnhet"]["remedyEnhetId"].asText()
        )
    }
    val kandidater = tilknytninger.filter { it.gyldigFom <= gyldigPåDato && (it.gyldigTom == null || it.gyldigTom >= gyldigPåDato)}
    val sortert = kandidater.sortedWith { a, _ ->
        when {
            a.orgEnhetsType == "NAV_ARBEID_OG_YTELSER" -> -1
            else -> 0
        }
    }
    return sortert.firstOrNull()?.enhet
}

data class Tilknytning(
    val gyldigFom: LocalDate,
    val gyldigTom: LocalDate?,
    val orgEnhetsType: String,
    val enhet: String
)

