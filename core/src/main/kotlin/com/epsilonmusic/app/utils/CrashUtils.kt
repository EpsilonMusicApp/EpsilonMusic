package com.epsilonmusic.app.utils

import timber.log.Timber

var exceptionReporter: ((Throwable) -> Unit)? = null

fun reportException(throwable: Throwable) {
    Timber.e(throwable)
    exceptionReporter?.invoke(throwable)
}
