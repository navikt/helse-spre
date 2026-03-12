package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retry
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.UUID.randomUUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class GosysOppgaveClient(
    private val baseUrl: String,
    private val tokenClient: AzureTokenProvider,
    private val httpClient: HttpClient,
    private val gosysScope: String,
) : OppgaveoppretterClient {

    override fun lagOppgave(duplikatkontrollId: UUID, fødselsnummer: String, årsak: Årsak, skjæringstidspunkt: LocalDate) {
        val årsakTekst = when (årsak) {
            Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent -> "Det er utbetalt sykepenger fra dag én og vedkommende har 80% dekningsgrad"
            Årsak.SykepengerettOpphørtPåGrunnAvMaksdatoAlderEllerDød -> "Sykepengerett har opphørt som følge av ingen gjenstående dager"
            is Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag -> "For stort avvik mellom sykepengegrunnlag, ${årsak.sykepengegrunnlag.setScale(2)}, og premiegrunnlag, ${årsak.premiegrunnlag.setScale(2)}. Avviket er ${årsak.avviksprosent.setScale(2)}"
        }
        runBlocking {
            retry {
                val response = httpClient.preparePost("$baseUrl/api/v1/oppgaver") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    val bearerToken = tokenClient.bearerToken(gosysScope).getOrThrow()
                    bearerAuth(bearerToken.token)
                    setBody(
                        OpprettOppgaveRequest(
                            personident = fødselsnummer,
                            uuid = duplikatkontrollId.toString(),
                            aktivDato = LocalDate.now(),
                            prioritet = Prioritet.NORM,
                            oppgavetype = "VURD_HENV",
                            tema = "FOS",
                            behandlingstype = "ae0221",
                            beskrivelse = "Årsak: $årsakTekst. Skjæringstidspunkt: ${skjæringstidspunkt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}."
                        )
                    )
                    header("X-Correlation-ID", randomUUID().toString())
                }.execute()

                if (response.status !in listOf(HttpStatusCode.Created, HttpStatusCode.Conflict)) {
                    teamLogs.error("Vi fikk status code ${response.status} tilbake med følgende respons: ${response.bodyAsText()}.")
                    throw IllegalStateException("Feil ved forsøk på å opprette oppgave")
                }
            }
        }
    }
}

@Serializable
data class OpprettOppgaveRequest(
    val personident: String,
    val uuid: String,
    val aktivDato: LocalDate,
    val prioritet: Prioritet,
    val oppgavetype: String,
    val tema: String,
    val behandlingstype: String,
    val beskrivelse: String,
)

@Serializable
enum class Prioritet { HOY, NORM, LAV }
