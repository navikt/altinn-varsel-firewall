package no.nav.arbeidsgiver.altinn_varsel_firewall

import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.concurrent.ExecutorService


object Health {
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}

fun <T : ExecutorService> T.produceMetrics(name: String): T {
    ExecutorServiceMetrics(this, name, emptyList()).bindTo(Health.meterRegistry)
    return this
}