package cn.mafei.cmp

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import cn.mafei.cmp.session.WebPageRequest
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream

/** The WebView is a platform surface; navigation and native playback remain in CMP. */
internal object AndroidEmbeddedWebHost : EmbeddedWebHost {
    override val nativePlaybackSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    @SuppressLint("SetJavaScriptEnabled")
    @Composable override fun Content(request: WebPageRequest, callbacks: EmbeddedWebCallbacks, modifier: Modifier) {
        val context = LocalContext.current
        val view = remember(request) { WebView(context).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.allowFileAccess = false; settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (url != null && request.accepts(url)) callbacks.onNativeAction("location", url)
                }
                override fun shouldOverrideUrlLoading(view: WebView, navigation: WebResourceRequest): Boolean {
                    if (request.accepts(navigation.url.toString())) return false
                    if (navigation.isForMainFrame) callbacks.onNativeAction("openUrl", navigation.url.toString())
                    return true
                }
                override fun onReceivedError(view: WebView, navigation: WebResourceRequest, error: WebResourceError) {
                    if (navigation.isForMainFrame) callbacks.onFailure()
                }
                override fun onReceivedHttpError(view: WebView, navigation: WebResourceRequest, response: WebResourceResponse) {
                    if (navigation.isForMainFrame && response.statusCode >= 400) callbacks.onFailure()
                }
                override fun shouldInterceptRequest(view: WebView, navigation: WebResourceRequest): WebResourceResponse? {
                    val url = navigation.url.toString()
                    if (request.bootstrapScript.isEmpty() || !request.accepts(url)) return null
                    val main = Regex(".*/main\\.[^/\\s]+\\.bundle\\.js").containsMatchIn(url) && !url.contains("deferred=true")
                    if (!main && !navigation.url.path.orEmpty().contains("/native/")) return null
                    val file = if (main) "injectionScript.js" else navigation.url.lastPathSegment ?: return null
                    if (file !in setOf("injectionScript.js", "nativeshell.js", "EventEmitter.js", "ExoPlayerPlugin.js", "NavigationPlugin.js")) return null
                    return try {
                        var script = context.assets.open("native/$file").bufferedReader().use { it.readText() }
                        if (file == "nativeshell.js") script += "\nwindow.NativeShell.AppHost.supports = c => ['externallinks','clientsettings','multiserver','exit'].includes(c.toLowerCase());\nwindow.NativeShell.getPlugins = () => ['NavigationPlugin','ExoPlayerPlugin'];"
                        WebResourceResponse("application/javascript", "utf-8", ByteArrayInputStream(script.toByteArray(Charsets.UTF_8)))
                    } catch (_: Exception) { null }
                }
            }
            if (request.bootstrapScript.isNotEmpty()) {
                if (nativePlaybackSupported) {
                    WebViewCompat.addWebMessageListener(this, "MaFeiBridge", setOf(request.origin)) { _, message, source, mainFrame, _ ->
                        val text = message.data
                        if (mainFrame && request.accepts(source.toString()) && text != null && text.length <= 32 * 1024) {
                            val event = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                            val action = event?.get("action")?.jsonPrimitive?.contentOrNull
                            val value = event?.get("value")?.jsonPrimitive?.contentOrNull.orEmpty()
                            if (action != null) callbacks.onNativeAction(action, value)
                        }
                    }
                    WebViewCompat.addDocumentStartJavaScript(this, request.bootstrapScript + "\n" +
                        request.messageBridgeScript("MaFeiBridge"), setOf(request.origin))
                    loadUrl(request.url)
                } else callbacks.onFailure()
            } else loadUrl(request.url)
        } }
        DisposableEffect(view) {
            callbacks.registerBack { if (view.canGoBack()) { view.goBack(); true } else false }
            onDispose {
                callbacks.registerBack(null); view.stopLoading()
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(view, "MaFeiBridge")
                view.destroy()
            }
        }
        AndroidView(factory = { view }, modifier = modifier)
    }
}
