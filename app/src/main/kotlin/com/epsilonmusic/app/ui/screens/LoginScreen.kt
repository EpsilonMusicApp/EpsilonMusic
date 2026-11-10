package com.epsilonmusic.app.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Message
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.epsilonmusic.app.LocalPlayerAwareWindowInsets
import com.epsilonmusic.app.R
import com.epsilonmusic.app.constants.AccountChannelHandleKey
import com.epsilonmusic.app.constants.AccountEmailKey
import com.epsilonmusic.app.constants.AccountNameKey
import com.epsilonmusic.app.constants.DataSyncIdKey
import com.epsilonmusic.app.constants.InnerTubeCookieKey
import com.epsilonmusic.app.constants.VisitorDataKey
import com.epsilonmusic.app.ui.component.IconButton
import com.epsilonmusic.app.ui.utils.backToMain
import com.epsilonmusic.app.utils.rememberPreference
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

private val ALLOWED_LOGIN_HOSTS_REGEX =
    Regex("""(^|\.)(google|youtube|gstatic|googleusercontent|googleapis|googlevideo)\.[a-z.]+$|^g\.co$""", RegexOption.IGNORE_CASE)

private fun isAllowedLoginHost(host: String): Boolean {
    if (host.isEmpty()) return false
    return ALLOWED_LOGIN_HOSTS_REGEX.containsMatchIn(host)
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun LoginScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var visitorData by rememberPreference(VisitorDataKey, "")
    var dataSyncId by rememberPreference(DataSyncIdKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    var hasCompletedLogin by remember { mutableStateOf(false) }

    var webView by remember { mutableStateOf<WebView?>(null) }

    fun processSuccessfulAuth(capturedCookie: String) {
        if (hasCompletedLogin) return
        hasCompletedLogin = true
        innerTubeCookie = capturedCookie

        coroutineScope.launch {
            delay(500)

            YouTube.cookie = innerTubeCookie
            YouTube.dataSyncId = dataSyncId
            YouTube.visitorData = visitorData

            Timber.d("Login: YouTube object initialized, validating...")

            YouTube.accountInfo()
                .onSuccess {
                    accountName = it.name
                    accountEmail = it.email.orEmpty()
                    accountChannelHandle = it.channelHandle.orEmpty()

                    Timber.d("Login: Successfully logged in as ${it.name}, navigating back...")

                    webView?.apply {
                        stopLoading()
                        clearHistory()
                        clearCache(true)
                        clearFormData()
                    }

                    Toast.makeText(context, "Logged in as ${it.name}", Toast.LENGTH_SHORT).show()

                    if (!navController.navigateUp()) {
                        navController.backToMain()
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Login: Authentication validation failed, saving fallback and returning")
                    if (accountName.isBlank()) {
                        accountName = "YouTube Music"
                    }

                    webView?.apply {
                        stopLoading()
                        clearHistory()
                        clearCache(true)
                        clearFormData()
                    }

                    if (!navController.navigateUp()) {
                        navController.backToMain()
                    }
                }
        }
    }

    AndroidView(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
        factory = { webViewContext ->
            WebView(webViewContext).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url ?: return false
                        val host = url.host?.lowercase().orEmpty()
                        val scheme = url.scheme?.lowercase().orEmpty()

                        if (scheme != "http" && scheme != "https") {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, url))
                            } catch (e: Exception) {
                                Timber.w(e, "Cannot open scheme: $url")
                            }
                            return true
                        }

                        if (isAllowedLoginHost(host)) {
                            return false
                        }

                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url))
                        } catch (e: Exception) {
                            Timber.w(e, "Cannot open external URL: $url")
                        }
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        if (url?.contains("youtube.com") == true) {
                            view.evaluateJavascript(
                                "(function() { return (window.yt && window.yt.config_) ? window.yt.config_.VISITOR_DATA : ''; })();"
                            ) { value ->
                                val unquoted = value?.removeSurrounding("\"")
                                if (!unquoted.isNullOrEmpty() && unquoted != "null") {
                                    visitorData = unquoted
                                }
                            }
                            view.evaluateJavascript(
                                "(function() { return (window.yt && window.yt.config_) ? window.yt.config_.DATASYNC_ID : ''; })();"
                            ) { value ->
                                val unquoted = value?.removeSurrounding("\"")
                                if (!unquoted.isNullOrEmpty() && unquoted != "null") {
                                    dataSyncId = unquoted.substringBefore("||")
                                }
                            }
                            loadUrl("javascript:Android.onRetrieveVisitorData(window.yt && window.yt.config_ ? window.yt.config_.VISITOR_DATA : '')")
                            loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt && window.yt.config_ ? window.yt.config_.DATASYNC_ID : '')")
                        }

                        val musicCookie = CookieManager.getInstance().getCookie("https://music.youtube.com")
                        val currentCookie = if (!url.isNullOrEmpty()) CookieManager.getInstance().getCookie(url) else null
                        val effectiveCookie = musicCookie ?: currentCookie

                        val isYouTubeMusic = url?.startsWith("https://music.youtube.com") == true
                        val hasAuthToken = effectiveCookie?.contains("SAPISID") == true ||
                            effectiveCookie?.contains("__Secure-3PSID") == true ||
                            effectiveCookie?.contains("LOGIN_INFO") == true

                        if ((isYouTubeMusic || (url?.contains("youtube.com") == true && hasAuthToken)) &&
                            !hasCompletedLogin &&
                            effectiveCookie != null
                        ) {
                            processSuccessfulAuth(effectiveCookie)
                        }
                    }
                }

                webChromeClient =
                    object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                            transport.webView = view
                            resultMsg.sendToTarget()
                            return true
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            Timber.d("Login WebView: ${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(true)
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                    // Mask embedded WebView signature so Google 2FA and modern auth flows execute without rejection
                    val defaultUa = userAgentString ?: WebSettings.getDefaultUserAgent(context)
                    userAgentString =
                        defaultUa
                            .replace("; wv", "")
                            .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onRetrieveVisitorData(newVisitorData: String?) {
                        if (newVisitorData != null) {
                            visitorData = newVisitorData
                        }
                    }
                    @JavascriptInterface
                    fun onRetrieveDataSyncId(newDataSyncId: String?) {
                        if (newDataSyncId != null) {
                            dataSyncId = newDataSyncId.substringBefore("||")
                        }
                    }
                }, "Android")
                webView = this
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
                loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
            }
        },
        onRelease = { releasedWebView ->
            releasedWebView.stopLoading()
            releasedWebView.loadUrl("about:blank")
            releasedWebView.destroy()
            webView = null
        }
    )

    TopAppBar(
        title = { Text(stringResource(R.string.login)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
