package io.github.xhr666.wristchat.data

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * 手机同步:手表端临时 HTTP 服务(仅局域网,带 PIN)。
 * GET / -> 配置页 HTML;GET /api/config?pin= -> 当前设置;POST /api/save?pin= -> 保存设置。
 * 仅在前台页面开启,3 分钟无请求自动关闭(由调用方计时)。
 */
class SyncServer(
    private val pin: String,
    private val readConfig: () -> String,          // JSON string of current settings
    private val applyConfig: (String) -> String,   // JSON body -> ok msg or error
) {
    private var serverSocket: ServerSocket? = null
    private var running = false
    private var thread: Thread? = null
    var port: Int = 0
        private set

    fun start(): Boolean {
        return try {
            serverSocket = ServerSocket(0)
            port = serverSocket!!.localPort
            running = true
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
            Thread { handle(client) }.apply { isDaemon = true; start() }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val input = socket.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]
            // 读 headers
            var contentLength = 0
            while (true) {
                val line = input.readLine() ?: break
                if (line.isBlank()) break
                if (line.startsWith("Content-Length:", true)) {
                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            val body = if (contentLength > 0) CharArray(contentLength).let { input.read(it); String(it) } else ""

            val response = when {
                path.startsWith("/api/config") -> handleConfig(path)
                path.startsWith("/api/save") -> handleSave(path, body)
                else -> handleHtml()
            }
            val out = socket.getOutputStream()
            val bytes = response.toByteArray(Charsets.UTF_8)
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n".toByteArray())
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

    private fun handleSave(path: String, body: String): String {
        if (!checkPin(path)) return """{"ok":false,"error":"PIN 错误"}"""
        if (body.isBlank()) return """{"ok":false,"error":"空请求"}"""
        val result = applyConfig(body)
        return """{"ok":true,"message":"$result"}"""
    }

    private fun handleHtml(): String = PAGE_HTML

    companion object {
        private val PAGE_HTML = """
<!DOCTYPE html>
<html lang="zh"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>WristChat 同步</title>
<style>
body{font-family:system-ui,sans-serif;background:#0f1419;color:#e8eaed;max-width:480px;margin:0 auto;padding:16px}
h1{font-size:20px} h2{font-size:15px;margin:18px 0 8px;color:#9aa4af}
label{display:block;font-size:13px;margin:10px 0 4px;color:#c3cbd4}
input,select,textarea{width:100%;box-sizing:border-box;background:#1b2228;border:1px solid #333c44;color:#e8eaed;border-radius:8px;padding:10px;font-size:15px}
button{width:100%;padding:12px;margin-top:16px;background:#4d9fff;border:none;border-radius:8px;color:#fff;font-size:16px;font-weight:600}
button:active{opacity:.8}
#msg{margin-top:12px;padding:10px;border-radius:8px;display:none}
.ok{background:#143d26;color:#7dffa8}.err{background:#3d1414;color:#ff9d9d}
.pinbox{display:flex;gap:8px}.pinbox input{flex:1}
</style></head><body>
<h1>🖐 WristChat 手机同步</h1>
<div class="pinbox"><input id="pin" type="password" placeholder="4 位 PIN" inputmode="numeric"><button onclick="load()" style="margin-top:0">连接</button></div>
<div id="form" style="display:none">
<h2>对话参数</h2>
<label>温度 (0-2)</label><input id="temperature" type="number" step="0.1" min="0" max="2">
<label>Top P (0-1)</label><input id="topP" type="number" step="0.05" min="0" max="1">
<label>最大输出 tokens</label><input id="maxTokens" type="number" min="256" max="65536">
<label>思考模式</label><select id="thinking"><option value="true">开</option><option value="false">关</option></select>
<label>思考强度</label><select id="effort"><option value="low">低</option><option value="high">高</option><option value="max">最大</option></select>
<label>模型</label><input id="model" placeholder="deepseek-v4-flash">
<h2>服务</h2>
<label>Provider</label><select id="provider"><option value="deepseek">DeepSeek</option><option value="qwen">通义千问</option><option value="custom">自定义</option></select>
<label>API Base URL</label><input id="baseUrl" placeholder="https://api.deepseek.com">
<label>API 路径</label><input id="apiPath" placeholder="/chat/completions">
<label>API Key(留空保持不变)</label><input id="apiKey" type="password" placeholder="sk-...">
<h2>系统提示与技能</h2>
<label>自定义系统 Prompt</label><textarea id="customPrompt" rows="3"></textarea>
<label>导入技能(粘贴 SKILL.md 内容,留空跳过)</label><textarea id="skillContent" rows="3" placeholder="---&#10;name: xxx&#10;description: xxx&#10;---&#10;指令内容"></textarea>
<label>技能文件名(可选)</label><input id="skillName" placeholder="myskill.md">
<h2>快捷输入(每行一条)</h2>
<textarea id="quickInputs" rows="4"></textarea>
<button onclick="save()">保存到手表</button>
<div id="msg"></div>
</div>
<script>
var CUR_PIN='';
function load(){
  CUR_PIN=document.getElementById('pin').value;
  if(CUR_PIN.length<4){show('请输入 4 位 PIN','err');return}
  fetch('/api/config?pin='+CUR_PIN).then(r=>r.json()).then(j=>{
    if(!j.ok){show(j.error||'连接失败','err');return}
    var c=j.config;
    document.getElementById('temperature').value=c.temperature;
    document.getElementById('topP').value=c.topP;
    document.getElementById('maxTokens').value=c.maxTokens;
    document.getElementById('thinking').value=String(c.thinking);
    document.getElementById('effort').value=c.effort;
    document.getElementById('model').value=c.model;
    document.getElementById('provider').value=c.provider;
    document.getElementById('baseUrl').value=c.baseUrl;
    document.getElementById('apiPath').value=c.apiPath;
    document.getElementById('customPrompt').value=c.customPrompt;
    document.getElementById('quickInputs').value=(c.quickInputs||[]).join('\n');
    document.getElementById('form').style.display='block';
    show('已连接','ok');
  }).catch(()=>show('无法连接,检查手表和手机是否同一网络','err'));
}
function save(){
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
    quickInputs:document.getElementById('quickInputs').value.split('\n').map(s=>s.trim()).filter(Boolean),
    apiKey:document.getElementById('apiKey').value.trim(),
    skillName:document.getElementById('skillName').value.trim(),
    skillContent:document.getElementById('skillContent').value
  };
  fetch('/api/save?pin='+CUR_PIN,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
    .then(r=>r.json()).then(j=>{show(j.ok?('已保存: '+j.message):(j.error||'保存失败'),j.ok?'ok':'err')})
    .catch(()=>show('保存失败:网络错误','err'));
}
function show(t,cls){var m=document.getElementById('msg');m.textContent=t;m.className=cls;m.style.display='block';setTimeout(()=>m.style.display='none',4000)}
</script></body></html>
        """.trimIndent()
    }
}
