package no.nav.arbeidsgiver.altinn_varsel_firewall

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry

import java.util.concurrent.ExecutorService


object Health {
    val clock: Clock = Clock.SYSTEM

    val meterRegistry = PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT,
        PrometheusRegistry.defaultRegistry,
        clock
    )
}

fun <T : ExecutorService> T.produceMetrics(name: String): T {
    ExecutorServiceMetrics(this, name, emptyList()).bindTo(Health.meterRegistry)
    return this
}