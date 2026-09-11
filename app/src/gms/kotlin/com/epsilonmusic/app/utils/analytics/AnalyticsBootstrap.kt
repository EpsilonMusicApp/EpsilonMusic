package com.epsilonmusic.app.utils.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.epsilonmusic.app.BuildConfig
import com.epsilonmusic.app.utils.exceptionReporter
import timber.log.Timber

/**
 * GMS flavor: real Firebase Analytics + Crashlytics wiring.
 *
 * `App.onCreate` calls [install] once (foss builds get a same-named no-op object).
 * What it does:
 *  - obtains the (auto-initialized) FirebaseAnalytics singleton and registers it behind
 *    the shared [Analytics] facade;
 *  - points the global `exceptionReporter` hook at Crashlytics — until now every
 *    `reportException` call in the codebase silently went nowhere, so NONE of the
 *    internally-handled errors (failed stream resolution, network failures, parser
 *    errors…) ever reached the Firebase console;
 *  - sets stable user scoping: an anonymous persisted install id as Crashlytics userId
 *    (PII-free) plus build metadata as user properties so console reports can be
 *    filtered by flavor/version.
 *
 * NOTE: `google-services.json` must be present at build time (repo file or the CI
 * `GOOGLE_SERVICES_JSON` secret) for Firebase to initialize. Without it the SDK is
 * compiled in but cannot start, install() fails into the no-op catch below, and the
 * Firebase console stays empty — which is exactly the symptom this file fixes.
 */
object AnalyticsBootstrap {

    fun install(context: Context) {
        try {
            val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            val crashlytics = FirebaseCrashlytics.getInstance()

            // Collection ON, explicitly. The fork's old manifest shipped
            // firebase_*_collection_enabled=false with nothing ever re-enabling
            // it — the SDK was initialized but silently dropped every event and
            // crash, which is why the Firebase console stayed empty. Enabling
            // here (in addition to removing those manifest flags) keeps the
            // intent explicit and guards against the flags sneaking back in.
            firebaseAnalytics.setAnalyticsCollectionEnabled(true)
            crashlytics.setCrashlyticsCollectionEnabled(true)

            // Anonymous install id: persisted by App (DataStore) and passed back in
            // via setUserId below. Crashlytics userId must never be a real account id.
            Analytics.impl = object : AnalyticsImpl {
                override fun logEvent(name: String, params: Map<String, Any?>) {
                    // FirebaseAnalytics.logEvent takes a Bundle; translate the typed
                    // map used by the shared facade. Anything exotic falls back to its
                    // string form so no event is ever dropped.
                    val bundle = Bundle().apply {
                        params.forEach { (key, value) ->
                            when (value) {
                                null -> {}
                                is String -> putString(key, value)
                                is Int -> putInt(key, value)
                                is Long -> putLong(key, value)
                                is Double -> putDouble(key, value)
                                is Float -> putFloat(key, value)
                                is Boolean -> putBoolean(key, value)
                                is Bundle -> putBundle(key, value)
                                else -> putString(key, value.toString())
                            }
                        }
                    }
                    firebaseAnalytics.logEvent(name, bundle)
                }

                override fun setUserProperty(key: String, value: String) {
                    firebaseAnalytics.setUserProperty(key, value.take(36))
                }

                override fun setUserId(id: String) {
                    firebaseAnalytics.setUserId(id)
                    crashlytics.setUserId(id)
                }

                override fun recordException(throwable: Throwable) {
                    crashlytics.recordException(throwable)
                }
            }

            // Route every existing reportException() call site (stream resolution
            // failures, IO recovery, lyric provider errors, …) into Crashlytics as
            // non-fatals. This single assignment is what makes Crashlytics "accurate"
            // for this app, because almost nothing here actually crashes the process —
            // errors are caught and recovered from.
            exceptionReporter = { throwable ->
                try {
                    crashlytics.recordException(throwable)
                } catch (t: Throwable) {
                    Timber.w(t, "Crashlytics recordException failed")
                }
            }

            // Build metadata as user properties — lets the console split sessions by
            // build type / flavor instead of every release blending together.
            runCatching {
                firebaseAnalytics.setUserProperty("build_type", BuildConfig.BUILD_TYPE.take(36))
                firebaseAnalytics.setUserProperty("version_name", BuildConfig.VERSION_NAME.take(36))
                firebaseAnalytics.setUserProperty(
                    "app_update_channel",
                    if (BuildConfig.IS_NIGHTLY) "nightly" else "release",
                )
            }

            Timber.i("Firebase Analytics + Crashlytics installed (flavor=%s)", BuildConfig.FLAVOR)
        } catch (t: Throwable) {
            // A broken/missing Firebase setup must never take the app down.
            Timber.w(t, "AnalyticsBootstrap.install failed — analytics stays disabled")
        }
    }
}
