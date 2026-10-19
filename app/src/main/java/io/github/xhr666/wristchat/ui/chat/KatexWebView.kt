package io.github.xhr666.wristchat.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 懒加载共享 WebView:Markdown(marked.js)+ LaTeX(KaTeX)渲染。
 * 仅含公式的消息使用;离开聊天页销毁。
 */
@SuppressLint("SetJavaScriptEnabled")
class KatexWebView(context: Context) : WebView(context) {

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        // 关键:file:// 页面默认禁止加载子资源(JS/字体),Android 10+ 会静默失败 → 公式空白
        @Suppress("DEPRECATION")
        settings.allowFileAccess = true
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = true
        settings.blockNetworkLoads = false
        settings.setTextZoom(100)
        // 关键:WebView 默认白底,不设透明就会在深色气泡里显示成一条白块
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // 软件渲染:排除硬件加速合成层导致公式区域被"画丢"的可能
        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
        webViewClient = WebViewClient()
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                // 只记录级别与位置,不记录消息内容(避免聊天内容进日志)
                if (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                    io.github.xhr666.wristchat.data.AppLog.i("katex", "js-error line=${m.lineNumber()} src=${m.sourceId()?.takeLast(24)}")
                }
                return true
            }
        }
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        loadDataWithBaseURL(
            "file:///android_asset/",
            HTML_TEMPLATE, "text/html", "utf-8", null
        )
    }

    fun render(markdown: String, textColorHex: String? = null) {
        // 用 JSON 转义(模板字符串会被 JS 解析:反斜杠/`${` 都会出错)
        val quoted = org.json.JSONObject.quote(markdown)
        evaluateJavascript("render($quoted${if (textColorHex != null) ", '$textColorHex'" else ""})", null)
    }

    companion object {
        private const val HTML_TEMPLATE = """
<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="katex/katex.min.css">
<script src="katex/katex.min.js"></script>
<script src="katex/contrib/auto-render.min.js"></script>
<script src="js/marked.min.js"></script>
<style>
html{background:transparent}
body{margin:6px;padding:0;color:#E8EAED;font-size:13px;line-height:1.45;word-wrap:break-word;overflow-wrap:break-word;background:transparent;overflow-x:auto;-webkit-overflow-scrolling:touch}
html{overflow-x:auto}
.katex-display{overflow-x:auto;overflow-y:hidden;padding:2px 0}
.katex{font-size:1.05em}
p{margin:4px 0}
pre{background:#0D1117;padding:8px;border-radius:6px;overflow-x:auto;font-size:12px}
code{background:#0D1117;padding:1px 4px;border-radius:4px;font-size:12px}
pre code{background:none;padding:0}
blockquote{border-left:3px solid #4D9FFF;margin:4px 0;padding-left:8px;color:#9AA4AF}
table{border-collapse:collapse}td,th{border:1px solid #333C44;padding:4px 8px}
img{max-width:100%}
a{color:#4D9FFF}
h1,h2,h3,h4{margin:8px 0 4px}
</style>
</head><body></body>
<script>
function render(md, color){
  try{
    if(color) document.body.style.color = color;
    if(typeof marked === 'undefined'){ document.body.textContent = md; return; }
    document.body.innerHTML = marked.parse(md);
    if(typeof renderMathInElement !== 'function' || typeof katex === 'undefined') return;
    // 官方 auto-render:四种分隔符 + throwOnError:false(错误公式显示红色源码而非整块消失)
    renderMathInElement(document.body, {
      delimiters: [
        {left:'$$', right:'$$', display:true},
        {left:'\\[', right:'\\]', display:true},
        {left:'$',  right:'$',  display:false},
        {left:'\\(', right:'\\)', display:false}
      ],
      throwOnError: false,
      strict: false,
      errorColor: '#FF6B6B',
      ignoredTags: ['script','noscript','style','textarea','pre','code']
    });
  }catch(e){ document.body.setAttribute('data-render-error','1'); }
}
</script></html>
        """
    }
}
