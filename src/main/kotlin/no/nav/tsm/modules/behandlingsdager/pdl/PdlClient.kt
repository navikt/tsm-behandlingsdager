package no.nav.tsm.modules.behandlingsdager.pdl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.plugins.di.annotations.*
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.tsm.core.TsmPdlConfig
import no.nav.tsm.core.logger
import no.nav.tsm.plugins.auth.TexasClient
import kotlin.reflect.KClass

sealed interface PdlClient {
    companion object {
        val subtypes: List<KClass<out PdlClient>> = listOf(
            PdlCloudClient::class,
            PdlLocalClient::class,
        )
    }
    enum class PdlErrors {
        NotFound,
        UnknownError,
    }

    suspend fun getPerson(ident: String): Either<PdlErrors, PdlPerson>
}

class PdlCloudClient(
    @Named("RetryHttpClient") httpClient: HttpClient,
    private val texasClient: TexasClient,
    private val tsmPdlConfig: TsmPdlConfig,
) : PdlClient {
    private val logger = logger()
    private val pdlHttpClient = httpClient.config {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())

                // tsm-pdl-cache responds with some values we don't care about
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    @WithSpan
    override suspend fun getPerson(ident: String): Either<PdlClient.PdlErrors, PdlPerson> {
        val (token) = getToken()

        val response =
            pdlHttpClient.get("${tsmPdlConfig.url}/api/person") {
                headers {
                    append("Nav-Consumer-Id", "syk-inn-api")
                    append("Authorization", "Bearer $token")
                    append("Ident", ident)
                }
            }

        return when {
            response.status.isSuccess() ->
                try {
                    response.body<PdlPerson>().right()
                } catch (e: Exception) {
                    logger.error("Error deserializing PDL response", e)
                    return PdlClient.PdlErrors.UnknownError.left()
                }

            response.status == HttpStatusCode.NotFound -> PdlClient.PdlErrors.NotFound.left()
            else -> {
                logger.error("Unable to get person from pdl, see team logs for ident")
                PdlClient.PdlErrors.UnknownError.left()
            }
        }
    }

    private suspend fun getToken() = texasClient.entraIdToken("tsm", "tsm-pdl-cache")
}
