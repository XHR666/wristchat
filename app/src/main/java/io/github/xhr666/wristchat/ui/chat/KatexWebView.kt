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
        settings.setTextZoom(100)
        webViewClient = WebViewClient()
        webChromeClient = WebChromeClient()
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        loadDataWithBaseURL(
            "file:///android_asset/",
            HTML_TEMPLATE, "text/html", "utf-8", null
        )
    }

    fun render(markdown: String) {
        val escaped = markdown
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
        evaluateJavascript("render(`$escaped`)", null)
    }

    companion object {
        private const val HTML_TEMPLATE = """
<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="katex/katex.min.css">
<script src="katex/katex.min.js"></script>
<script src="js/marked.min.js"></script>
<style>
body{margin:6px;padding:0;color:#E8EAED;font-size:13px;line-height:1.45;word-wrap:break-word;overflow-wrap:break-word;background:transparent;overflow-x:auto;-webkit-overflow-scrolling:touch}
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
function render(md){
  // 先把各种数学分隔符抽成占位符(块级优先),这样跨段落/跨节点的公式也能正确渲染
  var store=[];
  md = String(md).replace(/\$\$([\s\S]+?)\$\$|\\\[([\s\S]+?)\\\]|\\\(([\s\S]+?)\\\)|\$([^\$\n]+?)\$/g,
    function(m,b1,b2,i1,i2){
      var expr = (b1!==undefined)?b1:(b2!==undefined)?b2:(i1!==undefined)?i1:i2;
      var display = (b1!==undefined)||(b2!==undefined);
      store.push({e:expr,d:display});
      return 'MATHX'+(store.length-1)+'X';
    });
  document.body.innerHTML = marked.parse(md);
  var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null);
  var nodes=[];var n;while(n=walker.nextNode())nodes.push(n);
  nodes.forEach(function(t){
    var s=t.nodeValue;
    if(!s||s.indexOf('MATHX')===-1) return;
    var span=document.createElement('span');
    span.innerHTML = s.replace(/MATHX(\d+)X/g,function(mm,k){
      var it=store[+k]; if(!it) return mm;
      try { return katex.renderToString(it.e,{displayMode:it.d,throwOnError:false}); }
      catch(err) { return it.d ? ('$$'+it.e+'$$') : ('$'+it.e+'$'); }
    });
    t.parentNode.replaceChild(span,t);
  });
}
</script></html>
        """
    }
}
