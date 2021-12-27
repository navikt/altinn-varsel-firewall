package no.nav.arbeidsgiver.altinn_varsel_firewall

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Conflict
import io.ktor.http.HttpStatusCode.Companion.ExpectationFailed
import io.ktor.http.HttpStatusCode.Companion.FailedDependency
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.Gone
import io.ktor.http.HttpStatusCode.Companion.InsufficientStorage
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.LengthRequired
import io.ktor.http.HttpStatusCode.Companion.Locked
import io.ktor.http.HttpStatusCode.Companion.MethodNotAllowed
import io.ktor.http.HttpStatusCode.Companion.NotAcceptable
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.NotImplemented
import io.ktor.http.HttpStatusCode.Companion.PayloadTooLarge
import io.ktor.http.HttpStatusCode.Companion.PaymentRequired
import io.ktor.http.HttpStatusCode.Companion.PreconditionFailed
import io.ktor.http.HttpStatusCode.Companion.ProxyAuthenticationRequired
import io.ktor.http.HttpStatusCode.Companion.RequestHeaderFieldTooLarge
import io.ktor.http.HttpStatusCode.Companion.RequestTimeout
import io.ktor.http.HttpStatusCode.Companion.RequestURITooLong
import io.ktor.http.HttpStatusCode.Companion.RequestedRangeNotSatisfiable
import io.ktor.http.HttpStatusCode.Companion.TooManyRequests
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.http.HttpStatusCode.Companion.UnsupportedMediaType
import io.ktor.http.HttpStatusCode.Companion.UpgradeRequired
import io.ktor.http.HttpStatusCode.Companion.VariantAlsoNegotiates
import io.ktor.http.HttpStatusCode.Companion.VersionNotSupported
import io.ktor.util.*
import org.slf4j.Logger
import org.slf4j.event.Level
import org.slf4j.spi.LoggingEventBuilder

class SuspektLogging(configuration: Configuration) {
    val level = configuration.level

    class Configuration {
        var level = Level.ERROR
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, SuspektLogging> {
        private val logger = logger()
        override val key = AttributeKey<SuspektLogging>("DetektivPlugin")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): SuspektLogging {
            val configuration = Configuration().apply(configure)
            val plugin = SuspektLogging(configuration)

            pipeline.intercept(ApplicationCallPipeline.Monitoring) {
                proceed()

                when (call.response.status()) {
                    null -> {}
                    /* 4xx */
                    BadRequest,
                    Unauthorized,
                    PaymentRequired,
                    Forbidden,
                    NotFound,
                    MethodNotAllowed,
                    NotAcceptable,
                    ProxyAuthenticationRequired,
                    RequestTimeout,
                    Conflict,
                    Gone,
                    LengthRequired,
                    PreconditionFailed,
                    PayloadTooLarge,
                    RequestURITooLong,
                    UnsupportedMediaType,
                    RequestedRangeNotSatisfiable,
                    ExpectationFailed,
                    UnprocessableEntity,
                    Locked,
                    FailedDependency,
                    UpgradeRequired,
                    TooManyRequests,
                    RequestHeaderFieldTooLarge,
                        /* 5xx */
                    InternalServerError,
                    NotImplemented,
                    VersionNotSupported,
                    VariantAlsoNegotiates,
                    InsufficientStorage,
                    -> {
                        // sus: log and alert
                        logger.at(configuration.level).log("""
                            suspekt oppførsel:
                            response: ${call.response.status()} 
                            request: ${call.request.toLogString()} ${
                            call.request.headers.entries().map { "${it.key}=${it.value.joinToString()}" }
                        }
                        """.trimIndent().replace(Regex("\n"), " "))
                        // TODO: metric med alert
                    }
                }
            }
            return plugin
        }
    }
}

fun Logger.at(level: Level): LoggingEventBuilder = makeLoggingEventBuilder(level)