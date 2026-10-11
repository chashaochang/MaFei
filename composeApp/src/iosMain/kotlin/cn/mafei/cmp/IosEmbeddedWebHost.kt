package cn.mafei.cmp

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cn.mafei.cmp.session.WebPageRequest
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.darwin.NSObject
import platform.CoreGraphics.CGRectZero
import platform.WebKit.*

@OptIn(ExperimentalForeignApi::class)
internal object IosEmbeddedWebHost : EmbeddedWebHost {
    @Composable override fun Content(request: WebPageRequest, callbacks: EmbeddedWebCallbacks, modifier: Modifier) {
        val delegate = remember(request) { object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                webView.URL?.absoluteString?.takeIf(request::accepts)?.let { callbacks.onNativeAction("location", it) }
            }
            override fun webView(webView: WKWebView, decidePolicyForNavigationAction: WKNavigationAction,
                decisionHandler: (WKNavigationActionPolicy) -> Unit) {
                val url = decidePolicyForNavigationAction.request.URL?.absoluteString
                if (url != null && !request.accepts(url)) {
                    if (decidePolicyForNavigationAction.targetFrame?.mainFrame == true) callbacks.onNativeAction("openUrl", url)
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                } else decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
            }
            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
                if (withError.code != NSURLErrorCancelled.toLong()) callbacks.onFailure()
            }
            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
                if (withError.code != NSURLErrorCancelled.toLong()) callbacks.onFailure()
            }
        } }
        val view = remember(request) {
            val config = WKWebViewConfiguration().apply {
                // Authenticated web storage belongs to this account/route, not another window/account.
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                if (request.bootstrapScript.isNotBlank()) userContentController.addUserScript(WKUserScript(
                    source = request.bootstrapScript, injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = true))
            }
            WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
                navigationDelegate = delegate
                loadRequest(NSURLRequest(uRL = requireNotNull(NSURL(string = request.url))))
            }
        }
        DisposableEffect(view) {
            callbacks.registerBack { if (view.canGoBack) { view.goBack(); true } else false }
            onDispose { callbacks.registerBack(null); view.stopLoading(); view.navigationDelegate = null }
        }
        UIKitView(factory = { view }, modifier = modifier)
    }
}
