package com.example.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SarkariWebView(
    modifier: Modifier = Modifier,
    initialUrl: String = SARKARI_HOME_URL,
    pendingUrl: String?,
    onPendingUrlConsumed: () -> Unit,
    canGoBack: Boolean,
    onProgressUpdate: (Int) -> Unit,
    onLoadingStateChanged: (Boolean) -> Unit,
    onTitleReceived: (String?) -> Unit,
    onUrlChanged: (String?) -> Unit,
    onHistoryChanged: (Boolean) -> Unit,
    onWebViewReady: (WebView) -> Unit,
    onErrorEncountered: () -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            filePathCallback?.onReceiveValue(uris.toTypedArray())
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    LaunchedEffect(pendingUrl, webViewInstance) {
        val target = pendingUrl
        val wv = webViewInstance
        if (!target.isNullOrBlank() && wv != null) {
            wv.loadUrl(target)
            onPendingUrlConsumed()
        }
    }

    BackHandler(enabled = canGoBack) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        }
    }

    val pullRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            webViewInstance?.reload()
            coroutineScope.launch {
                delay(900)
                isRefreshing = false
            }
        },
        state = pullRefreshState,
        modifier = modifier
            .fillMaxSize()
            .testTag("webview_container")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                createConfiguredWebView(
                    context = ctx,
                    onProgressUpdate = onProgressUpdate,
                    onLoadingStateChanged = onLoadingStateChanged,
                    onTitleReceived = onTitleReceived,
                    onUrlChanged = onUrlChanged,
                    onHistoryChanged = onHistoryChanged,
                    onErrorOccurred = onErrorEncountered,
                    onShowFileChooserRequest = { callback ->
                        filePathCallback?.onReceiveValue(null)
                        filePathCallback = callback
                        try {
                            filePickerLauncher.launch("*/*")
                        } catch (_: Exception) {
                            callback.onReceiveValue(null)
                            filePathCallback = null
                        }
                    }
                ).also { wv ->
                    webViewInstance = wv
                    onWebViewReady(wv)
                    wv.loadUrl(initialUrl)
                }
            },
            update = { wv ->
                webViewInstance = wv
                onWebViewReady(wv)
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    onProgressUpdate: (Int) -> Unit,
    onLoadingStateChanged: (Boolean) -> Unit,
    onTitleReceived: (String?) -> Unit,
    onUrlChanged: (String?) -> Unit,
    onHistoryChanged: (Boolean) -> Unit,
    onErrorOccurred: () -> Unit,
    onShowFileChooserRequest: (ValueCallback<Array<Uri>>) -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        try {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        } catch (_: Exception) {}

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            userAgentString = userAgentString.replace("; wv", "")
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)

        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false

        // Support PDF/form downloads via system DownloadManager or browser fallback
        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            handleFileDownload(context, url, userAgent, contentDisposition, mimeType)
        })

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressUpdate(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                onTitleReceived(title)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback != null) {
                    onShowFileChooserRequest(filePathCallback)
                    return true
                }
                return false
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (resultMsg == null || view == null) return false
                val tempWebView = WebView(view.context).apply {
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            wv: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUrl = request?.url?.toString()
                            if (!targetUrl.isNullOrBlank()) {
                                if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                                    view.loadUrl(targetUrl)
                                } else {
                                    handleUrlNavigation(context, view, targetUrl)
                                }
                            }
                            try {
                                wv?.destroy()
                            } catch (_: Exception) {}
                            return true
                        }
                    }
                }
                val transport = resultMsg.obj as? WebView.WebViewTransport
                if (transport != null) {
                    transport.webView = tempWebView
                    resultMsg.sendToTarget()
                    return true
                }
                return false
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onLoadingStateChanged(true)
                onUrlChanged(url)
                onHistoryChanged(view?.canGoBack() == true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoadingStateChanged(false)
                onUrlChanged(url)
                onHistoryChanged(view?.canGoBack() == true)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleUrlNavigation(context, view, url)
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.cancel()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val errorCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.errorCode ?: 0
                    } else {
                        0
                    }
                    if (errorCode == ERROR_HOST_LOOKUP ||
                        errorCode == ERROR_CONNECT ||
                        errorCode == ERROR_TIMEOUT ||
                        errorCode == ERROR_IO
                    ) {
                        onErrorOccurred()
                    }
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                view?.let { wv ->
                    try {
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.destroy()
                    } catch (_: Exception) {}
                }
                onErrorOccurred()
                return true
            }
        }
    }
}

private fun handleFileDownload(
    context: Context,
    url: String,
    userAgent: String,
    contentDisposition: String,
    mimeType: String
) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                setDescription("Downloading from Sarkari Sahayata")
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            dm?.enqueue(request)
            Toast.makeText(context, "Downloading $fileName…", Toast.LENGTH_SHORT).show()
            return
        }
    } catch (_: Exception) {
    }

    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open file link", Toast.LENGTH_SHORT).show()
    }
}

private fun handleUrlNavigation(context: Context, webView: WebView?, url: String): Boolean {
    if (url.startsWith("http://") || url.startsWith("https://")) {
        return false
    }

    if (url.startsWith("intent://") || url.startsWith("intent:")) {
        try {
            val parsedIntent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            if (parsedIntent != null) {
                val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                if (!fallbackUrl.isNullOrEmpty() && (fallbackUrl.startsWith("http://") || fallbackUrl.startsWith("https://"))) {
                    webView?.loadUrl(fallbackUrl)
                    return true
                }
                val dataUri = parsedIntent.dataString
                if (dataUri != null && (dataUri.startsWith("http://") || dataUri.startsWith("https://"))) {
                    webView?.loadUrl(dataUri)
                    return true
                }
                val pkg = parsedIntent.getPackage()
                if (!pkg.isNullOrEmpty() && !isBrowserPackage(pkg)) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        try {
                            parsedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(parsedIntent)
                            return true
                        } catch (_: Exception) {}
                    }
                    webView?.loadUrl("https://play.google.com/store/apps/details?id=$pkg")
                    return true
                }
            }
        } catch (_: Exception) {}
        return true
    }

    if (url.startsWith("market://")) {
        val webPlayUrl = url.replace("market://details?id=", "https://play.google.com/store/apps/details?id=")
        webView?.loadUrl(webPlayUrl)
        return true
    }

    if (url.startsWith("tg://")) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
            val webTgUrl = when {
                url.contains("domain=") -> "https://t.me/" + url.substringAfter("domain=")
                url.contains("resolve?domain=") -> "https://t.me/" + url.substringAfter("resolve?domain=")
                url.contains("join?invite=") -> "https://t.me/+" + url.substringAfter("join?invite=")
                else -> url.replace("tg://", "https://t.me/")
            }
            webView?.loadUrl(webTgUrl)
            return true
        }
    }

    if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:")) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
        return true
    }

    if (url.contains(".") && !url.contains("://")) {
        webView?.loadUrl("https://$url")
        return true
    }

    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
    return true
}

private fun isBrowserPackage(packageName: String): Boolean {
    val lower = packageName.lowercase()
    return lower.contains("browser") ||
        lower.contains("chrome") ||
        lower.contains("firefox") ||
        lower.contains("opera") ||
        lower.contains("edge") ||
        lower.contains("brave") ||
        lower.contains("ucmobile")
}
