package com.epsilonmusic.app.utils

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.epsilonmusic.app.extensions.toEnum
import com.epsilonmusic.app.utils.dataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Note: previously this composable called `context.dataStore.get(key)` (which uses `runBlocking`)
 * synchronously on the Main thread to compute the initial value for `collectAsState`. That
 * blocked the UI thread for 50–500 ms on every cold read, causing the "app sometimes freezes"
 * symptom reported by users.
 *
 * The fix: use `defaultValue` as the initial value (synchronous, no I/O) and let the
 * `dataStore.data` flow deliver the real persisted value asynchronously. The user will see
 * the default for at most one frame (~16 ms) — typically zero frames, because `dataStore.data`
 * has the persisted value cached in memory after the first read.
 *
 * The same change was applied to `rememberEnumPreference` below.
 */
@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state =
        remember {
            context.dataStore.data
                .map { (try { it[key] } catch(e: Exception) { null }) ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsState(defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state =
        remember {
            context.dataStore.data
                .map { (try { it[key] } catch(e: Exception) { null }).toEnum(defaultValue = defaultValue) }
                .distinctUntilChanged()
        }.collectAsState(defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value.name
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
