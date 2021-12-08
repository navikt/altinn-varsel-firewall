package no.nav.arbeidsgiver.altinn_varsel_firewall

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

data class AzureAdPrincipal(val azp: String): Principal

fun main() {
    val httpClient = HttpClient(Apache)
    val endpointUrl = getEndpointUrl()

    embeddedServer(Netty, port = 8080) {
        install(Authentication) {
            jwt {
                val issuer = System.getenv("AZURE_OPENID_CONFIG_ISSUER")
                val jwksUri = System.getenv("AZURE_OPENID_CONFIG_JWKS_URI")
                val audience = System.getenv("AZURE_APP_CLIENT_ID")

                val jwkProvider = JwkProviderBuilder(URL(jwksUri))
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

                validate {
                    val azp = it.payload.getClaim("azp").asString() ?: run {
                        log.error("AzureAD missing azp-claim")
                        return@validate null
                    }

                    AzureAdPrincipal(azp = azp)
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
            authenticate {
                get("/ServiceEngineExternal/NotificationAgencyExternalBasic.svc") {
                    /* Question: which request-headers to propagate */
                    val response = httpClient.post<HttpResponse>(endpointUrl) {
                        contentType(call.request.contentType())
                        body = call.request.receiveChannel()
                    }

                    call.respondOutputStream(
                        status = response.status,
                        contentType = response.contentType(),
                    ) {
                        /* Question: which response-headers to propagate */
                        response.content.transferTo(this)
                    }
                }
            }
        }
    }.start(wait = true)
}

fun getEndpointUrl(): String {
    val fromConfig  = System.getenv("NOTIFICATION_AGENCY_ENDPOINT_URL")
    val fromEnv = when (val cluster = System.getenv("NAIS_CLUSTER_NAME")) {
        "dev-gcp" -> "https://tt02.altinn.no/ServiceEngineExternal/NotificationAgencyExternalBasic.svc"
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