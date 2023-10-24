package no.nav.arbeidsgiver.altinn_varsel_firewall

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.altinn_varsel_firewall.Health.meterRegistry
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.slf4j.event.Level

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SuspektLoggingTest {
    private val fakeLogger = FakeLogger()
    private val engine = TestApplicationEngine(
        environment = createTestEnvironment {
            log = fakeLogger
        }
    )

    @Test
    fun badRequestSuspekt() = assertStatusCode(BadRequest, isSuspect = true)

    @Test
    fun notFoundSuspekt() = assertStatusCode(NotFound, isSuspect = true)

    @Test
    fun forbiddenSuspekt() = assertStatusCode(Forbidden, isSuspect = true)

    @Test
    fun okNormalt() = assertStatusCode(OK, isSuspect = false)

    @Test
    fun noContentNormalt() = assertStatusCode(NoContent, isSuspect = false)

    @BeforeAll
    fun beforeAll() {
        engine.start()
        engine.application.install(SuspektLogging)
    }

    @AfterAll
    fun afterAll() {
        engine.stop(0L, 0L)
    }

    @BeforeEach
    fun beforeEach() {
        fakeLogger.clear()
        SuspektLogging.antall.set(0)
    }

    private fun assertStatusCode(httpStatusCode: HttpStatusCode, isSuspect: Boolean) {
        // given
        val path = "/status/${httpStatusCode.value}"
        engine.environment.application.routing {
            get(path) {
                call.respond<String>(httpStatusCode, "")
            }
        }

        // when
        engine.handleRequest(HttpMethod.Get, path).response

        // then
        val logEntry = fakeLogger.entries.find {
            it.level == Level.ERROR && it.messagePattern.contains("suspekt oppførsel")
        }
        val suspektAntall = meterRegistry.get("suspekt.antall").gauge().value()

        if (isSuspect) {
            assertNotNull(logEntry, "$httpStatusCode er suspekt, men ingen error er logget")
            assertEquals(1.0, suspektAntall, "$httpStatusCode er suspekt, men suspekt-gauge har ikke økt")
        } else {
            assertNull(logEntry, "$httpStatusCode er normalt, men error er logget")
            assertEquals(0.0, suspektAntall, "$httpStatusCode er normalt, men suspekt-gauge har økt")
        }
    }
}

