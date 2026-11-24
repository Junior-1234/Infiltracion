package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.InfiltrationAnalysis
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Infiltrometer3DView(
    plotName: String,
    analysis: InfiltrationAnalysis,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }

    val payloadJson = remember(plotName, analysis) {
        buildThreeJsPayload(plotName, analysis)
    }

    LaunchedEffect(payloadJson, pageLoaded) {
        if (pageLoaded) {
            webViewRef?.evaluateJavascript("window.updateTestData($payloadJson);", null)
        }
    }

    Box(modifier = modifier.fillMaxSize().testTag("infiltrometer_3d_view")) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null
                            if (!url.startsWith("https://appassets.local/assets/")) return null
                            val assetPath = url.removePrefix("https://appassets.local/assets/")
                            return runCatching {
                                val mime = when {
                                    assetPath.endsWith(".html") -> "text/html"
                                    assetPath.endsWith(".js") -> "application/javascript"
                                    assetPath.endsWith(".css") -> "text/css"
                                    else -> "application/octet-stream"
                                }
                                WebResourceResponse(mime, "UTF-8", ctx.assets.open(assetPath))
                            }.getOrNull()
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            pageLoaded = true
                            view?.evaluateJavascript("window.updateTestData($payloadJson);", null)
                        }
                    }

                    loadUrl("https://appassets.local/assets/infiltrometer_3d.html")
                    webViewRef = this
                }
            }
        )
    }
}

private fun buildThreeJsPayload(plotName: String, analysis: InfiltrationAnalysis): String {
    val rowsArr = JSONArray()
    analysis.rows.forEach { r ->
        rowsArr.put(
            JSONObject().apply {
                put("orderIndex", r.orderIndex)
                put("t", r.cumulativeTimeMin)
                put("h", r.waterLevelCm)
                put("refill", r.refillCm)
                put("I", r.cumulativeIMm)
                put("rate", r.instantRateMmH ?: 0.0)
                put("stage", r.stage.name)
                put("stageLabel", r.stage.label)
            }
        )
    }
    return JSONObject().apply {
        put("plotName", plotName)
        put("diameterM", analysis.diameterM)
        put("outerDiameterM", analysis.outerDiameterM)
        put("maxI", maxOf(analysis.totals.totalCumulativeIMm, 10.0))
        put("rows", rowsArr)
    }.toString()
}
