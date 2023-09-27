package no.nav.arbeidsgiver.altinn_varsel_firewall

import io.ktor.server.application.*
import io.ktor.server.plugins.*
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
import io.ktor.server.logging.*
import io.ktor.util.*
import java.util.concurrent.atomic.AtomicInteger


class SuspektLogging {
    class Configuration

    companion object Feature : BaseApplicationPlugin<ApplicationCallPipeline, Configuration, SuspektLogging> {
        override val key = AttributeKey<SuspektLogging>("SuspektLogging")
        val antall = Health.meterRegistry.gauge("suspekt.antall", AtomicInteger(0))!!

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): SuspektLogging {
            val plugin = SuspektLogging()

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
                    /* InternalServerError, SoapFault kommer som 500, lar den passere */
                    NotImplemented,
                    VersionNotSupported,
                    VariantAlsoNegotiates,
                    InsufficientStorage,
                    -> {
                        application.log.error(
                            "suspekt oppførsel: response: {} request: {}",
                            call.response.status(), call.request.toLogString()
                        )
                        antall.incrementAndGet()
                    }
                }
            }
            return plugin
        }
    }
}