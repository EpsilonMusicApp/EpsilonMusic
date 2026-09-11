package com.epsilonmusic.app.utils.analytics

/**
 * Flavor-agnostic analytics facade.
 *
 * The FOSS flavor has no Firebase classes on its classpath, so the implementation is
 * injected at runtime by `AnalyticsBootstrap` — an object with the same fully-qualified
 * name provided by BOTH flavor source sets: real Firebase wiring under `src/gms`, a
 * no-op under `src/foss`. When no implementation is installed — FOSS builds — every
 * call here is a cheap no-op, so call sites never need flavor guards.
 *
 * This exists because the app previously shipped with NO analytics implementation at
 * all: `google-services.json` was absent from CI builds, no Firebase code was compiled
 * in, and the global `exceptionReporter` hook (used by `reportException` across the
 * entire codebase) was never assigned — so neither Analytics nor Crashlytics ever
 * received a single event.
 */
interface AnalyticsImpl {
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap())
    fun setUserProperty(key: String, value: String)
    fun setUserId(id: String)
    fun recordException(throwable: Throwable)
}

object Analytics {
    @Volatile
    var impl: AnalyticsImpl? = null

    val enabled: Boolean get() = impl != null

    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()) {
        impl?.logEvent(name, params)
    }

    fun setUserProperty(key: String, value: String) {
        impl?.setUserProperty(key, value)
    }

    fun setUserId(id: String) {
        impl?.setUserId(id)
    }

    fun recordException(throwable: Throwable) {
        impl?.recordException(throwable)
    }
}

/**
 * Stable, anonymous installation id for Analytics/Crashlytics user scoping.
 * Persisted by the caller (DataStore); this is only a process-unique fallback.
 */
fun freshInstallId(): String = java.util.UUID.randomUUID().toString()
