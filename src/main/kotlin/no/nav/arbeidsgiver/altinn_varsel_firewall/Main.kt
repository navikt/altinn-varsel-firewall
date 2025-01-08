package no.nav.arbeidsgiver.altinn_varsel_firewall

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Accept
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.event.Level
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

data class PreAuthorizedApp(
    val name: String,
    val clientId: String,
) : Principal

fun main() {
    val httpClient = HttpClient(CIO) {
        expectSuccess = false
    }

    val objectMapper = jacksonObjectMapper()

    val endpointUrl = getEndpointUrl()

    val metricsDispatcher: CoroutineContext = Executors.newFixedThreadPool(1)
        .produceMetrics("internal-http")
        .asCoroutineDispatcher()

    embeddedServer(io.ktor.server.cio.CIO, port = 8080) {
        install(SuspektLogging)
        install(CallLogging) {
            disableDefaultColors()
            level = Level.INFO

            filter { call ->
                !call.request.path().startsWith("/internal/")
            }

            mdc("method") { call ->
                call.request.httpMethod.value
            }
            mdc("host") { call ->
                call.request.header("host")
            }
            mdc("path") { call ->
                call.request.path()
            }
            mdc("preAuthorizedAppName") { call ->
                call.principal<PreAuthorizedApp>()?.name
            }
            callIdMdc("x_correlation_id")
        }

        install(Authentication) {
            jwt {
                val issuer = System.getenv("AZURE_OPENID_CONFIG_ISSUER")
                val jwksUri = System.getenv("AZURE_OPENID_CONFIG_JWKS_URI")
                val audience = System.getenv("AZURE_APP_CLIENT_ID")
                val preAuthorizedApps = objectMapper.readValue<List<PreAuthorizedApp>>(
                    System.getenv("AZURE_APP_PRE_AUTHORIZED_APPS")
                )

                this@embeddedServer.log.info("pre authorized apps: $preAuthorizedApps")

                val jwkProvider = JwkProviderBuilder(URI(jwksUri).toURL())
                    .cached(10, 24, TimeUnit.HOURS)
                    .rateLimited(
                        10,
                        1,
                        TimeUnit.MINUTES
                    )
                    .build()

                verifier(jwkProvider, issuer) {
                    withAudience(audience)
                }

                validate { jwt ->
                    val azp = jwt.payload.getClaim("azp").asString() ?: run {
                        this@embeddedServer.log.error("AzureAD missing azp-claim")
                        return@validate null
                    }

                    preAuthorizedApps.find { it.clientId == azp }
                        ?: run {
                            this@embeddedServer.log.error(
                                "azp={} not among pre-authorized apps={}",
                                azp,
                                preAuthorizedApps.joinToString(", ")
                            )
                            return@validate null
                        }
                }

            }
        }
        routing {
            get("/internal/alive") {
                call.respond(HttpStatusCode.OK)
            }
            get("/internal/ready") {
                call.respond(HttpStatusCode.OK)
            }
            get("/internal/metrics") {
                withContext(this.coroutineContext + metricsDispatcher) {
                    call.respond(Health.meterRegistry.scrape())
                }
            }
            authenticate {
                post("/ServiceEngineExternal/NotificationAgencyExternalBasic.svc") {
                    /* Question: which request-headers to propagate */
                    val response = httpClient.post(endpointUrl) {
                        contentType(call.request.contentType())
                        call.request.headers["soapaction"]?.let {
                            headers["soapaction"] = it
                        }
                        call.request.headers[Accept]?.let {
                            headers[Accept] = it
                        }
                        setBody(call.request.receiveChannel())
                    }

                    call.respondOutputStream(
                        status = response.status,
                        contentType = response.contentType(),
                    ) {
                        /* Question: which response-headers to propagate */
                        response.bodyAsChannel().transferTo(this)
                    }
                }
            }
        }
    }.start(wait = true)
}

fun getEndpointUrl(): String {
    val fromConfig = System.getenv("NOTIFICATION_AGENCY_ENDPOINT_URL")
    val fromEnv = when (val cluster = System.getenv("NAIS_CLUSTER_NAME")) {
        "dev-gcp" -> "https://tt02.altinn.no/ServiceEngineExternal/NotificationAgencyExternalBasic.svc"
        "prod-gcp" -> "https://www.altinn.no/ServiceEngineExternal/NotificationAgencyExternalBasic.svc"
        else -> throw IllegalStateException("unknown cluster '$cluster'")
    }

    if (fromConfig != fromEnv) {
        throw IllegalArgumentException("url from config and env don't match")
    }

    return fromEnv
}

suspend fun ByteReadChannel.transferTo(outputStream: OutputStream) {
    val inputStream = this.toInputStream()
    withContext(Dispatchers.IO) {
        inputStream.transferTo(outputStream)
    }
}