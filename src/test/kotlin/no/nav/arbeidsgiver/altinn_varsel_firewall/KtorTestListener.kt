package no.nav.arbeidsgiver.altinn_varsel_firewall

import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.ktor.application.*
import io.ktor.server.testing.*

class KtorTestListener(
    private val engine: TestApplicationEngine,
    private val init: Application.() -> Unit
) : TestListener {
    override val name: String
        get() = this::class.simpleName!!

    override suspend fun beforeSpec(spec: Spec) {
        engine.start()
        engine.application.apply(init)
    }

    override suspend fun afterSpec(spec: Spec) {
        engine.stop(0L, 0L)
    }
}