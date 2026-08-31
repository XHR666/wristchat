package io.github.xhr666.wristchat.ui.chat

import android.content.Context
import android.graphics.Color
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.xhr666.wristchat.R
import io.github.xhr666.wristchat.data.ChatMessage
import io.noties.markwon.Markwon
import java.util.concurrent.ConcurrentHashMap

class ChatAdapter(
    private val context: Context,
    private val onCopy: (String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private var thinking = false
    private val markwon: Markwon = Markwon.create(context)

    // 展开的思维链(按 message 索引)
    private val expandedReasoning = ConcurrentHashMap.newKeySet<Int>()

    // 共享懒加载 WebView(含公式消息用)
    private var katexWebView: KatexWebView? = null
    private var mathPosition: Int = -1

    fun submit(list: List<ChatMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    fun setThinking(t: Boolean) {
        thinking = t
        if (t) {
            messages.add(ChatMessage(role = "assistant", content = "思考中…", ts = System.currentTimeMillis()))
            notifyItemInserted(messages.size - 1)
        } else {
            val idx = messages.indexOfFirst { it.content == "思考中…" }
            if (idx >= 0) { messages.removeAt(idx); notifyItemRemoved(idx) }
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (messages[position].role == "user") TYPE_USER else TYPE_AI

    override fun getItemCount(): Int = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(inflater.inflate(R.layout.item_message_user, parent, false))
        } else {
            AiHolder(inflater.inflate(R.layout.item_message_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = messages[position]
        when (holder) {
            is UserHolder -> {
                holder.content.text = m.content
            }
            is AiHolder -> {
                val isMath = containsMath(m.content)
                val hasReasoning = m.reasoning.isNotBlank()
                if (hasReasoning) {
                    holder.reasoningHeader.visibility = View.VISIBLE
                    val expanded = position in expandedReasoning
                    holder.reasoningHeader.text = if (expanded) "💭 思考过程 ▾" else "💭 思考过程 ▸"
                    holder.reasoningBody.visibility = if (expanded) View.VISIBLE else View.GONE
                    holder.reasoningBody.text = m.reasoning
                    holder.reasoningHeader.setOnClickListener {
                        if (expanded) expandedReasoning.remove(position) else expandedReasoning.add(position)
                        notifyItemChanged(position)
                    }
                } else {
                    holder.reasoningHeader.visibility = View.GONE
                    holder.reasoningBody.visibility = View.GONE
                }

                if (isMath) {
                    holder.content.visibility = View.GONE
                    ensureKatex()
                    mathPosition = position
                    katexWebView?.render(m.content)
                    // 把 WebView 放进 messageHost
                    holder.messageHost.removeAllViews()
                    holder.messageHost.addView(katexWebView, ViewGroup.LayoutParams(176.dp(), ViewGroup.LayoutParams.WRAP_CONTENT))
                } else {
                    if (holder.messageHost.childCount > 0 && holder.messageHost.getChildAt(0) is WebView) {
                        holder.messageHost.removeAllViews()
                        holder.messageHost.addView(holder.content)
                    }
                    holder.content.visibility = View.VISIBLE
                    val spanned: Spanned = markwon.toMarkdown(m.content)
                    holder.content.text = spanned
                }

                if (m.cost > 0 || (m.usage?.totalTokens ?: 0) > 0) {
                    holder.meta.visibility = View.VISIBLE
                    holder.meta.text = "tokens:${m.usage?.totalTokens ?: 0} · ¥%.4f".format(m.cost)
                } else {
                    holder.meta.visibility = View.GONE
                }
            }
        }
    }

    private fun ensureKatex() {
        if (katexWebView == null) {
            katexWebView = KatexWebView(context)
        }
    }

    fun release() {
        katexWebView?.destroy()
        katexWebView = null
    }

    private fun containsMath(s: String): Boolean = s.contains("$")

    private fun Int.dp(): Int = (this * context.resources.displayMetrics.density).toInt()

    class UserHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.tvContent)
    }

    class AiHolder(view: View) : RecyclerView.ViewHolder(view) {
        val reasoningHeader: TextView = view.findViewById(R.id.tvReasoningHeader)
        val reasoningBody: TextView = view.findViewById(R.id.tvReasoningBody)
        val messageHost: ViewGroup = view.findViewById(R.id.messageHost)
        val content: TextView = view.findViewById(R.id.tvContent)
        val meta: TextView = view.findViewById(R.id.tvMeta)
    }

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI = 1
    }
}
