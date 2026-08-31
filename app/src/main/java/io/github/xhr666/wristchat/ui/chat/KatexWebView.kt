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
body{margin:6px;padding:0;color:#E8EAED;font-size:13px;line-height:1.45;word-wrap:break-word;overflow-wrap:break-word;background:transparent}
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
function renderMathInNode(node){
  var textNodes=[];
  var walker=document.createTreeWalker(node,NodeFilter.SHOW_TEXT,null);
  var n; while(n=walker.nextNode()) textNodes.push(n);
  textNodes.forEach(function(t){
    var s=t.nodeValue;
    if(!s||s.indexOf('$')===-1) return;
    // 块级 $$...$$
    var out='';
    var i=0;
    while(i<s.length){
      var start=s.indexOf('$$',i);
      if(start===-1){out+=s.slice(i);break}
      var end=s.indexOf('$$',start+2);
      if(end===-1){out+=s.slice(i);break}
      out+=s.slice(i,start);
      var expr=s.slice(start+2,end);
      try{out+=katex.renderToString(expr,{displayMode:true,throwOnError:false})}catch(e){out+='$$'+expr+'$$'}
      i=end+2;
    }
    // 行内 $...$
    if(out.indexOf('$')!==-1){
      var out2='';var j=0;
      while(j<out.length){
        var st=out.indexOf('$',j);
        if(st===-1){out2+=out.slice(j);break}
        var en=out.indexOf('$',st+1);
        if(en===-1){out2+=out.slice(j);break}
        // 避免渲染成 HTML 标签属性的 $
        out2+=out.slice(j,st);
        var ex=out.slice(st+1,en);
        try{out2+=katex.renderToString(ex,{displayMode:false,throwOnError:false})}catch(e){out2+='$'+ex+'$'}
        j=en+1;
      }
      out=out2;
    }
    var span=document.createElement('span');
    span.innerHTML=out;
    t.parentNode.replaceChild(span,t);
  });
}
function render(md){
  var body=document.body;
  body.innerHTML=marked.parse(md);
  renderMathInNode(body);
}
</script></html>
        """
    }
}
