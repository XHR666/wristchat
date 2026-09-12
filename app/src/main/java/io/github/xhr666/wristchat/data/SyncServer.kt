package io.github.xhr666.wristchat.data

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * 手机同步:手表端临时 HTTP 服务(仅局域网,带 PIN)。
 * GET / -> 配置页;GET /api/config?pin= -> 当前设置;POST /api/save?pin= -> 保存。
 * 前台页面开启,离开即停。
 */
class SyncServer(
    private val pin: String,
    private val readConfig: () -> String,
    private val applyConfig: (String) -> String,
    private val readLog: (String) -> String = { "" },
) {
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var running = false
    private var thread: Thread? = null
    private var pool: java.util.concurrent.ExecutorService? = null
    var port: Int = 0
        private set

    fun start(): Boolean {
        return try {
            serverSocket = ServerSocket(0)
            port = serverSocket!!.localPort
            running = true
            pool = Executors.newFixedThreadPool(2)
            thread = Thread { acceptLoop() }.apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (e: Exception) {}
        thread?.interrupt()
        pool?.shutdownNow()
        serverSocket = null
    }

    fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }?.hostAddress
    } catch (e: Exception) { null }

    private fun acceptLoop() {
        while (running) {
            val client = try { serverSocket?.accept() ?: return } catch (e: Exception) { return }
            pool?.execute { handle(client) }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 20000
            val input = socket.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val path = parts[1]
            var contentLength = 0
            while (true) {
                val line = input.readLine() ?: break
                if (line.isBlank()) break
                if (line.startsWith("Content-Length:", true)) {
                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            val safeLen = if (contentLength in 1..1_048_576) contentLength else 0
            val body = if (safeLen > 0) {
                val buf = CharArray(safeLen); var off = 0
                while (off < safeLen) { val n = input.read(buf, off, safeLen - off); if (n < 0) break; off += n }
                String(buf, 0, off)
            } else ""

            val response = when {
                path.startsWith("/api/config") -> handleConfig(path)
                path.startsWith("/api/save") -> handleSave(path, body)
                path.startsWith("/logs") -> handleLogs(path)
                else -> PAGE_HTML
            }
            val contentType = when {
                path.startsWith("/api/") -> "application/json; charset=utf-8"
                path.startsWith("/logs/") -> "text/plain; charset=utf-8"
                else -> "text/html; charset=utf-8"
            }
            val out = socket.getOutputStream()
            val bytes = response.toByteArray(Charsets.UTF_8)
            out.write("HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n".toByteArray())
            out.write(bytes)
            out.flush()
        } catch (e: Exception) {
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun checkPin(path: String): Boolean {
        val p = path.substringAfter("pin=", "").substringBefore("&")
        return p == pin
    }

    private fun handleConfig(path: String): String {
        if (!checkPin(path)) return """{"ok":false,"error":"PIN 错误"}"""
        return """{"ok":true,"config":${readConfig()}}"""
    }

    /** 日志页:/logs?pin= 汇总页;/logs/crash|anr|app?pin=&lines= 纯文本尾巴 */
    private fun handleLogs(path: String): String {
        if (!checkPin(path)) return "PIN 错误:网址后加 ?pin=四位密钥"
        val name = path.removePrefix("/logs").trim('/').substringBefore('?')
        val lines = (path.substringAfter("lines=", "300").substringBefore("&").toIntOrNull() ?: 300).coerceIn(10, 5000)
        val pin = path.substringAfter("pin=", "").substringBefore("&")
        if (name.isEmpty() || name == "all") {
            val sb = StringBuilder()
            sb.append("<html><head><meta charset=\"utf-8\">")
            sb.append("<meta name=viewport content=\"width=device-width,initial-scale=1\">")
            sb.append("<title>WristChat 日志</title><style>body{background:#0f1419;color:#e8eaed;font-family:system-ui,sans-serif;padding:14px}")
            sb.append("h2{font-size:16px;margin:18px 0 6px;color:#9aa4af}pre{background:#1b2228;padding:10px;border-radius:10px;overflow:auto;font-size:12px;line-height:1.5;max-height:60vh;white-space:pre-wrap;word-break:break-all}")
            sb.append("a{color:#4d9fff;display:inline-block;margin-right:14px;font-size:14px}</style></head><body>")
            sb.append("<h1 style=\"font-size:19px\">WristChat 日志(最后 $lines 行)</h1>")
            sb.append("<p style=\"font-size:13px;color:#9aa4af\">点下面链接可切纯文本页,方便全选复制;也可直接长按复制。</p>")
            for ((key, label) in listOf("crash" to "崩溃日志 crash.log", "anr" to "卡死日志 anr.log", "app" to "运行日志 app.log")) {
                sb.append("<h2>$label <a href=\"/logs/$key?pin=$pin&lines=$lines\">纯文本</a></h2>")
                sb.append("<pre>").append(escapeHtml(tail(readLog(key), lines))).append("</pre>")
            }
            sb.append("</body></html>")
            return sb.toString()
        }
        return tail(readLog(name), lines)
    }

    private fun tail(text: String, lines: Int): String {
        if (text.isBlank()) return "(空)"
        val all = text.lines()
        return if (all.size <= lines) text else all.takeLast(lines).joinToString("\n")
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun handleSave(path: String, body: String): String {
        if (!checkPin(path)) return """{"ok":false,"error":"PIN 错误"}"""
        if (body.isBlank()) return """{"ok":false,"error":"空请求"}"""
        val result = applyConfig(body)
        return org.json.JSONObject().put("ok", true).put("message", result).toString()
    }

    companion object {
        private val PAGE_HTML = """
<!DOCTYPE html>
<html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
<title>WristChat 同步</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,system-ui,"PingFang SC","Microsoft YaHei",sans-serif;background:#0f1419;color:#e8eaed;min-height:100vh;display:flex;flex-direction:column;padding:16px;font-size:16px}
h1{font-size:20px;margin:6px 0 4px;text-align:center}
h2{font-size:15px;margin:20px 0 6px;color:#9aa4af}
.sub{color:#9aa4af;font-size:13px;text-align:center;margin-bottom:14px}
label{display:block;font-size:13px;margin:10px 0 4px;color:#c3cbd4}
input,select,textarea{width:100%;background:#1b2228;border:1px solid #3a4450;color:#e8eaed;border-radius:10px;padding:12px;font-size:17px;outline:none}
input:focus,select:focus,textarea:focus{border-color:#4d9fff}
button{width:100%;padding:14px;margin-top:14px;background:#4d9fff;border:none;border-radius:10px;color:#fff;font-size:18px;font-weight:600}
button:active{opacity:.85}
#msg,#msg2{margin-top:12px;padding:10px;border-radius:10px;text-align:center;display:none}
.ok{background:#143d26;color:#7dffa8}.err{background:#3d1414;color:#ff9d9d}
</style></head><body>
<h1>WristChat 同步</h1>
<p class="sub">先输入手表上显示的 4 位密钥,才能查看/修改设置</p>
<input id="pin" type="password" placeholder="4 位密钥" inputmode="numeric" autocomplete="off" style="text-align:center;font-size:24px;letter-spacing:8px">
<button onclick="load()" style="margin-top:10px">连接</button>
<div id="msg"></div>
<div id="form" style="display:none">
<h2>对话参数</h2>
<label>温度 (0-2)</label><input id="temperature" type="number" step="0.1" min="0" max="2">
<label>Top P (0-1)</label><input id="topP" type="number" step="0.05" min="0" max="1">
<label>最大输出 tokens</label><input id="maxTokens" type="number" min="256" max="65536">
<label>思考模式</label><select id="thinking"><option value="true">开</option><option value="false">关</option></select>
<label>思考强度</label><select id="effort"><option value="low">低</option><option value="high">高</option><option value="max">最大</option></select>
<label>模型</label><input id="model" placeholder="deepseek-flash">
<h2>服务</h2>
<label>服务商</label><select id="provider"><option value="deepseek">DeepSeek</option><option value="qwen">通义千问</option><option value="glm">智谱 GLM</option><option value="kimi">Kimi</option><option value="volcano">火山方舟</option><option value="custom">自定义</option></select>
<label>API Base URL</label><input id="baseUrl" placeholder="https://api.deepseek.com">
<label>API 路径</label><input id="apiPath" placeholder="/chat/completions">
<label>API Key(留空保持不变)</label><input id="apiKey" type="password" placeholder="sk-...">
<h2>系统提示与技能</h2>
<label>自定义系统 Prompt</label><textarea id="customPrompt" rows="4"></textarea>
<label>导入技能(SKILL.md,留空跳过)</label><textarea id="skillContent" rows="4" placeholder="---&#10;name: xxx&#10;description: xxx&#10;---&#10;指令内容"></textarea>
<label>技能文件名(可选)</label><input id="skillName" placeholder="myskill.md">
<h2>快捷输入(每行一条)</h2>
<textarea id="quickInputs" rows="4"></textarea>
<h2>导入聊天记录(可选)</h2>
<label>粘贴聊天 JSON/文本,保存即导入</label>
<textarea id="chatImport" rows="4" placeholder='{"messages":[{"role":"user","content":"hi"}]}'></textarea>
<button onclick="save()">保存到手表</button>
<div id="msg2"></div>
<h2>日志</h2>
<p class="sub" style="text-align:left">排查崩溃/卡死时用,手机浏览器直接看</p>
<a id="loglink" href="#" style="display:block;text-align:center;color:#4d9fff;font-size:16px;padding:10px">打开日志页</a>
</div>
<script>
var CUR='';
function show(m,cls,target){var el=document.getElementById(target||'msg');el.textContent=m;el.className=cls;el.style.display='block';setTimeout(function(){el.style.display='none'},5000)}
function load(){
  CUR=document.getElementById('pin').value.trim();
  if(CUR.length!==4){show('请输入 4 位密钥','err');return}
  show('连接中…','ok');
  fetch('/api/config?pin='+CUR).then(function(r){return r.json()}).then(function(j){
    if(!j.ok){show(j.error||'密钥错误','err');return}
    var c=j.config;
    set('temperature',c.temperature);set('topP',c.topP);set('maxTokens',c.maxTokens);
    document.getElementById('thinking').value=String(c.thinking);
    document.getElementById('effort').value=c.effort;
    set('model',c.model);set('provider',c.provider);set('baseUrl',c.baseUrl);set('apiPath',c.apiPath);
    set('customPrompt',c.customPrompt);
    document.getElementById('quickInputs').value=(c.quickInputs||[]).join('\n');
    document.getElementById('form').style.display='block';
    document.getElementById('loglink').href='/logs?pin='+CUR;
    show('已连接','ok');
  }).catch(function(){show('无法连接,确认手机与手表同一网络','err')});
}
function set(id,v){var el=document.getElementById(id);if(el){el.value=(v===undefined||v===null)?'':v}}
function save(){
  if(CUR.length!==4){show('请先连接','err','msg2');return}
  var body={
    temperature:parseFloat(document.getElementById('temperature').value)||1,
    topP:parseFloat(document.getElementById('topP').value)||1,
    maxTokens:parseInt(document.getElementById('maxTokens').value)||4096,
    thinking:document.getElementById('thinking').value==='true',
    effort:document.getElementById('effort').value,
    model:document.getElementById('model').value.trim(),
    provider:document.getElementById('provider').value,
    baseUrl:document.getElementById('baseUrl').value.trim(),
    apiPath:document.getElementById('apiPath').value.trim(),
    customPrompt:document.getElementById('customPrompt').value,
    quickInputs:document.getElementById('quickInputs').value.split('\n').map(function(x){return x.trim()}).filter(Boolean),
    apiKey:document.getElementById('apiKey').value.trim(),
    skillName:document.getElementById('skillName').value.trim(),
    skillContent:document.getElementById('skillContent').value,
    chatImport:document.getElementById('chatImport').value
  };
  fetch('/api/save?pin='+CUR,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
    .then(function(r){return r.json()}).then(function(j){show(j.ok?('已保存: '+j.message):(j.error||'保存失败'),j.ok?'ok':'err','msg2')})
    .catch(function(){show('保存失败:网络错误','err','msg2')});
}
</script></body></html>
        """
    }
}
