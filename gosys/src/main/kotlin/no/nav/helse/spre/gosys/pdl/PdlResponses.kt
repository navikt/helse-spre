package no.nav.helse.spre.gosys.pdl


/**
 * Tilsvarer graphql-spørringen hentFullPerson.graphql
 */

open class PdlResponse<T>(
    open val errors: List<PdlError>?,
    open val data: T?
)

data class PdlHentPerson(
    val hentPerson: Navn
)

data class Navn(
    val navn: List<PdlNavn>
)

data class PdlNavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
) {
    fun tilVisning(): String {
        return listOfNotNull(fornavn, mellomnavn, etternavn)
            .map { it.lowercase() }
            .flatMap { it.split(" ") }
            .joinToString(" ") { navnebit -> navnebit.replaceFirstChar { it.uppercase() } }
    }
}

data class PdlError(
    val message: String,
    val locations: List<PdlErrorLocation>,
    val path: List<String>?,
    val extensions: PdlErrorExtension
)

data class PdlErrorLocation(
    val line: Int?,
    val column: Int?
)

data class PdlErrorExtension(
    val code: String?,
    val classification: String
)
