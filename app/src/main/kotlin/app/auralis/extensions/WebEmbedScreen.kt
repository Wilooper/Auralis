package app.auralis.extensions

import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayInputStream

@Suppress("SetJavaScriptEnabled")
@Composable
fun WebEmbedScreen(entry: ExtensionRegistry.Entry, close: () -> Unit) {
    val network = remember(entry.manifest.id, entry.revision) { EmbedNetwork(entry) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var message by remember { mutableStateOf("Tap the provider’s play control to start. Only approved HTTPS origins can load.") }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_STOP) { web?.loadUrl("about:blank"); web?.onPause(); close() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { network.close(); owner.lifecycle.removeObserver(observer); web?.apply { stopLoading(); onPause(); removeAllViews(); destroy() }; web = null }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(entry.manifest.name, Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.titleMedium); TextButton(close) { Text("Close web player") } }
        Text(message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
            WebView(context).apply {
                web = this
                settings.apply {
                    javaScriptEnabled = true // Remote player rendering only; never expose a Java/Kotlin interface.
                    allowFileAccess = false; allowContentAccess = false
                    @Suppress("DEPRECATION")
                    run { allowFileAccessFromFileURLs = false; allowUniversalAccessFromFileURLs = false }
                    domStorageEnabled = false; databaseEnabled = false; javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false); mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    mediaPlaybackRequiresUserGesture = true; cacheMode = WebSettings.LOAD_NO_CACHE
                    safeBrowsingEnabled = true
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                setDownloadListener { _, _, _, _, _ -> message = "Downloads are disabled in web embeds" }
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
                    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?) = false
                }
                webViewClient = object : WebViewClient() {
                    fun allowed(url: String) = runCatching { ExtensionManifest.allowed(url,entry.manifest.origins) }.isSuccess
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean = !allowed(request.url.toString())
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                        return network.fetch(request)
                    }
                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: android.net.http.SslError?) { handler.cancel() }
                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean { view?.destroy(); web = null; message = "Web renderer stopped. Close and reopen the embed."; return true }
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { if(url != "about:blank" && url != "https://auralis.invalid/") message = "Loading approved web player…" }
                    override fun onPageFinished(view: WebView?, url: String?) { message = "Web controls are separate from Auralis playback. Some providers disallow embedding." }
                }
                val url = entry.manifest.web!!.getString("url")
                val sources = entry.manifest.origins.joinToString(" ")
                val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; frame-src $sources; style-src 'unsafe-inline';"><style>html,body,iframe{margin:0;width:100%;height:100%;border:0;background:#09090c}</style></head><body><iframe title="Music provider" sandbox="allow-scripts allow-same-origin" allow="autoplay" referrerpolicy="no-referrer" src="${android.text.TextUtils.htmlEncode(url)}"></iframe></body></html>"""
                loadDataWithBaseURL("https://auralis.invalid/", html, "text/html", "UTF-8", null)
            }
        })
    }
}
