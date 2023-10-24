package no.nav.arbeidsgiver.altinn_varsel_firewall

import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.AbstractLogger

class FakeLogger : AbstractLogger() {
    data class Entry(
        val level: Level,
        val marker: Marker?,
        val messagePattern: String,
        val arguments: List<Any>?,
        val throwable: Throwable?
    )

    val entries = ArrayList<Entry>()

    fun clear() {
        entries.clear()
    }

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        messagePattern: String,
        arguments: Array<out Any>?,
        throwable: Throwable?
    ) {
        entries.add(Entry(
            level = level,
            marker = marker,
            messagePattern = messagePattern,
            arguments = arguments?.toList(),
            throwable = throwable,
        ))
    }

    override fun isTraceEnabled() = true
    override fun isTraceEnabled(marker: Marker?) = true
    override fun isDebugEnabled() = true
    override fun isDebugEnabled(marker: Marker?) = true
    override fun isInfoEnabled() = true
    override fun isInfoEnabled(marker: Marker?) = true
    override fun isWarnEnabled() = true
    override fun isWarnEnabled(marker: Marker?) = true
    override fun isErrorEnabled() = true
    override fun isErrorEnabled(marker: Marker?) = true
    override fun getFullyQualifiedCallerName() = "no-documentation-found"

}
