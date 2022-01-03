package no.nav.arbeidsgiver.altinn_varsel_firewall

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.spyk
import io.mockk.verify
import no.nav.arbeidsgiver.altinn_varsel_firewall.Health.meterRegistry
import org.slf4j.LoggerFactory

class SuspektLoggingTest : DescribeSpec({
    val spiedOnLogger = spyk(LoggerFactory.getLogger("KtorTestApplicationLogger"))
    val engine = TestApplicationEngine(
        environment = createTestEnvironment {
            log = spiedOnLogger
        }
    )
    listener(KtorTestListener(engine) {
        install(SuspektLogging)
    })

    fun whenResponseStatus(code: HttpStatusCode): TestApplicationResponse {
        val path = "/status/${code.value}"
        engine.environment.application.routing {
            get(path) {
                this.call.respond(code, "")
            }
        }
        return engine.handleRequest(HttpMethod.Get, path).response
    }

    beforeContainer {
        clearAllMocks()
        SuspektLogging.antall.set(0)
    }

    describe("suspekt logging behaviour") {
        forAll(
            HttpStatusCode.BadRequest,
            HttpStatusCode.NotFound,
            HttpStatusCode.Forbidden,
        ) { statusCode ->
            context("when a $statusCode is returned") {
                whenResponseStatus(statusCode)

                it("logs $statusCode as sus") {
                    verify {
                        spiedOnLogger.error(
                            withArg { it shouldContain "suspekt oppførsel" },
                            statusCode,
                            any(),
                        )
                    }
                }

                it("increments gauge value") {
                    val suspektGauge = meterRegistry.get("suspekt.antall").gauge()
                    suspektGauge.value() shouldBe 1
                }
            }
        }

        forAll(
            HttpStatusCode.OK,
            HttpStatusCode.NoContent,
        ) { statusCode ->
            context("when $statusCode is returned") {
                whenResponseStatus(statusCode)

                it("does not log") {
                    verify(exactly = 0) {
                        spiedOnLogger.error(any() as String, any(), any())
                    }
                }

                it("does not increment gauge value") {
                    val suspektGauge = meterRegistry.get("suspekt.antall").gauge()
                    suspektGauge.value() shouldBe 0
                }
            }
        }
    }
})

