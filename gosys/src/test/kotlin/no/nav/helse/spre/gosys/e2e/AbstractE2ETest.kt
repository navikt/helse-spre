package no.nav.helse.spre.gosys.e2e

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.gosys.*
import no.nav.helse.spre.gosys.setupDataSourceMedFlyway
import no.nav.helse.spre.gosys.vedtak.VedtakMediator
import org.junit.jupiter.api.BeforeEach
import java.util.*

internal abstract class AbstractE2ETest {

    protected val testRapid = TestRapid()
    protected val dataSource = setupDataSourceMedFlyway()
    protected var capturedJoarkRequests = mutableListOf<HttpRequestData>()
    protected var capturedPdfRequests = mutableListOf<HttpRequestData>()

    protected val mockClient = httpclient()
    protected val pdfClient = PdfClient(mockClient)
    protected val stsMock: StsRestClient = mockk {
        coEvery { token() }.returns("6B70C162-8AAB-4B56-944D-7F092423FE4B")
    }
    protected val joarkClient = JoarkClient("https://url.no", stsMock, mockClient)
    protected val duplikatsjekkDao = DuplikatsjekkDao(dataSource)
    protected val vedtakMediator = VedtakMediator(pdfClient, joarkClient, duplikatsjekkDao)

    @BeforeEach
    internal fun abstractSetup() {
        testRapid.reset()
        capturedJoarkRequests.clear()
        capturedPdfRequests.clear()
    }

    private fun httpclient(): HttpClient {
        return HttpClient(MockEngine) {
            install(JsonFeature) {
                serializer = JacksonSerializer(objectMapper)
            }
            engine {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true" -> {
                            capturedJoarkRequests.add(request)
                            respond("Hello, world")
                        }
                        "/api/v1/genpdf/spre-gosys/vedtak" -> {
                            capturedPdfRequests.add(request)
                            respond("Test".toByteArray())
                        }
                        else -> error("Unhandled ${request.url.fullPath}")
                    }
                }
            }
        }
    }

}