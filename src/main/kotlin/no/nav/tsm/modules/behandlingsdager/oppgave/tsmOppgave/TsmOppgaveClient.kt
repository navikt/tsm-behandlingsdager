package no.nav.tsm.modules.behandlingsdager.oppgave.tsmOppgave

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson3.*
import io.ktor.server.plugins.di.annotations.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.LocalDate
import no.nav.tsm.core.Environment
import no.nav.tsm.ktor.auth.texas.Texas
import no.nav.tsm.ktor.logger

data class OpprettOppgaveRequest(
    val personident: String?,
    val tildeltEnhetsnr: String? = null,
    val journalpostId: String? = null,
    val beskrivelse: String?,
    val behandlingstype: String? = null,
    val fristFerdigstillelse: LocalDate,
    val sykmeldingId: String,
    val type: String,
)

sealed interface TsmOppgaveClient {
    enum class TsmOppgaveErrors {
        BadRequest,
        InternalServerError,
        UnknownError,
    }

    suspend fun createOppgave(
        sykmeldingId: String,
        oppgaveRequest: OpprettOppgaveRequest,
    ): Either<TsmOppgaveErrors, Boolean>
}

class TsmOppgaveCloudClient(
    @Named("RetryHttpClient") httpClient: HttpClient,
    private val texasClient: Texas,
    env: Environment,
) : TsmOppgaveClient {

    private val tsmOppgaveConfig = env.external.tsmOppgave
    private val logger = logger()
    private val tsmOppgaveClient = httpClient.config {
        install(ContentNegotiation) {
            jackson {}
        }
    }

    @WithSpan
    override suspend fun createOppgave(
        sykmeldingId: String,
        oppgaveRequest: OpprettOppgaveRequest,
    ): Either<TsmOppgaveClient.TsmOppgaveErrors, Boolean> {
        val span = Span.current()
        val (token) = getToken()

        val response =
            tsmOppgaveClient.post("${tsmOppgaveConfig.url}/api/oppgave") {
                header("Authorization", "Bearer $token")
                header("X-Correlation-ID", sykmeldingId)
                contentType(ContentType.Application.Json)
                setBody(oppgaveRequest)
            }

        logger.info("tsm-oppgave responded with status ${response.status} for sykmeldingId $sykmeldingId")

        return when {
            response.status == HttpStatusCode.Accepted -> {
                span.setAttribute("client.outcome", "ok")
                true.right()
            }
            response.status == HttpStatusCode.BadRequest -> {
                span.setAttribute("client.outcome", "BadRequest")
                TsmOppgaveClient.TsmOppgaveErrors.BadRequest.left()
            }
            response.status == HttpStatusCode.InternalServerError -> {
                span.setAttribute("client.outcome", "InternalServerError")
                TsmOppgaveClient.TsmOppgaveErrors.InternalServerError.left()
            }
            else -> {
                span.setAttribute("client.outcome", response.status.toString())
                TsmOppgaveClient.TsmOppgaveErrors.UnknownError.left()
            }
        }
    }

    private suspend fun getToken() = texasClient.entraIdToken("tsm", "tsm-oppgave")
}

class TsmOppgaveLocalClient() : TsmOppgaveClient {
    private val logger = logger()

    override suspend fun createOppgave(
        sykmeldingId: String,
        oppgaveRequest: OpprettOppgaveRequest,
    ): Either<TsmOppgaveClient.TsmOppgaveErrors, Boolean> {
        logger.info("Creating oppgave")
        return true.right()
    }
}
